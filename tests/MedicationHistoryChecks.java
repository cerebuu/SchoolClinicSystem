import clinic.app.ApplicationContext;
import clinic.core.ClinicSystem;
import clinic.model.*;
import clinic.persistence.Database;
import clinic.persistence.StorageException;
import clinic.service.InMemoryClinicSystem;
import clinic.service.PageResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Focused combined medication and inventory history checks. */
public final class MedicationHistoryChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        inMemoryHistoryIsCombinedAndPaged();
        administratorCannotAddStockWithoutPermission();
        persistentHistoryScopesMigratesAndRollsBack();
        System.out.println("Medication history checks passed: "+checks);
    }

    private static void inMemoryHistoryIsCombinedAndPaged() {
        InMemoryClinicSystem store=new InMemoryClinicSystem();
        MedicineProduct medicine=new MedicineProduct("Loratadine","R06AX13","");
        LocalDate expiry=LocalDate.now().plusDays(90);
        store.registerPatient(new Student("patient-a","Patient A","contact","BSCS",2,"A"));
        store.saveInventoryBatch(medicine,5,expiry,"",false,false,"nurse-a","uic-bajada");
        MedicineTransaction dispense=store.dispenseMedicineWithCampus("patient-a",medicine,2,"nurse-a","","uic-bajada");
        PageResult<MedicationHistoryEntry> page=store.medicationHistoryPage(0,1,"",null,null,"uic-bajada");
        check(page.total()==2&&page.rows().size()==1&&page.size()==1,"combined page count and size");
        check(store.medicationHistoryPage(1,1,"",null,null,"uic-bajada").rows().size()==1,"combined second page");
        PageResult<MedicationHistoryEntry> history=store.medicationHistoryPage(0,25,"",null,null,"uic-bajada");
        check(history.rows().get(0).dateTime().compareTo(history.rows().get(1).dateTime())>=0,"history is newest first");
        MedicationHistoryEntry stock=history.rows().stream().filter(e->e.type()==MedicationHistoryEntry.Type.INITIAL_STOCK).findFirst().orElseThrow();
        check(stock.actorId().equals("nurse-a")&&stock.campusId().equals("uic-bajada"),"stock movement has actor and campus");
        check(stock.patientId()==null&&!stock.toReport().contains("patient-a"),"stock movement contains no patient identifier");
        check(history.rows().stream().anyMatch(e->e.id().equals(dispense.getTransactionId())),"existing dispense retained in combined page");
        check(store.medicationHistoryPage(0,25,"patient-a",null,null,"uic-bajada").total()==1,"search covers dispense rows");
        check(store.medicationHistoryPage(0,25,"",null,null,"other-campus").total()==0,"campus filter applies before count");
    }

    private static void administratorCannotAddStockWithoutPermission() {
        InMemoryClinicSystem store=new InMemoryClinicSystem();
        Administrator admin=new Administrator("root","Administrator");
        clinic.service.SecuredClinicSystem secured=new clinic.service.SecuredClinicSystem(store,admin,
            new clinic.service.AuditLog());
        MedicineProduct medicine=new MedicineProduct("Antacid","","");
        rejects(SecurityException.class,()->secured.addMedicine(medicine,2,LocalDate.now().plusDays(30),""),
            "administrator without restock permission cannot add stock");
        check(store.getStock(medicine)==0&&store.medicationHistoryPage(0,10,"",null,null,null).total()==0,
            "denied admin stock request leaves no stock or movement");

        InMemoryClinicSystem authorizedStore=new InMemoryClinicSystem();
        SchoolNurse nurse=new SchoolNurse("n-1","Nurse");nurse.setCampusId("uic-bajada");
        clinic.service.SecuredClinicSystem authorized=new clinic.service.SecuredClinicSystem(authorizedStore,nurse,
            new clinic.service.AuditLog());
        authorized.addMedicine(medicine,2,LocalDate.now().plusDays(30),"");
        authorized.restockMedicine(medicine,3,LocalDate.now().plusDays(60),"");
        List<MedicationHistoryEntry> entries=authorizedStore.medicationHistoryPage(0,10,"",null,null,"uic-bajada").rows();
        check(entries.size()==2&&entries.stream().anyMatch(e->e.type()==MedicationHistoryEntry.Type.INITIAL_STOCK)
            &&entries.stream().anyMatch(e->e.type()==MedicationHistoryEntry.Type.RESTOCK),
            "direct secured add and restock APIs record movements");
        check(entries.stream().allMatch(e->e.actorId().equals("n-1")&&e.campusId().equals("uic-bajada")),
            "direct secured movements retain actor and campus");
    }

    private static void persistentHistoryScopesMigratesAndRollsBack() throws Exception {
        Path directory=Files.createTempDirectory("scms-medication-history-");
        Path databasePath=directory.resolve("clinic");
        MedicineProduct product=new MedicineProduct("Loratadine","R06AX13","");
        LocalDate firstExpiry=LocalDate.now().plusDays(60);
        String dispenseId,visitAId,visitBId;
        try {
            try(ApplicationContext app=setup(databasePath)) {
                app.login.logout();
                app.login.login("n-a","nurse-a-secret").orElseThrow();
                app.clinic().registerPatient(new Student("patient-a","Patient A","contact","BSCS",2,"A"));
                visitAId=app.clinic().recordVisit("patient-a",List.of("Cough"),"Campus A visit","","n-a").getRecordId();
                check(app.records(0,25,"").total()==1,"campus A record query returns its visit");
                check(app.clinic().getVisitHistory("patient-a").size()==1,"campus A patient history returns its visit");
                check(app.recentVisits(0,25,"",LocalDate.now().atStartOfDay(),java.time.LocalDateTime.now().plusSeconds(1)).total()==1,
                    "campus A recent visit query returns its visit");
                addStock(app,product,3,firstExpiry,false);
                Medicine batch=app.matchingMedicineBatches(product,firstExpiry).get(0);
                dispenseId=app.clinic().dispenseMedicine("patient-a",product,3,"n-a",false).getTransactionId();
                app.refillBatchSession(batch.getBatchId(),product.displayName()).accept(values(2,LocalDate.now().plusDays(75)));
                addStock(app,product,4,LocalDate.now().plusDays(90),true);
                PageResult<MedicationHistoryEntry> nurseA=app.medicationHistory(0,2,"",null,null);
                check(nurseA.total()==4&&nurseA.rows().size()==2,"SQL page combines dispense, initial stock, refill, and restock");
                check(app.medicationHistory(1,2,"",null,null).rows().size()==2,"SQL combined history paginates");
                check(app.medicationHistory(0,25,"patient-a",null,null).total()==1,"SQL search applies to patient and dispense rows");
                check(nurseA.rows().get(0).dateTime().compareTo(nurseA.rows().get(1).dateTime())>=0,"SQL history is newest first");
                check(app.medicationHistory(0,25,"",null,null).rows().stream()
                    .filter(e->e.type()!=MedicationHistoryEntry.Type.DISPENSE)
                    .allMatch(e->e.actorId().equals("n-a")&&e.campusId().equals("uic-bajada")),"SQL movement records retain actor and campus");

                app.login.logout();
                app.login.login("n-b","nurse-b-secret").orElseThrow();
                app.clinic().registerPatient(new Student("patient-b","Patient B","contact","BSCS",2,"B"));
                visitBId=app.clinic().recordVisit("patient-b",List.of("Fever"),"Campus B visit","","n-b").getRecordId();
                check(app.records(0,25,"").total()==1&&app.records(0,25,"").rows().get(0).getRecordId().equals(visitBId),
                    "campus B record query excludes campus A");
                check(app.clinic().getVisitHistory("patient-a").isEmpty(),"patient history excludes another campus");
                check(app.clinic().getVisitHistory("patient-b").size()==1,"patient history returns same-campus visit");
                check(app.recentVisits(0,25,"",LocalDate.now().atStartOfDay(),java.time.LocalDateTime.now().plusSeconds(1)).total()==1,
                    "campus B recent visits are scoped to B");
                addStock(app,new MedicineProduct("Vitamin C","A11GA01",""),1,LocalDate.now().plusDays(80),false);
                check(app.medicationHistory(0,25,"",null,null).total()==1,"campus B sees only its own movement");
                app.login.logout();
                app.login.login("n-a","nurse-a-secret").orElseThrow();
                check(app.medicationHistory(0,25,"",null,null).total()==4,"campus A cannot see campus B movement");
                check(app.records(0,25,"").total()==1&&app.records(0,25,"").rows().get(0).getRecordId().equals(visitAId),
                    "campus A record query excludes campus B");
            }

            // A legacy dispense row without campus attribution remains present but invisible to clinical users.
            try(Database database=new Database(databasePath)) {
                database.transaction(connection->{
                    try(var statement=connection.prepareStatement("UPDATE dispensing SET campus_id=NULL WHERE id=?")) {
                        statement.setString(1,dispenseId);statement.executeUpdate();
                    }
                    try(var statement=connection.prepareStatement("UPDATE visits SET campus_id=NULL WHERE id=?")) {
                        statement.setString(1,visitAId);statement.executeUpdate();
                    }
                    return null;
                });
            }
            try(ApplicationContext app=new ApplicationContext(databasePath)) {
                app.login.login("n-a","nurse-a-secret").orElseThrow();
                PageResult<MedicationHistoryEntry> scoped=app.medicationHistory(0,25,"",null,null);
                check(scoped.total()==3,"null-campus legacy dispense is hidden from clinic history");
                check(app.medicationHistory(0,25,"patient-a",null,null).total()==0,"campus filtering precedes search result counts");
                check(app.records(0,25,"").total()==0,"null-campus legacy visit is hidden from clinical records");
                check(app.recentVisits(0,25,"",LocalDate.now().minusDays(2).atStartOfDay(),java.time.LocalDateTime.now().plusSeconds(1)).total()==0,
                    "null-campus legacy visit is hidden from recent visit search");
                check(app.clinic().getVisitHistory("patient-a").isEmpty(),"null-campus legacy patient history remains hidden");
            }

            // Simulate an existing v9 database: migration adds the movement table and preserves old rows.
            try(Database database=new Database(databasePath)) {
                database.transaction(connection->{
                    try(Statement statement=connection.createStatement()) {
                        statement.execute("UPDATE schema_version SET version=9 WHERE id=1");
                        statement.execute("DROP TABLE inventory_movements");
                    }
                    return null;
                });
            }
            try(ApplicationContext app=new ApplicationContext(databasePath)) {
                app.login.login("n-a","nurse-a-secret").orElseThrow();
                check(app.clinic().getStock(product)==6,"additive migration preserves stock quantities");
                check(app.transactions(0,25,"patient-a",null,null).total()==0,
                    "additive migration keeps unattributed dispensing hidden from clinical queries");
                check(app.medicationHistory(0,25,"",null,null).total()==0,"v9 rows are not assigned invented movement history");
                MedicineProduct vitaminD=new MedicineProduct("Vitamin D","A11CC05","");
                app.clinic().addMedicine(vitaminD,2,LocalDate.now().plusDays(100),"");
                app.clinic().restockMedicine(vitaminD,1,LocalDate.now().plusDays(110),"");
                check(app.medicationHistory(0,25,"",null,null).total()==2,
                    "direct add and restock APIs persist movements after migration");
            }
            try(Database database=new Database(databasePath)) {
                database.transaction(connection->{
                    try(var statement=connection.prepareStatement("SELECT COUNT(*) FROM dispensing WHERE id=? AND campus_id IS NULL")) {
                        statement.setString(1,dispenseId);
                        try(var rows=statement.executeQuery()) {
                            rows.next();
                            check(rows.getInt(1)==1,"additive migration preserves the original unattributed dispensing row");
                        }
                    }
                    try(var statement=connection.createStatement()) {
                        statement.execute("ALTER TABLE inventory_movements ADD CONSTRAINT reject_large_movement CHECK(quantity<5)");
                    }
                    long patientColumns;
                    try(Statement statement=connection.createStatement();var rows=statement.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='INVENTORY_MOVEMENTS' AND column_name='PATIENT_ID'")) {
                        rows.next();patientColumns=rows.getLong(1);
                    }
                    check(patientColumns==0,"stock movement schema has no patient field");
                    return null;
                });
            }
            try(ApplicationContext app=new ApplicationContext(databasePath)) {
                app.login.login("n-a","nurse-a-secret").orElseThrow();
                MedicineProduct failed=new MedicineProduct("Ibuprofen","M01AE01","");
                rejects(StorageException.class,()->addStock(app,failed,5,LocalDate.now().plusDays(100),false),"movement insert failure rejects stock operation");
                check(app.clinic().getStock(failed)==0,"failed movement write rolls back stock batch");
                check(app.medicationHistory(0,25,"",null,null).total()==2,"failed movement write leaves no history row");
            }
        } finally {
            try(var files=Files.walk(directory)) {
                for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);
            }
        }
    }

    private static ApplicationContext setup(Path file) {
        ApplicationContext app=new ApplicationContext(file);
        app.login.bootstrapAdministrator("root","Administrator","root-secret");
        app.login.login("root","root-secret").orElseThrow();
        SchoolNurse nurseA=new SchoolNurse("n-a","Nurse A");nurseA.setCampusId("uic-bajada");
        SchoolNurse nurseB=new SchoolNurse("n-b","Nurse B");nurseB.setCampusId("uic-annex");
        app.admin.addUser(nurseA,"nurse-a-secret");
        app.admin.addUser(nurseB,"nurse-b-secret");
        return app;
    }

    private static void addStock(ApplicationContext app,MedicineProduct product,int quantity,LocalDate expiry,boolean restock) {
        app.inventoryBatchSession(product,restock,false).accept(values(quantity,expiry));
    }

    private static Map<String,String> values(int quantity,LocalDate expiry) {
        return Map.of("Quantity",Integer.toString(quantity),"Expiry (YYYY-MM-DD)",expiry.toString(),"Remarks","");
    }

    private static void check(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);
        checks++;
    }

    private static void rejects(Class<? extends Throwable> type,Runnable work,String description) {
        try { work.run(); }
        catch(Throwable error) {
            if(type.isInstance(error)){checks++;return;}
            throw new AssertionError(description+" (wrong exception: "+error+")",error);
        }
        throw new AssertionError(description+" (no exception)");
    }
}
