package clinic.persistence;

import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

/** Single-process embedded database. All units of work commit or roll back together. */
public final class Database implements AutoCloseable {
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws Exception; }
    private Connection connection;
    private FileChannel lockChannel;
    private FileLock lock;
    private final Path file;
    private int transactionDepth;
    private Throwable rollbackCause;

    public Database(Path base) {
        file = base.toAbsolutePath().normalize();
        if (file.toString().contains(";") || file.toString().contains("\n"))
            throw new IllegalArgumentException("Invalid database location.");
        try {
            Class.forName("org.h2.Driver");
            Files.createDirectories(file.getParent());
            restrict(file.getParent());
            lockChannel = FileChannel.open(Path.of(file + ".application.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            if (lock == null) throw new IllegalStateException("Database is already in use.");
            Path key = Path.of(file + ".key");
            if (!Files.exists(key)) {
                if (Files.exists(Path.of(file + ".mv.db"))) throw new IllegalStateException("Database key is missing.");
                byte[] bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                Files.createFile(key);
                restrict(key);
                Files.writeString(key, HexFormat.of().formatHex(bytes));
                Arrays.fill(bytes, (byte)0);
            }
            restrict(key);
            String secret = Files.readString(key).trim();
            if (!secret.matches("[0-9a-f]{64}")) throw new IllegalStateException("Database key is invalid.");
            connection = DriverManager.getConnection("jdbc:h2:file:" + file.toString().replace('\\', '/')
                + ";CIPHER=AES;WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE", "clinic", secret + " " + secret);
            Schema.migrate(connection);
            connection.setAutoCommit(false);
        } catch (Exception failure) {
            release();
            throw new StorageException("Unable to open clinic storage. Check the driver, folder permissions, and that no other clinic window is using this database. Keep the database and key files together.", failure);
        }
    }

    private static void restrict(Path path) throws Exception {
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null) {
            AclEntry.Builder entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (Files.isDirectory(path)) entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
            acl.setAcl(List.of(entry.build()));
        } else if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwx------" : "rw-------"));
        }
    }

    public synchronized <T> T transaction(Work<T> work) {
        if (connection == null) throw new StorageException("Clinic storage is closed. Restart the application.", null);
        boolean outermost=transactionDepth==0;
        transactionDepth++;
        try {
            T result = work.run(connection);
            if(outermost) {
                if(rollbackCause!=null)
                    throw new StorageException("The operation could not be completed. No changes were saved.",rollbackCause);
                connection.commit();
            }
            return result;
        } catch (Exception | Error failure) {
            if(rollbackCause==null)rollbackCause=failure;
            if(outermost) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            }
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new StorageException("The database operation could not be completed. No changes were saved. Check storage availability and retry.", failure);
        } finally {
            transactionDepth--;
            if(outermost)rollbackCause=null;
        }
    }

    public String location() { return file + ".mv.db"; }
    @Override public synchronized void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException failure) {
            throw new StorageException("Unable to close clinic storage cleanly. Please retry.", failure);
        } finally { release(); }
    }
    private void release() {
        try { if (connection != null) connection.close(); } catch (SQLException ignored) { /* Original error is reported by caller. */ }
        connection = null;
        try { if (lock != null) lock.release(); } catch (java.io.IOException ignored) { /* Closing the channel also releases its lock. */ }
        try { if (lockChannel != null) lockChannel.close(); } catch (java.io.IOException ignored) { /* Startup/close failure already reported. */ }
    }
}
