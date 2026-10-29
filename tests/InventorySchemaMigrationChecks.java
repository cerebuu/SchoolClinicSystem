import clinic.app.ui.common.popup.MessagePopup;
import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import clinic.persistence.ClinicRepository;
import clinic.persistence.Database;
import clinic.persistence.StorageException;
import clinic.service.ClinicData;
import clinic.service.MedicineCatalog;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Exercises fresh and incomplete previous-version inventory schemas with real H2 storage. */
public final class InventorySchemaMigrationChecks {
    private static int checks;
    private static final LocalDate BASILIXIMAB_EXPIRY=LocalDate.of(2034,12,23);

    public static void main(String[] args)throws Exception {
        freshCatalogBatch();
        missingAtcSchemaReproducesAndMigrates();
        previousSchemaUpgradeMakesLegacyColumnsSafe();
        storageMessageIsSafe();
        System.out.println("PASS: "+checks+" inventory schema migration, Basiliximab, cause logging, and popup checks.");
    }

    private static void freshCatalogBatch()throws Exception {
        Path directory=Files.createTempDirectory("inventory-fresh-schema-");
        Path base=directory.resolve("clinic");
        try(Database database=new Database(base)) {
            seedActor(database);
            ClinicData data=ClinicRepository.create(database);
            MedicineCatalog catalog=new MedicineCatalog(List.of(
                new MedicineCatalog.Entry("Basiliximab","L04AC02","",false,true)));
            MedicineProduct product=catalog.findByName("Basiliximab").orElseThrow().product();
            addBatch(data,product,"wwww");
            Medicine saved=data.medicinePage(0,25,"Basiliximab").rows().get(0);
            check(saved.getQuantity()==12&&saved.getExpirationDate().equals(BASILIXIMAB_EXPIRY)
                &&saved.getRemarks().equals("wwww"),"fresh database stores Basiliximab quantity, expiry, and remarks");
            check(saved.getAtcPrimary().equals("L04AC02"),"fresh catalog batch stores its primary ATC code");
            var event=data.medicationHistoryPage(0,25,"Basiliximab",null,null,"uic-main").rows().get(0);
            check(event.medicine().atcPrimary().equals("L04AC02"),"fresh stock history stores Basiliximab ATC code");
            check(schemaVersion(database)==14,"fresh database reaches schema version 14");
        } finally { removeTree(directory); }
    }

    private static void missingAtcSchemaReproducesAndMigrates()throws Exception {
        Path directory=Files.createTempDirectory("inventory-missing-atc-");
        Path base=directory.resolve("clinic");
        try {
            try(Database database=new Database(base)) {
                seedActor(database);
                ClinicData data=ClinicRepository.create(database);
                database.transaction(connection->{
                    try(var update=connection.createStatement()) {
                        update.executeUpdate("UPDATE schema_version SET version=11 WHERE id=1");
                        for(String table:List.of("medicine_batches","dispensing","inventory_movements")) {
                            update.execute("ALTER TABLE "+table+" DROP COLUMN atc_primary");
                            update.execute("ALTER TABLE "+table+" DROP COLUMN atc_secondary");
                        }
                    }
                    return null;
                });
                assertUnmigratedBatchWriteFailure(database,data,"ATC_PRIMARY","42S22",42122);
                check(database.transaction(connection->{
                    try(var count=connection.createStatement().executeQuery("SELECT COUNT(*) FROM medicine_batches")) {
                        count.next();return count.getInt(1);
                    }
                })==0,"failed Basiliximab save leaves the old inventory unchanged");
            }

            try(Database database=new Database(base)) {
                ClinicData data=ClinicRepository.create(database);
                check(schemaVersion(database)==14,"version 11 database migrates additively to schema version 14");
                addBatch(data,new MedicineProduct("Basiliximab","L04AC02",""),"wwww");
                Medicine basiliximab=data.medicinePage(0,25,"Basiliximab").rows().get(0);
                check(basiliximab.getQuantity()==12&&basiliximab.getExpirationDate().equals(BASILIXIMAB_EXPIRY)
                    &&basiliximab.getRemarks().equals("wwww")&&basiliximab.getAtcPrimary().equals("L04AC02"),
                    "reported Basiliximab batch saves after missing-ATC migration");
            }
        } finally { removeTree(directory); }
    }

    private static void previousSchemaUpgradeMakesLegacyColumnsSafe()throws Exception {
        Path directory=Files.createTempDirectory("inventory-old-schema-");
        Path base=directory.resolve("clinic");
        try {
            try(Database database=new Database(base)) {
                seedActor(database);
                ClinicData data=ClinicRepository.create(database);
                data.saveInventoryBatch(new MedicineProduct("Legacy batch","",""),7,
                    LocalDate.of(2035,1,1),"legacy note",false,false,"MIGRATION-NURSE","uic-main");
                database.transaction(connection->{
                    try(var update=connection.createStatement()) {
                        update.executeUpdate("UPDATE medicine_batches SET strength='250mg',formulation='tablet' WHERE name='Legacy batch'");
                        update.executeUpdate("UPDATE schema_version SET version=12 WHERE id=1");
                        for(String table:List.of("medicine_batches","dispensing","inventory_movements")) {
                            update.execute("ALTER TABLE "+table+" ALTER COLUMN strength DROP DEFAULT");
                            update.execute("ALTER TABLE "+table+" ALTER COLUMN formulation DROP DEFAULT");
                        }
                    }
                    return null;
                });
                assertUnmigratedBatchWriteFailure(database,data,"STRENGTH","23502",null);
            }

            try(Database database=new Database(base)) {
                ClinicData data=ClinicRepository.create(database);
                check(schemaVersion(database)==14,"old schema advances additively to version 14");
                Medicine legacy=data.medicinePage(0,25,"Legacy batch").rows().get(0);
                check(legacy.getQuantity()==7&&legacy.getRemarks().equals("legacy note"),
                    "old batch remains readable after migration");
                database.transaction(connection->{
                    try(var query=connection.createStatement().executeQuery(
                            "SELECT strength,formulation FROM medicine_batches WHERE name='Legacy batch'")) {
                        query.next();
                        check(query.getString(1).equals("250mg")&&query.getString(2).equals("tablet"),
                            "migration preserves existing strength/formulation values");
                    }
                    return null;
                });
                addBatch(data,new MedicineProduct("Basiliximab","L04AC02",""),"wwww");
                Medicine basiliximab=data.medicinePage(0,25,"Basiliximab").rows().get(0);
                check(basiliximab.getQuantity()==12&&basiliximab.getExpirationDate().equals(BASILIXIMAB_EXPIRY)
                    &&basiliximab.getRemarks().equals("wwww")&&basiliximab.getAtcPrimary().equals("L04AC02"),
                    "reported Basiliximab batch saves after old-schema migration");
                var event=data.medicationHistoryPage(0,25,"Basiliximab",null,null,"uic-main").rows().get(0);
                check(event.medicine().atcPrimary().equals("L04AC02"),
                    "migrated inventory history stores Basiliximab ATC code");
            }
        } finally { removeTree(directory); }
    }

    private static void assertUnmigratedBatchWriteFailure(Database database,ClinicData data,String missingColumn,
            String expectedState,Integer expectedErrorCode)throws Exception {
        Logger logger=Logger.getLogger(Database.class.getName());
        boolean parentHandlers=logger.getUseParentHandlers();
        Level oldLevel=logger.getLevel();
        LogCapture capture=new LogCapture();
        logger.setUseParentHandlers(false);logger.setLevel(Level.ALL);logger.addHandler(capture);
        StorageException failure;
        try {
            try {
                addBatch(data,new MedicineProduct("Basiliximab","L04AC02",""),"wwww");
                throw new AssertionError("The old schema unexpectedly accepted the current batch save.");
            } catch(StorageException expected) { failure=expected; }
        } finally {
            logger.removeHandler(capture);logger.setUseParentHandlers(parentHandlers);logger.setLevel(oldLevel);
        }
        check(failure.getCause() instanceof SQLException,"StorageException retains the original SQLException cause");
        SQLException sqlFailure=(SQLException)failure.getCause();
        check(expectedState.equals(sqlFailure.getSQLState())
            &&(expectedErrorCode==null||sqlFailure.getErrorCode()==expectedErrorCode)
            &&sqlFailure.getMessage().contains(missingColumn),
            "old schema reproduces the missing "+missingColumn+" SQL failure");
        check(sqlFailure.getMessage().contains("INSERT INTO medicine_batches"),
            "failed save identifies the medicine batch INSERT statement");
        System.out.println("REPRODUCED SQL exception | SQLState="+sqlFailure.getSQLState()+" | errorCode="
            +sqlFailure.getErrorCode()+" | "+sqlFailure.getMessage());
        check(capture.record!=null&&capture.record.getThrown()!=null,
            "storage failure is logged with its throwable for diagnostics");
        StringWriter trace=new StringWriter();capture.record.getThrown().printStackTrace(new PrintWriter(trace));
        check(trace.toString().contains(missingColumn)&&trace.toString().contains("INSERT INTO medicine_batches"),
            "diagnostic log contains the SQL exception stack trace and SQL state");
    }

    private static void storageMessageIsSafe() {
        SQLException sql=new SQLException("Column ATC_PRIMARY missing in INSERT INTO medicine_batches", "42S22",42122);
        StorageException failure=new StorageException(
            "The database operation could not be completed. No changes were saved. Check storage availability and retry.",sql);
        String message=MessagePopup.errorText(failure);
        check(failure.getCause()==sql,"StorageException constructor retains the exact cause object");
        check(message.equals(failure.getMessage())&&!message.contains("ATC_PRIMARY")&&!message.contains("42S22")
            &&!message.contains("INSERT INTO"),"popup keeps the friendly message without SQL diagnostics");
    }

    private static void addBatch(ClinicData data,MedicineProduct product,String remarks) {
        data.saveInventoryBatch(product,12,BASILIXIMAB_EXPIRY,remarks,false,false,"MIGRATION-NURSE","uic-main");
    }

    private static int schemaVersion(Database database) {
        return database.transaction(connection->{
            try(var query=connection.createStatement().executeQuery("SELECT version FROM schema_version WHERE id=1")) {
                query.next();return query.getInt(1);
            }
        });
    }

    private static void seedActor(Database database) {
        database.transaction(connection->{
            try(var insert=connection.prepareStatement("""
                INSERT INTO accounts(id,name,role,password_hash,active,failed_attempts,locked_until,campus_id)
                VALUES(?,?,?,?,?,?,?,?)
                """)) {
                insert.setString(1,"MIGRATION-NURSE");insert.setString(2,"Fixture nurse");insert.setString(3,"NURSE");
                insert.setString(4,"fixture");insert.setBoolean(5,true);insert.setInt(6,0);insert.setLong(7,0);
                insert.setString(8,"uic-main");insert.executeUpdate();
            }
            return null;
        });
    }

    private static void removeTree(Path directory)throws Exception {
        try(var paths=Files.walk(directory)) {
            for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
        }
    }

    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
        checks++;System.out.println("PASS: "+message);
    }

    private static final class LogCapture extends Handler {
        private LogRecord record;
        @Override public void publish(LogRecord entry){record=entry;}
        @Override public void flush(){}
        @Override public void close(){}
    }
}
