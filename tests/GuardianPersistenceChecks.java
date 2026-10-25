import clinic.model.*;
import clinic.persistence.*;
import clinic.service.*;
import java.nio.file.*;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Objects;

/** Uses only newly created temporary databases; simulates a real version-2 schema. */
public final class GuardianPersistenceChecks {
    private static int checks;
    static void equal(String name,Object expected,Object actual) {
        System.out.println(name+" | expected="+expected+" | actual="+actual);
        if(!Objects.equals(expected,actual))throw new AssertionError(name);
        checks++;
    }
    public static void main(String[] args)throws Exception {
        Path file=Files.createTempDirectory("scms-guardian-check-").resolve("clinic");
        String notificationId;
        try(Database db=new Database(file)) {
            ClinicData clinic=ClinicRepository.create(db);
            Student old=new Student("old","Old student","own-contact","CS",1,"A");
            clinic.registerPatient(old);
            // Remove only newly added columns in this disposable DB to reproduce v2.
            db.transaction(c->{try(Statement s=c.createStatement()) {
                s.execute("ALTER TABLE patients DROP COLUMN guardian_contact");
                s.execute("ALTER TABLE notifications DROP COLUMN recipient_label");
                s.execute("ALTER TABLE notifications DROP COLUMN recipient_contact");
                s.executeUpdate("UPDATE schema_version SET version=2");
            }return null;});
        }
        try(Database db=new Database(file)) {
            ClinicData clinic=ClinicRepository.create(db);
            Patient old=clinic.findPatient("old").orElseThrow();
            equal("Migration: old guardian defaults null",null,old.getGuardianContact());
            equal("Migration: own contact preserved","own-contact",old.getContactInfo());
            Student p=new Student("new","New student","student-own","CS",1,"A");
            p.setGuardianContact("guardian-separate");
            clinic.registerPatient(p);
            clinic.registerPatient(new Employee("e","Employee","employee-own","Office","Staff"));
            db.transaction(c->{try(Statement s=c.createStatement()) {
                s.executeUpdate("INSERT INTO accounts(id,name,role,password_hash,active) VALUES('n','Fixture nurse','NURSE','test-only',TRUE)");
            }return null;});
            clinic.addMedicine("Clean",10,LocalDate.now().plusDays(30),"");
            MedicineTransaction tx=clinic.dispenseMedicine("new","Clean",1,"n",false);
            NotificationLog n=clinic.createGuardianNotification("new",tx.getTransactionId(),"private diagnosis");
            notificationId=n.getNotificationId();
            clinic.updateNotificationStatus(notificationId,NotificationStatus.SENT);
            MedicineTransaction et=clinic.dispenseMedicine("e","Clean",1,"n",false);
            clinic.createGuardianNotification("e",et.getTransactionId(),"reason");
            equal("Persistence: guardian saved","guardian-separate",clinic.findPatient("new").orElseThrow().getGuardianContact());
        }
        try(Database db=new Database(file)) {
            ClinicData clinic=ClinicRepository.create(db);
            equal("Reopen: guardian retained","guardian-separate",clinic.findPatient("new").orElseThrow().getGuardianContact());
            equal("Reopen: patient own contact separate","student-own",clinic.findPatient("new").orElseThrow().getContactInfo());
            equal("Reopen: old guardian still null",null,clinic.findPatient("old").orElseThrow().getGuardianContact());
            NotificationLog n=clinic.getNotifications("new").get(0);
            equal("Reopen: notification recipient retained","guardian-separate",n.getRecipientContact());
            equal("Reopen: notification SENT retained",NotificationStatus.SENT,n.getStatus());
            equal("Reopen: same notification",notificationId,n.getNotificationId());
            equal("Reopen: employee contact labeled",true,clinic.getNotifications("e").get(0).toMessage().startsWith("Employee contact (own): employee-own"));
            equal("Reopen: clinical reason excluded",false,(n.toMessage()+n.getReason()).contains("private diagnosis"));
        }
        System.out.println("PASS: "+checks+" guardian persistence and migration checks.");
    }
}
