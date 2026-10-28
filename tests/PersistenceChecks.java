import clinic.app.*;
import clinic.core.ClinicSystem;
import clinic.model.*;
import clinic.persistence.*;
import clinic.service.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Uses new temporary database directories, never the production database. */
public final class PersistenceChecks {
    private static int checks;
    private static void check(boolean ok,String name) { if(!ok)throw new AssertionError(name); checks++; }
    private static void rejects(Class<? extends Throwable> kind,Runnable work,String name) {
        try { work.run(); } catch(Throwable error) {
            if(!kind.isInstance(error))throw new AssertionError(name,error);
            checks++; return;
        }
        throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory(Path.of("verification"),"persistence-db-");
        Path file=folder.resolve("clinic");
        LocalDateTime visitTime;
        String recordId, notificationId;
        ClinicSystem retained;
        int auditCount;
        try(ApplicationContext app=new ApplicationContext(file)) {
            check(app.isPersistent()&&app.login.needsSetup(),"empty database requires bootstrap");
            rejects(StorageException.class,()->new ApplicationContext(file),"second application cannot share account cache");
            app.login.bootstrapAdministrator("root","Administrator","admin-password");
            check(app.login.login("root","admin-password").isPresent(),"persistent admin login");
            app.admin.saveCampus(new Campus("main","Main campus"),false);
            app.admin.saveCampus(new Campus("main","Updated campus"),true);
            rejects(IllegalArgumentException.class,()->app.admin.saveCampus(new Campus("main","Duplicate"),false),"duplicate campus");
            SchoolNurse nurseAccount=new SchoolNurse("n","Nurse");nurseAccount.setCampusId("uic-main");
            NurseAssistant assistantAccount=new NurseAssistant("a","Assistant");assistantAccount.setCampusId("main");
            Doctor doctorAccount=new Doctor("d","Doctor","");doctorAccount.setCampusId("main");
            app.admin.addUser(nurseAccount,"nurse-password");
            app.admin.addUser(assistantAccount,"assistant-password");
            app.admin.addUser(doctorAccount,"doctor-password");
            Doctor inactiveDoctor=new Doctor("d","Inactive doctor","");inactiveDoctor.setCampusId("main");
            app.admin.updateUser(inactiveDoctor,false);
            app.login.logout();
            check(app.login.login("n","nurse-password").isPresent(),"persistent nurse login");
            rejects(SecurityException.class,()->app.admin.users(0,25,""),"nurse cannot list accounts");
            rejects(SecurityException.class,()->app.admin.audit(0,25,""),"nurse cannot read audit");
            rejects(SecurityException.class,()->new clinic.app.ui.admin.SuperadminShell(app,()->{}),"nurse cannot open administrator shell");
            retained=app.clinic();
            Student patient=new Student("p'1","O'Connor <student>","contact","BSCS",2,"A");
            patient.setGuardianContact("guardian-contact");
            retained.registerPatient(patient);
            retained.registerPatient(new Employee("e1","Employee","contact","School","Teacher"));
            retained.addAllergy("p'1","Penicillin");
            rejects(IllegalArgumentException.class,()->retained.registerPatient(new Student("p'1","Duplicate","contact","CS",1,"A")),"duplicate patient");
            MedicalRecord visit=retained.recordVisit("p'1",List.of("Headache","Cough"),"Concern","Private note","n");
            visitTime=visit.getVisitDateTime(); recordId=visit.getRecordId();
            retained.addMedicine("Amoxicillin",5,LocalDate.now().plusDays(20),"first");
            retained.restockMedicine("Amoxicillin",7,LocalDate.now().plusDays(40));
            rejects(IllegalStateException.class,()->retained.dispenseMedicine("p'1","Amoxicillin",1,"n",false),"allergy still requires confirmation");
            check(retained.getStock("Amoxicillin")==12,"failed dispensing keeps persisted stock");
            MedicineTransaction tx=retained.dispenseMedicine("p'1","Amoxicillin",6,"n","Documented fixture reason");
            check(retained.getStock("Amoxicillin")==6,"FEFO stock persisted");
            NotificationLog n=retained.createGuardianNotification("p'1",tx.getTransactionId(),"Follow up");
            notificationId=n.getNotificationId();
            n.markSent();
            check(retained.getNotifications("p'1").get(0).getStatus()==NotificationStatus.PENDING,"notification read is detached");
            retained.updateNotificationStatus(notificationId,NotificationStatus.SENT);
            rejects(IllegalArgumentException.class,()->retained.createGuardianNotification("e1",tx.getTransactionId(),"Wrong patient"),"notification ownership");
            check(app.patients(0,25,"O'Connor").total()==1,"literal apostrophe search");
            check(app.patients(0,25,"' OR 1=1 --").total()==0,"SQL-shaped search remains literal");
            check(app.recentVisits(0,25,"O'Connor",LocalDate.now().atStartOfDay(),LocalDateTime.now()).total()==1,"persistent date/name filtering");
            app.login.logout();
            rejects(SecurityException.class,()->retained.getStock("Amoxicillin"),"logout revokes retained service");
            app.login.login("root","admin-password");
            auditCount=app.admin.audit(0,1000,"").total();
            check(app.admin.audit(0,1000,"").rows().stream().noneMatch(e->e.getDetails().contains("Private note")||e.getDetails().contains("Documented fixture")),"audit privacy");
        }
        try(ApplicationContext app=new ApplicationContext(file)) {
            check(!app.login.needsSetup(),"bootstrap not repeated after restart");
            check(app.login.login("d","doctor-password").isEmpty(),"inactive account survives restart");
            check(app.login.login("root","admin-password").isPresent(),"password hash survives restart");
            check(app.admin.userCount()==4&&app.admin.campusCount()==4,"admin counts and approved campus choices survive restart");
            check(app.admin.campuses(0,25,"").rows().stream()
                .anyMatch(campus->campus.id().equals("main")&&campus.name().equals("Updated campus")),
                "campus edit survives restart");
            check(app.admin.audit(0,1000,"").total()>=auditCount,"audit survives restart");
            Doctor reactivatedDoctor=new Doctor("d","Reactivated doctor","");reactivatedDoctor.setCampusId("main");
            app.admin.updateUser(reactivatedDoctor,true);
            app.login.logout();
            app.login.login("a","assistant-password");
            check(app.recentVisits(0,25,"").total()==0,"assistant cannot read nurse visits");
            rejects(SecurityException.class,()->app.clinic().restockMedicine("Amoxicillin",1,LocalDate.now().plusDays(40)),"assistant restock denied");
            app.login.logout();
            app.login.login("n","nurse-password");
            ClinicSystem clinic=app.clinic();
            check(app.patientCount()==2,"patient types survive restart");
            check(clinic.findPatient("p'1").orElseThrow().hasAllergy("Penicillin"),"allergy survives restart");
            MedicalRecord restored=clinic.getVisitHistory("p'1").get(0);
            check(restored.getRecordId().equals(recordId)&&restored.getVisitDateTime().equals(visitTime),"record ID and nanosecond timestamp preserved");
            check(restored.getSymptoms().equals(List.of("Headache","Cough"))&&restored.getAssessment().equals("Private note"),"clinical fields survive restart");
            check(clinic.getStock("Amoxicillin")==6,"stock survives restart");
            check(clinic.getNotifications("p'1").get(0).getNotificationId().equals(notificationId)
                &&clinic.getNotifications("p'1").get(0).getStatus()==NotificationStatus.SENT,"notification status survives restart");
            rejects(SecurityException.class,()->retained.findPatient("p'1"),"old context service remains revoked");
            app.login.logout();
            for(int i=0;i<3;i++)check(app.login.login("n","wrong").isEmpty(),"failed persistent login");
        }
        try(ApplicationContext app=new ApplicationContext(file)) {
            check(app.login.login("n","nurse-password").isEmpty(),"lockout survives application restart");
            check(app.login.login("root","admin-password").isPresent(),"other account is not locked");
        }
        // Inspect only this isolated database using its repository boundary.
        try(Database database=new Database(file)) {
            check(new AccountRepository(database).load().stream().noneMatch(a->a.hash().contains("password")),"no plaintext passwords stored");
            new AccountRepository(database).attempts("n",3,System.currentTimeMillis()-1);
            ClinicData clinic=ClinicRepository.create(database);
            database.transaction(c -> {
                try(var s=c.createStatement()) { s.execute("ALTER TABLE dispensing ADD CONSTRAINT simulated_failure CHECK(quantity<7)"); }
                return null;
            });
            clinic.restockMedicine("Amoxicillin",10,LocalDate.now().plusDays(50));
            int before=clinic.getStock("Amoxicillin");
            rejects(StorageException.class,()->clinic.dispenseMedicine("e1","Amoxicillin",7,"n",false),"database failure after batch update is reported");
            check(clinic.getStock("Amoxicillin")==before,"failed transaction rolls back batch updates");
            database.transaction(c -> {
                try(var s=c.createStatement()) { s.execute("ALTER TABLE dispensing DROP CONSTRAINT simulated_failure"); }
                return null;
            });
            check(clinic.dispenseMedicine("e1","Amoxicillin",1,"n",false).getQuantity()==1,"connection usable after rollback");
            rejects(StorageException.class,()->clinic.recordVisit("e1",List.of("Test"),"Test","","missing-staff"),"database foreign key rejects missing staff");
            check(clinic.getVisitHistory("e1").isEmpty(),"foreign key failure leaves no record");
            database.transaction(c -> {
                try(var s=c.createStatement();var r=s.executeQuery("SELECT remarks FROM dispensing WHERE patient_id='p''1'")) {
                    check(r.next()&&r.getString(1).contains("Documented fixture reason"),"override reason stored in database");
                }
                return null;
            });
        }
        try(ApplicationContext app=new ApplicationContext(file)) {
            check(app.login.login("n","nurse-password").isPresent(),"persisted lockout expires");
        }
        // Re-running an interrupted version-1 migration must preserve existing records.
        try(Database database=new Database(file)) {
            database.transaction(c -> { try(var s=c.createStatement()) { s.executeUpdate("UPDATE schema_version SET version=1"); } return null; });
        }
        try(ApplicationContext app=new ApplicationContext(file)) {
            check(app.login.login("n","nurse-password").isPresent()&&app.patientCount()==2,"additive migration preserves accounts and patients");
        }
        Database closed=new Database(folder.resolve("closed"));
        ClinicData closedClinic=ClinicRepository.create(closed);
        closed.close();
        rejects(StorageException.class,()->closedClinic.registerPatient(new Employee("x","Name","contact","School","Staff")),"closed storage does not report save success");
        System.out.println("PASS: "+checks+" persistence, restart, foreign-key, transaction rollback, and authorization checks.");
        System.out.println("Isolated database evidence: "+file);
    }
}
