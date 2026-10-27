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
        persistentWorkflow();
        System.out.println("PASS: "+checks+" consultation administration checks.");
    }

    private static void memoryWorkflow() {
        InMemoryClinicSystem data=new InMemoryClinicSystem();
        SchoolNurse nurse=new SchoolNurse("n","Nurse");nurse.setCampusId("C1");
        ClinicSystem clinic=new SecuredClinicSystem(data,nurse,new AuditLog());
        clinic.registerPatient(new Student("p","Test patient","contact","CS",1,"A"));
        MedicineProduct product=new MedicineProduct("Vitamin C","500 mg","tablet");
        MedicineProduct lowerStrength=new MedicineProduct("Vitamin C","250 mg","tablet");
        data.addMedicine(product,2,LocalDate.now().plusDays(5),"");
        data.addMedicine(product,4,LocalDate.now().plusDays(12),"");
        data.addMedicine(lowerStrength,5,LocalDate.now().plusDays(8),"");
        MedicalRecord visit=clinic.recordVisitAndAdminister("p",List.of("Cough"),"Concern","Notes","n",
            List.of(new DispenseItem(product,1),new DispenseItem(product,2)),false);
        MedicineTransaction transaction=data.transactionPage(0,25,"",null,null).rows().get(0);
        check(visit.getCampusId().equals("C1"),"memory visit stores trusted campus");
        check(transaction.getVisitId().equals(visit.getRecordId()),"medicine transaction links to consultation");
        check(transaction.getQuantity()==3&&transaction.getBatchAllocations().size()==2,"duplicate products merge and allocate across batches");
        check(transaction.getBatchAllocations().get(0).expiry().isBefore(transaction.getBatchAllocations().get(1).expiry()),
            "FEFO allocation is ordered by expiry");
        check(transaction.getCampusId().equals("C1"),"memory transaction stores trusted campus");
        check(clinic.getStock(product)==3&&clinic.getStock(lowerStrength)==5,"strength-specific stock remains separate");
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
                app.admin.saveCampus(new Campus("C1","Campus One"),false);
                SchoolNurse nurse=new SchoolNurse("n","Nurse");nurse.setCampusId("C1");
                app.admin.addUser(nurse,"nurse-password");
                app.login.logout();
                app.login.login("n","nurse-password").orElseThrow();
                ClinicSystem clinic=app.clinic();
                clinic.registerPatient(new Student("p","Persistent patient","contact","CS",1,"A"));
                MedicineProduct product=new MedicineProduct("Ibuprofen","200 mg","tablet");
                clinic.addMedicine(product,2,LocalDate.now().plusDays(4),"");
                clinic.addMedicine(product,3,LocalDate.now().plusDays(9),"");
                MedicalRecord visit=clinic.recordVisitAndAdminister("p",List.of("Fever"),"Concern","",
                    "n",List.of(new DispenseItem(product,4)),false);
                MedicineTransaction tx=app.transactions(0,25,"",null,null).rows().get(0);
                check(visit.getCampusId().equals("C1")&&tx.getCampusId().equals("C1"),
                    "persistent visit and transaction store assigned campus");
                check(tx.getVisitId().equals(visit.getRecordId())&&tx.getBatchAllocations().size()==2,
                    "persistent consultation link and batch allocation save");
            }
            try(ApplicationContext app=new ApplicationContext(database)) {
                app.login.login("n","nurse-password").orElseThrow();
                ClinicSystem clinic=app.clinic();
                MedicalRecord visit=clinic.getVisitHistory("p").get(0);
                MedicineTransaction tx=app.transactions(0,25,"200 mg",null,null).rows().get(0);
                check(visit.getCampusId().equals("C1"),"campus attribution survives restart");
                check(tx.getVisitId().equals(visit.getRecordId())&&tx.getBatchAllocations().stream()
                    .mapToInt(BatchAllocation::quantity).sum()==4,"consultation link and allocated quantities survive restart");
                check(clinic.getStock(new MedicineProduct("Ibuprofen","200 mg","tablet"))==1,
                    "FEFO stock decrement survives restart");
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
