import clinic.app.ApplicationContext;
import clinic.core.ClinicSystem;
import clinic.model.*;
import clinic.persistence.*;
import clinic.service.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

public final class ConsultationAdministrationChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        memoryWorkflow();
        optionalAndMultipleMedicines();
        persistentWorkflow();
        System.out.println("PASS: "+checks+" consultation administration checks.");
    }

    private static void optionalAndMultipleMedicines() {
        InMemoryClinicSystem data=new InMemoryClinicSystem();
        SchoolNurse nurse=new SchoolNurse("n","Nurse");nurse.setCampusId("uic-bajada");
        ClinicSystem clinic=new SecuredClinicSystem(data,nurse,new AuditLog());
        clinic.registerPatient(new Student("p","Patient","contact","CS",1,"A"));
        MedicalRecord noMedicine=clinic.recordVisitAndAdminister("p",List.of("Cough"),"Review","","n",List.of(),false);
        check(noMedicine.getMedicationGiven().isEmpty()&&data.transactionPage(0,25,"",null,null).total()==0,
            "consultation without medicines saves without an administration");
        MedicineProduct first=new MedicineProduct("First medicine","A01","");
        MedicineProduct second=new MedicineProduct("Second medicine","A01","");
        data.addMedicine(first,3,LocalDate.now().plusDays(30),"");
        data.addMedicine(second,4,LocalDate.now().plusDays(30),"");
        MedicalRecord multiple=clinic.recordVisitAndAdminister("p",List.of("Cough"),"Review","","n",
            List.of(new DispenseItem(first,1),new DispenseItem(second,2)),false);
        check(clinic.getStock(first)==2&&clinic.getStock(second)==2,
            "one consultation deducts each distinct medicine independently");
        check(data.transactionPage(0,25,"",null,null).rows().stream()
                .allMatch(transaction->multiple.getRecordId().equals(transaction.getVisitId())),
            "all administrations link to the same consultation");
        rejects(IllegalStateException.class,()->clinic.recordVisitAndAdminister("p",List.of("Cough"),"Review","","n",
            List.of(new DispenseItem(first,1),new DispenseItem(second,100)),false),
            "a shortage in the second medicine rejects the entire consultation");
        check(clinic.getStock(first)==2&&clinic.getStock(second)==2&&clinic.getVisitHistory("p").size()==2,
            "second-medicine shortage preserves both stocks and consultation count");
    }

    private static void memoryWorkflow() {
        InMemoryClinicSystem data=new InMemoryClinicSystem();
        SchoolNurse nurse=new SchoolNurse("n","Nurse");nurse.setCampusId("uic-bajada");
        ClinicSystem clinic=new SecuredClinicSystem(data,nurse,new AuditLog());
        clinic.registerPatient(new Student("p","Test patient","contact","CS",1,"A"));
        MedicineProduct product=new MedicineProduct("Vitamin C","A11GA01","");
        MedicineProduct otherMedicine=new MedicineProduct("Vitamin D","A11CC05","");
        data.addMedicine(product,2,LocalDate.now().plusDays(5),"");
        data.addMedicine(product,4,LocalDate.now().plusDays(12),"");
        data.addMedicine(otherMedicine,5,LocalDate.now().plusDays(8),"");
        MedicalRecord visit=clinic.recordVisitAndAdminister("p",List.of("Cough"),"Concern","Notes","n",
            List.of(new DispenseItem(product,1),new DispenseItem(product,2)),false);
        MedicineTransaction transaction=data.transactionPage(0,25,"",null,null).rows().get(0);
        check(visit.getCampusId().equals("uic-bajada"),"memory visit stores trusted campus");
        check(transaction.getVisitId().equals(visit.getRecordId()),"medicine transaction links to consultation");
        check(transaction.getQuantity()==3&&transaction.getBatchAllocations().size()==2,"duplicate products merge and allocate across batches");
        check(transaction.getBatchAllocations().get(0).expiry().isBefore(transaction.getBatchAllocations().get(1).expiry()),
            "FEFO allocation is ordered by expiry");
        check(transaction.getCampusId().equals("uic-bajada"),"memory transaction stores trusted campus");
        check(clinic.getStock(product)==3&&clinic.getStock(otherMedicine)==5,"different medicine names retain separate stock");
        int visits=data.recordPage(0,25,"").total(),transactions=data.transactionPage(0,25,"",null,null).total();
        rejects(IllegalStateException.class,()->clinic.recordVisitAndAdminister("p",List.of("Cough"),"Too much","",
            "n",List.of(new DispenseItem(product,100)),false),"insufficient stock rejects composite operation");
        check(data.recordPage(0,25,"").total()==visits&&data.transactionPage(0,25,"",null,null).total()==transactions,
            "failed composite operation leaves visits and transactions unchanged");
        rejects(SecurityException.class,()->new SecuredClinicSystem(data,new SchoolNurse("u","Unassigned"),new AuditLog())
            .recordVisitAndAdminister("p",List.of("Cough"),"Concern","","u",List.of(),false),
            "unassigned clinical user is blocked");
    }

    private static void persistentWorkflow() throws Exception {
        Path root=Path.of("verification");
        Files.createDirectories(root);
        Path folder=Files.createTempDirectory(root,"consultation-administration-");
        Path database=folder.resolve("clinic");
        try {
            try(ApplicationContext app=new ApplicationContext(database)) {
                app.login.bootstrapAdministrator("root","Administrator","admin-password");
                app.login.login("root","admin-password").orElseThrow();
                SchoolNurse nurse=new SchoolNurse("n","Nurse");nurse.setCampusId("uic-bajada");
                app.admin.addUser(nurse,"nurse-password");
                app.login.logout();
                app.login.login("n","nurse-password").orElseThrow();
                ClinicSystem clinic=app.clinic();
                clinic.registerPatient(new Student("p","Persistent patient","contact","CS",1,"A"));
                MedicineProduct product=new MedicineProduct("Ibuprofen","M01AE01","");
                clinic.addMedicine(product,2,LocalDate.now().plusDays(4),"");
                clinic.addMedicine(product,3,LocalDate.now().plusDays(9),"");
                MedicalRecord visit=clinic.recordVisitAndAdminister("p",List.of("Fever"),"Concern","",
                    "n",List.of(new DispenseItem(product,4)),false);
                MedicineTransaction tx=app.transactions(0,25,"",null,null).rows().get(0);
                check(visit.getCampusId().equals("uic-bajada")&&tx.getCampusId().equals("uic-bajada"),
                    "persistent visit and transaction store assigned campus");
                check(tx.getVisitId().equals(visit.getRecordId())&&tx.getBatchAllocations().size()==2,
                    "persistent consultation link and batch allocation save");
            }
            try(ApplicationContext app=new ApplicationContext(database)) {
                app.login.login("n","nurse-password").orElseThrow();
                ClinicSystem clinic=app.clinic();
                MedicalRecord visit=clinic.getVisitHistory("p").get(0);
                MedicineTransaction tx=app.transactions(0,25,"Ibuprofen",null,null).rows().get(0);
                check(visit.getCampusId().equals("uic-bajada"),"campus attribution survives restart");
                check(tx.getVisitId().equals(visit.getRecordId())&&tx.getBatchAllocations().stream()
                    .mapToInt(BatchAllocation::quantity).sum()==4,"consultation link and allocated quantities survive restart");
                check(clinic.getStock(new MedicineProduct("Ibuprofen","M01AE01",""))==1,
                    "FEFO stock decrement survives restart");
            }
            try(Database db=new Database(database)) {
                ClinicData data=ClinicRepository.create(db);
                MedicineProduct first=new MedicineProduct("Rollback first","","");
                MedicineProduct second=new MedicineProduct("Rollback second","","");
                data.addMedicine(first,3,LocalDate.now().plusDays(30),"");
                data.addMedicine(second,3,LocalDate.now().plusDays(30),"");
                int visits=data.recordCount(),transactions=data.transactionPage(0,25,"",null,null).total();
                db.transaction(connection->{
                    try(var statement=connection.createStatement()) {
                        statement.execute("ALTER TABLE dispensing ADD CONSTRAINT consultation_failure CHECK(medicine <> 'Rollback second')");
                    }
                    return null;
                });
                rejects(StorageException.class,()->data.recordVisitAndAdministerWithCampus("p",List.of("Cough"),
                    "Rollback check","","n",List.of(new DispenseItem(first,1),new DispenseItem(second,1)),false,"uic-bajada"),
                    "failure saving the second administration rolls back the transaction");
                check(data.recordCount()==visits&&data.transactionPage(0,25,"",null,null).total()==transactions,
                    "database rollback removes the new visit and both administrations");
                check(data.getStock(first)==3&&data.getStock(second)==3,
                    "database rollback restores both medicine stock deductions");
                db.transaction(connection->{
                    try(var statement=connection.createStatement()) {
                        statement.execute("ALTER TABLE dispensing DROP CONSTRAINT consultation_failure");
                    }
                    return null;
                });
                MedicalRecord retry=data.recordVisitAndAdministerWithCampus("p",List.of("Cough"),"Retry","","n",
                    List.of(new DispenseItem(first,1),new DispenseItem(second,1)),false,"uic-bajada");
                check(data.transactionPage(0,25,"Rollback",null,null).rows().stream()
                        .filter(transaction->retry.getRecordId().equals(transaction.getVisitId())).count()==2,
                    "retry after rollback saves both linked medicine administrations");
            }
        } finally {
            try(var paths=Files.walk(folder)) {
                for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean passed,String description) {
        if(!passed)throw new AssertionError(description);
        checks++;
        System.out.println("PASS: "+description);
    }

    private static void rejects(Class<? extends Throwable> expected,Runnable action,String description) {
        try { action.run(); }
        catch(Throwable failure) {
            if(!expected.isInstance(failure))throw new AssertionError(description,failure);
            checks++;
            System.out.println("PASS: "+description+" ("+failure.getClass().getSimpleName()+")");
            return;
        }
        throw new AssertionError(description);
    }
}
