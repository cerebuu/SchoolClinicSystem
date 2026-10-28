package clinic.app.ui.notifications;

import clinic.app.ApplicationContext;
import clinic.app.DataTopic;
import clinic.model.Employee;
import clinic.model.MedicineTransaction;
import clinic.model.Student;
import clinic.service.AuditEntry;
import clinic.service.AuditLog;
import clinic.service.GuardianMessageTemplates;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Fixed notification types, patient-only transactions, audit privacy, and data refresh checks. */
public final class NotificationWorkflowChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        AuditLog audit=new AuditLog();
        try(ApplicationContext app=new ApplicationContext(true,audit)) {
            app.login.login("N001","nurse123").orElseThrow();
            seed(app);
            MedicineTransaction latest=transactions(app);
            List<MedicineTransaction> choices=NotificationTransactions.latestForPatient(app,"S-1");
            System.out.println("FIELD transaction picker | Patient ID=S-1 | choices="+
                choices.stream().map(tx->tx.getTransactionId()+" "+tx.getMedicineDisplayName()+" × "+tx.getQuantity()).toList()+
                " | None available=true | medicine-given Save without transaction=false");
            check(choices.size()==2&&choices.get(0).getTransactionId().equals(latest.getTransactionId()),
                "transaction choices are exact-patient and newest first");
            int before=app.clinic().getNotifications("S-1").size();
            rejects(()->app.clinic().createGuardianNotification("S-1",null,"MEDICINE_GIVEN"),
                "medicine-given type requires a transaction");
            check(app.clinic().getNotifications("S-1").size()==before,"rejected transactionless medicine saves nothing");

            CountDownLatch refreshed=new CountDownLatch(1);
            var subscription=app.changes.subscribe(refreshed::countDown,DataTopic.NOTIFICATIONS);
            var notification=app.clinic().createGuardianNotification("S-1",latest.getTransactionId(),"MEDICINE_GIVEN");
            System.out.println("FIELD medicine-given saved | Patient ID=S-1 | Transaction ID="+
                latest.getTransactionId()+" | Type=Medicine given | Save enabled=true with selected transaction");
            check(notification.getMessageType()==GuardianMessageTemplates.MessageType.MEDICINE_GIVEN,
                "medicine notification uses fixed template type");
            check(notification.getReason().equals("Medicine given")&&!notification.messageBody().contains("private clinical detail"),
                "notification stores a fixed label and no user-entered clinical text");
            check(refreshed.await(2,TimeUnit.SECONDS),"secured notification save publishes the table refresh topic");
            subscription.close();

            var visit=app.clinic().createGuardianNotification("S-1",null,"CLINIC_VISIT");
            System.out.println("FIELD visit saved | Patient ID=S-1 | Transaction ID=None | Type=Visit | Save enabled=true");
            check(visit.getTransactionId()==null&&visit.getMessageType()==GuardianMessageTemplates.MessageType.CLINIC_VISIT,
                "None creates a visit notification without a transaction");
            var employee=app.clinic().createGuardianNotification("E-1",null,"CLINIC_VISIT");
            check(employee.getRecipientLabel().equals("Employee contact (own)"),"employee notification uses their own contact");
            rejects(()->app.clinic().createGuardianNotification("S-2",null,"CLINIC_VISIT"),
                "student without guardian contact cannot create notification");
            List<AuditEntry> events=audit.getAll().stream()
                .filter(entry->entry.getAction().equals("createGuardianNotification")&&entry.isAllowed()).toList();
            check(events.size()==3&&events.get(0).getDetails().startsWith("notificationId="),
                "notification audit contains only the generated notification ID");
            check(!events.toString().contains("Student Name")&&!events.toString().contains("guardian@example.test")
                &&!events.toString().contains("Paracetamol")&&!events.toString().contains("private clinical detail"),
                "notification audit excludes names, contacts, medicine, and message text");
        }
        System.out.println("PASS: "+checks+" notification workflow checks.");
    }

    private static void seed(ApplicationContext app) {
        Student student=new Student("S-1","Student Name","09171234560","BSCS",2,"CS-2A");
        student.setGuardianContact("guardian@example.test");app.clinic().registerPatient(student);
        Student noContact=new Student("S-2","No Contact","09171112222","BSCS",1,"CS-1A");app.clinic().registerPatient(noContact);
        app.clinic().registerPatient(new Employee("E-1","Employee Name","employee@example.test","Admin","Nurse"));
        app.clinic().addMedicine("Paracetamol 500mg",20,LocalDate.now().plusDays(90),"");
        app.clinic().dispenseMedicine("S-1","Paracetamol 500mg",1,"N001",true);
        try { Thread.sleep(5); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        app.clinic().dispenseMedicine("S-1","Paracetamol 500mg",1,"N001",true);
        app.clinic().dispenseMedicine("E-1","Paracetamol 500mg",1,"N001",true);
    }

    private static MedicineTransaction transactions(ApplicationContext app) {
        return app.transactions(0,10,"S-1",null,null).rows().stream()
            .filter(tx->tx.getPatientId().equals("S-1")).findFirst().orElseThrow();
    }
    private static void rejects(Runnable action,String description) {
        try { action.run(); } catch(IllegalArgumentException|IllegalStateException expected) { check(true,description);return; }
        throw new AssertionError(description);
    }
    private static void check(boolean passed,String description) { if(!passed)throw new AssertionError(description);checks++; }
}
