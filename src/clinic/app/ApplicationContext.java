package clinic.app;
import clinic.core.*;
import clinic.model.*;
import clinic.service.*;
import java.lang.reflect.*;
public final class ApplicationContext implements AutoCloseable {
    private final AuditLog audit;
    public final Login login;
    public final AdminService admin;
    private final ClinicData store;
    private final clinic.persistence.Database database;
    public ApplicationContext(boolean demo) {
        this(demo,new AuditLog());
    }
    /** Explicit fixture injection for audit assertions; production uses the Path constructor. */
    public ApplicationContext(boolean demo, AuditLog audit) {
        database=null;
        this.audit=java.util.Objects.requireNonNull(audit);
        store=new InMemoryClinicSystem();
        login=demo?Login.withSampleUsers(audit):new Login(audit);
        admin=new AdminService(login, audit);
        if (demo) seedSampleCampuses();
    }
    /** Normal launch uses this durable composition; boolean constructor is an isolated fixture. */
    public ApplicationContext(java.nio.file.Path databaseFile) {
        database=new clinic.persistence.Database(databaseFile);
        try {
            var repository=new clinic.persistence.AdminRepository(database);
            audit=new AuditLog(repository);
            login=new Login(audit,new clinic.persistence.AccountRepository(database));
            admin=new AdminService(login,audit,repository);
            store=clinic.persistence.ClinicRepository.create(database);
        } catch (RuntimeException failure) {
            database.close();
            throw failure;
        }
    }
    public static ApplicationContext production() {
        return new ApplicationContext(java.nio.file.Path.of(System.getProperty("clinic.database", "data/scms")));
    }
    public boolean isPersistent() { return database!=null; }
    public String storageDescription() {
        return isPersistent()?"Changes are saved to the local database.":"Demo/test data lasts only for this session.";
    }
    public String databaseLocation() { return isPersistent()?database.location():"In-memory fixture"; }
    @Override public void close() {
        synchronized(login) { login.logout(); if(database!=null)database.close(); }
    }
    private void seedSampleCampuses() {
        login.login("AD001", "admin123").orElseThrow(
            () -> new IllegalStateException("Demo administrator account is unavailable."));
        try {
            admin.saveCampus(new Campus("C1", "Campus 1 (sample)"), false);
            admin.saveCampus(new Campus("C2", "Campus 2 (sample)"), false);
            admin.saveCampus(new Campus("C3", "Campus 3 (sample)"), false);
        } finally {
            login.logout();
        }
    }
    public User current() {
        return login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
    }
    /** Captures one nurse session; checks do not hold the login lock during network I/O. */
    public Runnable nurseSessionGuard() {
        synchronized (login) {
            User owner = current();
            if (!(owner instanceof SchoolNurse)) throw new SecurityException("Nurse access required.");
            long version = login.getSessionVersion();
            return () -> {
                synchronized (login) {
                    if (login.getSessionVersion() != version || current() != owner)
                        throw new SecurityException("Session expired. Sign in again.");
                }
            };
        }
    }
    public void require(String feature) {
        requireAny(feature);
    }
    private void requireAny(String... features) {
        User user = current();
        boolean allowed = java.util.Arrays.stream(features).anyMatch(user::canAccess);
        audit.record(java.time.LocalDateTime.now(), user.getUserId(), user.getName(),
            user.getRole(), "clinicQuery", "Read permission check", allowed);
        if (!allowed) throw new SecurityException("Access denied.");
    }
    // The proxy rechecks session identity on every call, invalidating retained screen references after logout.
    public ClinicSystem clinic() {
        User owner=current();
        long version = login.getSessionVersion();
        ClinicSystem secured=new SecuredClinicSystem(store, owner, audit);
        return (ClinicSystem)Proxy.newProxyInstance(ClinicSystem.class.getClassLoader(), new Class<?>[] {
            ClinicSystem.class
        }, (p, m, a)-> {
            synchronized (login) {
                if(current()!=owner || login.getSessionVersion()!=version)throw new SecurityException("Session expired.");
                try {
                    return m.invoke(secured, a);
                }
                catch(InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }
        });
    }
    public PageResult<Patient> patients(int page, int size, String q) {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            return store.patientPage(page, size, q);
        }
    }
    public PageResult<Medicine> medicines(int page, int size, String q) {
        synchronized (login) {
            require(User.VIEW_STOCK);
            return store.medicinePage(page, size, q);
        }
    }
    public PageResult<MedicalRecord> records(int page, int size, String q) {
        synchronized (login) {
            requireAny(User.RECORDS, User.VIEW_REPORTS);
            return store.recordPage(page, size, q);
        }
    }
    public PageResult<MedicalRecord> recentVisits(int page, int size, String query) {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            User user = current();
            return store.recentRecordPage(page, size, query,
                user.canAccess(User.RECORDS) ? null : user.getUserId());
        }
    }
    public PageResult<MedicalRecord> recentVisits(int page, int size, String nameOrId,
            java.time.LocalDateTime fromInclusive, java.time.LocalDateTime toInclusive) {
        synchronized (login) {
            User user = current();
            boolean allowed = user.canAccess(User.RECORD_TRANSACTION);
            audit.record(java.time.LocalDateTime.now(), user.getUserId(), user.getName(), user.getRole(),
                "visitSearch", "visit search performed", allowed);
            if (!allowed) throw new SecurityException("Access denied.");
            return store.recentRecordPage(page, size, nameOrId,
                user.canAccess(User.RECORDS) ? null : user.getUserId(), fromInclusive, toInclusive);
        }
    }
    public PageResult<NotificationLog> notifications(int page, int size, String q) {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            return store.notificationPage(page, size, q);
        }
    }
    public PageResult<Alert> alerts(int page, int size, String q) {
        synchronized (login) {
            require(User.RECORDS);
            return store.alertPage(page, size, q);
        }
    }
    public int patientCount() {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            return store.patientCount();
        }
    }
    public int visitCount() {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            return store.recordCount();
        }
    }
}
