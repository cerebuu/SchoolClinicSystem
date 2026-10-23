import clinic.app.*;
import clinic.core.*;
import clinic.model.*;
import clinic.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
public final class DomainChecks {
    private static int checks;
    private static void check(boolean condition, String name) {
        if(!condition)throw new AssertionError(name);
        checks++;
    }
    private static void rejects(Class<? extends Throwable> type, Runnable action, String name) {
        try {
            action.run();
            throw new AssertionError(name);
        }
        catch(Throwable ex) {
            if(!type.isInstance(ex))throw new AssertionError(name, ex);
            checks++;
        }
    }
    public static void main(String[] args) {
        AtomicLong clock=new AtomicLong(1000);
        AuditLog audit=new AuditLog();
        Login login=new Login(audit, clock::get);
        login.bootstrapAdministrator("root", "Administrator", "test-secret");
        check(login.login("root", "test-secret").isPresent(), "first administrator login");
        login.addUser(new SchoolNurse("n", "Nurse"), "n-secret");
        login.addUser(new Doctor("d", "Doctor", "General"), "d-secret");
        login.logout();
        rejects(SecurityException.class, ()->login.addUser(new NurseAssistant("x", "Assistant"), "x-secret"), "direct unauthorized add");
        check(login.login("n", "bad").isEmpty(), "failed login one");
        check(login.login("n", "bad").isEmpty(), "failed login two");
        check(login.login("n", "bad").isEmpty(), "failed login three");
        check(login.login("n", "n-secret").isEmpty(), "locked account denies correct password");
        clock.addAndGet(60001);
        check(login.login("n", "n-secret").isPresent(), "lockout expires");
        check(audit.getAll().stream().noneMatch(e->e.toString().contains("test-secret")||e.toString().contains("n-secret")), "audit contains no passwords");
        login.logout();
        check(login.login("root", "test-secret").isPresent(), "admin relogin");
        login.updateUser(new SchoolNurse("n", "Nurse revised"), false);
        login.logout();
        check(login.login("n", "n-secret").isEmpty(), "inactive account denied");
        check(login.login("root", "test-secret").isPresent(), "admin session restored");
        login.updateUser(new NurseAssistant("n", "Assistant revised"), true);
        login.logout();
        check(login.login("n", "n-secret").orElseThrow().getRole().equals(User.ASSISTANT), "role updated without replacing password");
        InMemoryClinicSystem store=new InMemoryClinicSystem();
        SchoolNurse nurse=new SchoolNurse("n", "Nurse");
        ClinicSystem clinic=new SecuredClinicSystem(store, nurse, audit);
        clinic.registerPatient(new Student("p", "Private Patient", "contact", "BSCS", 1, "A"));
        rejects(IllegalArgumentException.class, ()->clinic.registerPatient(new Student("p", "Duplicate", "contact", "BSCS", 1, "A")), "duplicate patient rejected");
        clinic.findPatient("p").orElseThrow().addAllergy("Detached mutation");
        check(!clinic.findPatient("p").orElseThrow().hasAllergy("Detached mutation"), "read profiles are detached");
        clinic.addAllergy("p", "Penicillin");
        clinic.addMedicine("Amoxicillin", 5, LocalDate.now().plusDays(10), "");
        clinic.addMedicine("Amoxicillin", 5, LocalDate.now().plusDays(20), "");
        check(!clinic.checkSafety("p", "Amoxicillin").isEmpty(), "allergy warning");
        rejects(IllegalStateException.class, ()->clinic.dispenseMedicine("p", "Amoxicillin", 1, "n", false), "override required");
        check(clinic.getStock("Amoxicillin")==10, "warning failure leaves stock intact");
        rejects(SecurityException.class, ()->clinic.dispenseMedicine("p", "Amoxicillin", 1, "other", true), "staff spoof rejected");
        MedicineTransaction tx=clinic.dispenseMedicine("p", "Amoxicillin", 6, "n", true);
        check(clinic.getStock("Amoxicillin")==4, "stock reduced across batches");
        clinic.getLowStock().get(0).addQuantity(100);
        check(clinic.getStock("Amoxicillin")==4, "read medicine batches are detached");
        check(tx.getExpirationDate().equals(LocalDate.now().plusDays(10)), "earliest expiry first");
        rejects(IllegalArgumentException.class, ()->clinic.dispenseMedicine("p", "Amoxicillin", 9, "n", true), "insufficient stock rejected");
        check(clinic.getStock("Amoxicillin")==4, "insufficient stock atomic");
        rejects(IllegalArgumentException.class, ()->clinic.addMedicine("Expired", 1, LocalDate.now().minusDays(1), ""), "expired batch rejected");
        MedicalRecord record=clinic.recordVisit("p", List.of("Fever"), "Concern", "Remarks", "n");
        check(clinic.getVisitHistory("p").size()==1, "record saved");
        check(clinic.generateReport(record).contains("Concern"), "report formatting");
        NotificationLog notification=clinic.createGuardianNotification("p", tx.getTransactionId(), "Visit");
        check(notification.toMessage().contains("Private Patient") && notification.toMessage().contains("Amoxicillin"), "guardian message uses patient and medicine");
        notification.markSent();
        check(clinic.getNotifications("p").get(0).getStatus()==NotificationStatus.PENDING, "created notification is detached");
        clinic.getNotifications("p").get(0).markSent();
        store.notificationPage(0, 25, "").rows().get(0).markSent();
        check(clinic.getNotifications("p").get(0).getStatus()==NotificationStatus.PENDING, "notification reads cannot bypass authorized update");
        clinic.updateNotificationStatus(notification.getNotificationId(), NotificationStatus.SENT);
        rejects(IllegalArgumentException.class, ()->clinic.updateNotificationStatus(notification.getNotificationId(), NotificationStatus.SENT), "sent notification cannot be sent twice");
        check(clinic.getNotifications("p").get(0).getStatus()==NotificationStatus.SENT, "notification marked sent");
        rejects(IllegalArgumentException.class, ()->clinic.createGuardianNotification("p", "unknown", "Visit"), "unknown transaction rejected");
        ClinicSystem assistant=new SecuredClinicSystem(store, new NurseAssistant("a", "Assistant"), audit);
        rejects(SecurityException.class, ()->assistant.restockMedicine("Amoxicillin", 2, LocalDate.now().plusDays(30)), "assistant cannot restock");
        rejects(SecurityException.class, ()->assistant.generateReport(record), "assistant cannot report");
        ClinicSystem doctor=new SecuredClinicSystem(store, new Doctor("d", "Doctor", ""), audit);
        check(doctor.generateReport(record).contains(record.getRecordId()), "doctor reports allowed");
        rejects(SecurityException.class, ()->doctor.dispenseMedicine("p", "Amoxicillin", 1, "d", true), "doctor dispensing denied");
        ClinicSystem adminClinic=new SecuredClinicSystem(store, new Administrator("root", "Admin"), audit);
        rejects(SecurityException.class, ()->adminClinic.dispenseMedicine("p", "Amoxicillin", 1, "root", true), "admin dispensing denied");
        check(audit.getAll().stream().noneMatch(e->e.getDetails().contains("Private Patient")||e.getDetails().contains("Penicillin")), "audit excludes patient names and allergy text");
        ApplicationContext app=new ApplicationContext(false);
        app.login.bootstrapAdministrator("root", "Admin", "secret");
        app.login.login("root", "secret");
        app.admin.saveCampus(new Campus("main", "Main campus"), false);
        app.admin.saveCampus(new Campus("main", "Updated campus"), true);
        check(app.admin.campuses(0, 25, "").rows().get(0).name().equals("Updated campus"), "campus edit");
        rejects(IllegalArgumentException.class, ()->app.admin.saveCampus(new Campus("main", "Duplicate"), false), "duplicate campus rejected");
        app.admin.addUser(new SchoolNurse("n", "Nurse"), "secret");
        app.login.logout();
        app.login.login("n", "secret");
        ClinicSystem retained=app.clinic();
        app.login.logout();
        rejects(SecurityException.class, ()->retained.findPatient("p"), "logout invalidates retained service");
        rejects(SecurityException.class, ()->app.admin.campuses(0, 25, ""), "logged-out admin access denied");
        app.login.login("n", "secret");
        rejects(SecurityException.class, ()->retained.findPatient("p"), "old service remains invalid after same-user relogin");
        rejects(SecurityException.class, ()->app.admin.saveCampus(new Campus("other", "Other"), false), "nurse cannot manage campuses");
        Login sample=Login.withSampleUsers(new AuditLog());
        check(!sample.isLoggedIn(), "demo starts logged out");
        check(sample.login("AD001", "admin123").isPresent(), "demo admin available");
        check(clinic.getLowStock().size()==1 && clinic.getLowStock().get(0).getQuantity()==4, "low stock counts medicines and aggregates batches");
        check(clinic.getExpiringWithin(30).size()==1, "empty batches excluded from expiry alerts");
        rejects(IllegalArgumentException.class, ()->clinic.recordVisit("p", List.of(" "), "Concern", "", "n"), "blank symptoms rejected by domain");
        check(clinic.getVisitHistory("p").size()==1, "invalid visit leaves history unchanged");
        rejects(SecurityException.class, ()->app.admin.summary(), "admin summary denies nurse");
        check(sample.login("AD001", "bad").isEmpty() && !sample.isLoggedIn(), "failed reauthentication clears privileged session");
        rejects(IllegalArgumentException.class, ()->clinic.dispenseMedicine("p", "Amoxicillin", 1, "n", "short"), "short override reason rejected");
        check(clinic.getStock("Amoxicillin")==4, "invalid reason leaves stock unchanged");
        MedicineTransaction documented = clinic.dispenseMedicine("p", "Amoxicillin", 1, "n", "Documented test override");
        check(documented.getRemarks().contains("Documented test override"), "override reason retained in transaction");
        check(audit.getAll().stream().noneMatch(e->e.getDetails().contains("Documented test override")), "override reason excluded from general audit");
        System.out.println("PASS: "+checks+" domain, authentication, authorization, and integrity checks.");
    }
}
