import clinic.model.*;
import clinic.persistence.*;
import clinic.service.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Isolated regressions for consultation attribution and persistent medicine search. */
public final class BackendWorkflowRegressionChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        InMemoryClinicSystem memory=new InMemoryClinicSystem();
        consultationAttribution(memory);
        invalidCampusDoesNotDeduct(memory);
        availableBatchExpiry(memory);
        consultationCampusCannotBeReassigned();
        Path folder=Files.createTempDirectory("scms-backend-regressions-");
        try {
            try(Database database=new Database(folder.resolve("clinic"))) {
                database.transaction(c->{
                    try(var s=c.createStatement()) {
                        s.execute("INSERT INTO accounts(id,name,role,password_hash,active,campus_id) VALUES('n','Nurse','NURSE','fixture',TRUE,'C1')");
                    }
                    return null;
                });
                ClinicData persistent=ClinicRepository.create(database);
                consultationAttribution(persistent);
                medicineSearch(persistent);
                availableBatchExpiry(persistent);
                nestedTransactionsRollback(database);
                auditFailureRollsBackClinicalMutation(database,persistent);
                auditFailureRollsBackOtherWorkflows(database,persistent);
            }
        } finally {
            try(var paths=Files.walk(folder)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS: "+checks+" backend workflow regressions.");
    }
    private static void consultationAttribution(ClinicData clinic) {
        clinic.registerPatient(new Student("p","Synthetic patient","contact","CS",1,"A"));
        MedicineProduct product=new MedicineProduct("Loratadine","R06AX13","");
        MedicineProduct standalone=new MedicineProduct("Standalone medicine","A01","");
        clinic.addMedicine(product,5,LocalDate.now().plusDays(30),"");
        clinic.addMedicine(standalone,5,LocalDate.now().plusDays(30),"");
        MedicalRecord medicated=clinic.recordVisitAndAdministerWithCampus("p",List.of("Cough"),"Medicated","","n",
            List.of(new DispenseItem(product,1)),false,"C1");
        clinic.recordVisitWithCampus("p",List.of("Cough"),"No medicine","","n","C1");
        clinic.dispenseMedicineWithCampus("p",standalone,1,"n","","C1");
        List<MedicalRecord> found=clinic.searchRecords("p","","Loratadine",LocalDate.now(),"","","C1");
        check(found.size()==1&&found.get(0).getRecordId().equals(medicated.getRecordId()),
            "medicine filter returns only the linked consultation, not another same-day visit");
        check(clinic.searchRecords("p","","Standalone medicine",LocalDate.now(),"","","C1").isEmpty(),
            "standalone administrations are not attributed to unrelated consultations");
        check(clinic.transactionPage(0,25,"Standalone medicine",null,null,"C1").total()==1,
            "standalone administration remains available in medication history");
    }
    private static void invalidCampusDoesNotDeduct(InMemoryClinicSystem clinic) {
        MedicineProduct product=new MedicineProduct("Loratadine","R06AX13","");
        int before=clinic.getStock(product);
        try {
            clinic.dispenseMedicineWithCampus("p",product,1,"n","","x".repeat(501));
            throw new AssertionError("invalid campus should fail");
        } catch(IllegalArgumentException expected) { }
        check(clinic.getStock(product)==before,"invalid campus is rejected before stock deduction");
    }
    private static void availableBatchExpiry(ClinicData clinic) {
        MedicineProduct product=new MedicineProduct("Expiry regression","A01","");
        LocalDate soon=LocalDate.now().plusDays(2),later=LocalDate.now().plusDays(30);
        clinic.addMedicine(product,1,soon,"");
        clinic.dispenseMedicine("p",product,1,"n",false);
        check(clinic.dispensableMedicinePage(0,25,product.displayName()).rows().get(0).getQuantity()==0,
            "unavailable product remains visible for the disabled stock indicator");
        clinic.addMedicine(product,3,later,"");
        Medicine suggestion=clinic.dispensableMedicinePage(0,25,product.displayName()).rows().get(0);
        check(suggestion.getQuantity()==3&&suggestion.getExpirationDate().equals(later),
            "depleted batch does not determine available product expiry");
        MedicineTransaction transaction=clinic.dispenseMedicine("p",product,1,"n",false);
        check(transaction.getBatchAllocations().size()==1&&transaction.getBatchAllocations().get(0).expiry().equals(later),
            "dispensing excludes the depleted earlier batch");
    }
    private static void consultationCampusCannotBeReassigned() {
        InMemoryClinicSystem clinic=new InMemoryClinicSystem();
        clinic.registerPatient(new Student("scope","Synthetic patient","contact","CS",1,"A"));
        MedicalRecord created=clinic.recordVisitWithCampus("scope",List.of("Cough"),"Concern","","n","C1");
        assertCampusFixed(created,"C1");
        assertCampusFixed(clinic.getVisitHistory("scope").get(0),"C1");
        assertCampusFixed(clinic.recordPage(0,25,"").rows().get(0),"C1");
        assertCampusFixed(clinic.snapshot().records().get(0),"C1");
        check(clinic.recordCount("C1")==1&&clinic.recordCount("C2")==0,
            "returned consultation references cannot change campus-scoped queries");
        MedicalRecord legacy=new MedicalRecord("legacy","scope",List.of("Cough"),"Concern","","","n");
        InMemoryClinicSystem restored=new InMemoryClinicSystem(new InMemoryClinicSystem.State(
            clinic.snapshot().patients(),List.of(legacy),List.of(),List.of(),List.of()));
        assertCampusFixed(legacy,null);
        check(restored.recordCount("C2")==0&&restored.recordPage(0,25,"").rows().get(0).getCampusId()==null,
            "rehydrated legacy input cannot acquire invented campus attribution");
    }
    @SuppressWarnings("deprecation")
    private static void assertCampusFixed(MedicalRecord record,String campus) {
        try { record.setCampusId("uic-annex"); }
        catch(IllegalStateException expected) { }
        check(Objects.equals(campus,record.getCampusId()),"historical consultation campus cannot be reassigned");
    }
    private static void nestedTransactionsRollback(Database database) {
        try {
            database.transaction(c->{
                try(var s=c.createStatement()) { s.execute("INSERT INTO campuses VALUES('nested','Nested campus')"); }
                try { database.transaction(inner->{throw new IllegalArgumentException("Synthetic nested failure");}); }
                catch(IllegalArgumentException expected) { }
                return null;
            });
            throw new AssertionError("caught nested failure must prevent outer commit");
        } catch(StorageException expected) { }
        check(campusCount(database,"nested")==0,"caught nested failure marks outer transaction rollback-only");
        try {
            database.transaction(c->{
                database.transaction(inner->{
                    try(var s=inner.createStatement()) { s.execute("INSERT INTO campuses VALUES('nested-error','Nested error')"); }
                    return null;
                });
                throw new AssertionError("Synthetic outer failure");
            });
        } catch(AssertionError expected) {
            check("Synthetic outer failure".equals(expected.getMessage()),"outer Error is preserved");
        }
        check(campusCount(database,"nested-error")==0,"outer Error rolls back successful nested writes");
    }
    private static int campusCount(Database database,String id) {
        return database.transaction(c->{
            try(var s=c.prepareStatement("SELECT COUNT(*) FROM campuses WHERE id=?")) {
                s.setString(1,id);
                try(var r=s.executeQuery()) {r.next();return r.getInt(1);}
            }
        });
    }
    private static void auditFailureRollsBackClinicalMutation(Database database,ClinicData store) {
        SchoolNurse nurse=new SchoolNurse("n","Nurse");nurse.setCampusId("uic-bajada");
        clinic.core.ClinicSystem secured=new SecuredClinicSystem(store,nurse,new AuditLog(new AdminRepository(database)));
        MedicineProduct product=new MedicineProduct("Loratadine","R06AX13","");
        int before=store.getStock(product);
        int transactions=store.transactionPage(0,25,"",null,null).total();
        database.transaction(c->{
            try(var s=c.createStatement()) { s.execute("ALTER TABLE audit_entries ADD CONSTRAINT reject_dispense_audit CHECK(action<>'dispenseMedicine')"); }
            return null;
        });
        try {
            secured.dispenseMedicine("p",product,1,"n",false);
            throw new AssertionError("audit write must fail");
        } catch(StorageException expected) { }
        check(store.getStock(product)==before,"audit insert failure rolls back stock deduction");
        check(store.transactionPage(0,25,"",null,null).total()==transactions,
            "audit insert failure rolls back administration history");
        database.transaction(c->{
            try(var s=c.createStatement()) { s.execute("ALTER TABLE audit_entries DROP CONSTRAINT reject_dispense_audit"); }
            return null;
        });
        secured.dispenseMedicine("p",product,1,"n",false);
        check(store.getStock(product)==before-1,"retry after audit failure deducts stock exactly once");
    }
    private static void auditFailureRollsBackOtherWorkflows(Database database,ClinicData store) {
        SchoolNurse nurse=new SchoolNurse("n","Nurse");nurse.setCampusId("uic-bajada");
        AuditLog audit=new AuditLog(new AdminRepository(database));
        clinic.core.ClinicSystem secured=new SecuredClinicSystem(store,nurse,audit);
        MedicineProduct product=new MedicineProduct("Loratadine","R06AX13","");
        rejectAudit(database,"registerPatient",()->secured.registerPatient(
            new Student("audit-p","Synthetic patient","contact","CS",1,"A")));
        check(store.findPatient("audit-p").isEmpty(),"patient registration rolls back when its audit fails");
        int stock=store.getStock(product);
        int movements=store.medicationHistoryPage(0,25,"",null,null,"uic-bajada").total();
        rejectAudit(database,"restock new batch",()->secured.restockMedicine(product,2,LocalDate.now().plusDays(60),""));
        check(store.getStock(product)==stock,"restock audit failure rolls back stock addition");
        check(store.medicationHistoryPage(0,25,"",null,null,"uic-bajada").total()==movements,
            "restock audit failure rolls back inventory movement");
        int visits=store.recordCount(),transactions=store.transactionPage(0,25,"",null,null).total();
        rejectAudit(database,"recordVisitAndAdminister",()->secured.recordVisitAndAdminister("p",List.of("Cough"),
            "Synthetic concern","","n",List.of(new DispenseItem(product,1)),false));
        check(store.recordCount()==visits&&store.transactionPage(0,25,"",null,null).total()==transactions
                &&store.getStock(product)==stock,"consultation audit failure rolls back visit, administration and stock");
        rejectAudit(database,"createEmergencyNotification",()->secured.createEmergencyNotification("p"));
        check(store.getNotificationsForCampus("p","uic-bajada").isEmpty(),"notification creation rolls back when its audit fails");
        NotificationLog notification=secured.createEmergencyNotification("p");
        rejectAudit(database,"updateNotificationStatus",()->secured.updateNotificationStatus(
            notification.getNotificationId(),NotificationStatus.SENT));
        check(store.getNotificationsForCampus("p","uic-bajada").get(0).getStatus()==NotificationStatus.PENDING,
            "notification status rolls back when its audit fails");
        store.addAllergy("p","Latex");
        int auditCount=audit.size();
        rejectAudit(database,"dispense confirmation",()->secured.dispenseMedicine("p",product,1,"n",true));
        check(store.getStock(product)==stock&&store.transactionPage(0,25,"",null,null).total()==transactions,
            "secondary allergy audit failure rolls back administration and stock");
        check(audit.size()==auditCount,"secondary audit failure also rolls back the primary success audit");
    }
    private static void rejectAudit(Database database,String action,Runnable mutation) {
        database.transaction(c->{
            try(var s=c.createStatement()) { s.execute("ALTER TABLE audit_entries ADD CONSTRAINT reject_workflow_audit CHECK(action<>'"+
                action.replace("'","''")+"')"); }
            return null;
        });
        try {
            mutation.run();
            throw new AssertionError("Expected synthetic audit failure for "+action);
        } catch(StorageException expected) { }
        finally {
            database.transaction(c->{
                try(var s=c.createStatement()) {s.execute("ALTER TABLE audit_entries DROP CONSTRAINT reject_workflow_audit");}
                return null;
            });
        }
    }
    private static void medicineSearch(ClinicData clinic) {
        MedicineProduct product=new MedicineProduct("Loratadine","R06AX13","");
        check(clinic.dispensableMedicinePage(0,25,product.displayName()).total()==1,
            "persistent medicine suggestions accept the medicine name");
        check(clinic.dispensableMedicinePage(0,25,"loratadine").total()==1,
            "persistent suggestions match names without case sensitivity");
        check(clinic.transactionPage(0,25,product.displayName(),null,null,"C1").total()==1,
            "persistent administration search accepts the displayed product label");
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
        checks++;
    }
}
