import clinic.app.*;
import clinic.app.ui.common.PagedTablePanel;
import clinic.app.ui.notifications.NotificationsTableModel;
import clinic.core.ClinicSystem;
import clinic.model.*;
import clinic.notify.*;
import clinic.service.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;

public final class NotifierChecks {
    private static int checks;
    private static void equal(String name,Object expected,Object actual) {
        System.out.println(name+" | expected="+expected+" | actual="+actual);
        if(!Objects.equals(expected,actual))throw new AssertionError(name);
        checks++;
    }
    private static void truth(String name,boolean value) {
        System.out.println(name+" | "+(value?"PASS":"FAIL"));
        if(!value)throw new AssertionError(name);
        checks++;
    }
    private static Student student(String id,String contact,String guardian) {
        Student result=new Student(id,"Demo "+id,contact,"CS",1,"A");
        result.setGuardianContact(guardian);
        return result;
    }
    private static NotificationLog medicine(ClinicSystem clinic,String id) {
        clinic.addMedicine("Demo medicine",100,LocalDate.now().plusDays(30),"");
        MedicineTransaction tx=clinic.dispenseMedicine(id,"Demo medicine",1,"N1",false);
        return clinic.createGuardianNotification(id,tx.getTransactionId(),"demo reason");
    }
    private static ClinicSystem nurse(InMemoryClinicSystem store,AuditLog audit) {
        SchoolNurse user=new SchoolNurse("N1","Demo nurse");user.setCampusId("C1");
        return new SecuredClinicSystem(store,user,audit);
    }
    private static NotifierService simulated(ClinicSystem clinic) {
        return new NotifierService(clinic,NotifierConfig.simulated());
    }

    public static void main(String[] args) throws Exception { runDemo(); }
    public static void runDemo() throws Exception {
        SimulatedSender.clearOutbox();
        AuditLog audit=new AuditLog();
        InMemoryClinicSystem store=new InMemoryClinicSystem();
        ClinicSystem clinic=nurse(store,audit);
        clinic.registerPatient(student("email","student@private.test","guardian.demo@gmail.com"));
        clinic.registerPatient(student("phone","student@private.test","09171234567"));
        clinic.registerPatient(student("both","student@private.test","guardian@example.test;09171234567"));
        clinic.registerPatient(new Employee("employee","Demo employee","employee.contact@private.test","Office","Staff"));
        NotificationLog both=clinic.createEmergencyNotification("both");
        equal("Saved email and phone allow channel switching",List.of(Channel.EMAIL,Channel.SMS),
            NotificationRecipient.options(both).stream().map(NotificationRecipient.Option::channel).toList());

        NotificationLog email=medicine(clinic,"email");
        var emailOption=NotificationRecipient.select(email,Channel.EMAIL);
        System.out.println("MASKED EMAIL | "+emailOption.maskedAddress());
        System.out.println("EMAIL PREVIEW | "+email.messageBody());
        SendResult emailResult=simulated(clinic).send(email,Channel.EMAIL);
        equal("Student guardian email status",NotificationStatus.SENT,clinic.getNotifications("email").get(0).getStatus());
        truth("Student guardian email sent",emailResult.success());

        NotificationLog sms=medicine(clinic,"phone");
        var phoneOption=NotificationRecipient.select(sms,Channel.SMS);
        equal("Philippine mobile normalization","+639171234567",phoneOption.recipient().value());
        System.out.println("MASKED SMS | "+phoneOption.maskedAddress());
        System.out.println("SMS PREVIEW | "+simulated(clinic).preview(sms,Channel.SMS));
        SendResult smsResult=simulated(clinic).send(sms,Channel.SMS);
        equal("SMS body characters <= 160",true,simulated(clinic).preview(sms,Channel.SMS).length()<=160);
        truth("Student guardian SMS sent",smsResult.success());

        NotificationLog employee=medicine(clinic,"employee");
        equal("Employee uses own contact","employee.contact@private.test",employee.getRecipientContact());
        truth("Employee contact is labeled",employee.getRecipientLabel().contains("Employee contact (own)"));
        truth("Employee email sent",simulated(clinic).send(employee,Channel.EMAIL).success());

        Student missing=student("missing","student.own@private.test",null);
        clinic.registerPatient(missing);
        NotificationLog missingGuardian=clinic.createEmergencyNotification("missing");
        SendResult missingResult=simulated(clinic).send(missingGuardian,Channel.EMAIL);
        truth("No guardian fallback to student's own contact",missingResult.statusMessage().contains("no contact is on file"));
        equal("Missing guardian status unchanged",NotificationStatus.PENDING,
            clinic.getNotifications("missing").get(0).getStatus());

        clinic.registerPatient(student("invalid","own@private.test","not a valid contact"));
        NotificationLog invalid=clinic.createEmergencyNotification("invalid");
        SendResult invalidResult=simulated(clinic).send(invalid,Channel.EMAIL);
        truth("Invalid saved contact returns clear error",invalidResult.statusMessage().contains("not a valid EMAIL"));
        equal("Invalid contact status unchanged",NotificationStatus.PENDING,
            clinic.getNotifications("invalid").get(0).getStatus());

        clinic.registerPatient(student("retry","retry-own@private.test","+639171234567"));
        NotificationLog retry=clinic.createEmergencyNotification("retry");
        AtomicReference<LocalDateTime> clock=new AtomicReference<>(LocalDateTime.now().plusMinutes(1));
        AtomicInteger failures=new AtomicInteger();
        MessageSender failing=(to,subject,body)->{failures.incrementAndGet();return SendResult.failure("Provider unavailable.");};
        NotifierService failingService=new NotifierService(clinic,NotifierConfig.simulated(),
            Map.of(Channel.SMS,failing),clock::get);
        equal("First provider failure",false,failingService.send(retry,Channel.SMS).success());
        equal("First failure persisted",NotificationStatus.FAILED,clinic.getNotifications("retry").get(0).getStatus());
        truth("Cooldown refuses immediate retry",failingService.send(retry,Channel.SMS).statusMessage().contains("30 seconds"));
        clock.set(clock.get().plusSeconds(31));
        equal("Retry after cooldown reaches provider",false,failingService.send(retry,Channel.SMS).success());
        clock.set(clock.get().plusSeconds(31));
        equal("Third failure persisted",false,failingService.send(retry,Channel.SMS).success());
        clock.set(clock.get().plusSeconds(31));
        truth("Fourth attempt blocked",failingService.send(retry,Channel.SMS).statusMessage().contains("three send attempts"));
        equal("Maximum provider calls",3,failures.get());

        NotificationLog emergency=clinic.createEmergencyNotification("email");
        truth("Emergency notice sends in simulated mode",
            simulated(clinic).send(emergency,Channel.EMAIL).success());
        System.out.println("EMERGENCY CONFIRMATION | UI requires the confirmation checkbox; not exercised in this headless run.");

        NurseAssistant assistantUser=new NurseAssistant("A1","Demo assistant");assistantUser.setCampusId("C1");
        ClinicSystem assistant=new SecuredClinicSystem(store,assistantUser,audit);
        NotificationLog assistantNotice=assistant.createEmergencyNotification("phone");
        truth("Nurse assistant can send",simulated(assistant).send(assistantNotice,Channel.SMS).success());

        Doctor doctor=new Doctor("D1","Demo doctor","General");doctor.setCampusId("C1");
        for(User denied:List.of(doctor,new Administrator("AD1","Demo administrator"))) {
            ClinicSystem restricted=new SecuredClinicSystem(store,denied,audit);
            SendResult deniedResult=simulated(restricted).send(assistantNotice,Channel.SMS);
            truth(denied.getRole()+" cannot send",!deniedResult.success());
        }

        Properties liveValues=new Properties();liveValues.setProperty("mode","LIVE");
        NotifierService live=new NotifierService(clinic,NotifierConfig.fromProperties(liveValues));
        NotificationLog liveNotice=clinic.createEmergencyNotification("email");
        SendResult liveResult=live.send(liveNotice,Channel.EMAIL);
        truth("LIVE without settings fails safely",!liveResult.success()&&liveResult.statusMessage().contains("settings are incomplete"));
        System.out.println("LIVE CONFIG | "+liveResult.statusMessage());

        String auditText=audit.getAll().stream().map(entry->entry.getAction()+" "+entry.getDetails())
            .reduce("",(left,right)->left+"\n"+right);
        truth("Audit includes channel and notification ID",audit.getAll().stream().anyMatch(entry->
            entry.getAction().equals("notification sent")&&entry.getDetails().contains("channel=EMAIL")
                &&entry.getDetails().contains(email.getNotificationId())));
        truth("Failed send audit is redacted",audit.getAll().stream().anyMatch(entry->
            entry.getAction().equals("notification failed")&&entry.getDetails().contains("channel=SMS")
                &&entry.getDetails().contains(retry.getNotificationId())));
        truth("Audit excludes contacts, message text, and patient names",
            !auditText.contains("guardian.demo@gmail.com")&&!auditText.contains("09171234567")
                &&!auditText.contains("Demo email")&&!auditText.contains("Demo medicine"));

        checkAutomaticRefresh();
        truth("SMS truncation has a clear 160-character rule",
            NotifierService.limitSms("x".repeat(200)).length()==160
                &&NotifierService.limitSms("x".repeat(200)).endsWith("..."));
        System.out.println("SIMULATED OUTBOX ENTRIES | "+SimulatedSender.outbox().size());
        System.out.println("PASS: "+checks+" notifier checks.");
    }

    private static void checkAutomaticRefresh() throws Exception {
        ApplicationContext app=new ApplicationContext(false);
        try {
            app.login.bootstrapAdministrator("root","Demo admin","admin-pass");
            app.login.login("root","admin-pass").orElseThrow();
            app.admin.saveCampus(new clinic.model.Campus("C1","Campus 1"),false);
            SchoolNurse nurse=new SchoolNurse("N1","Demo nurse");nurse.setCampusId("C1");
            app.admin.addUser(nurse,"nurse-pass");
            app.login.logout();
            app.login.login("N1","nurse-pass").orElseThrow();
            ClinicSystem clinic=app.clinic();
            clinic.registerPatient(student("refresh","own@private.test","refresh.guardian@example.test"));
            NotificationLog notification=clinic.createEmergencyNotification("refresh");
            PagedTablePanel<NotificationLog> table=new PagedTablePanel<>(new NotificationsTableModel(),
                (page,size,filter)->new PageResult<>(List.of(),0));
            JPanel root=new JPanel() {
                @Override public boolean isShowing() { return true; }
            };
            root.add(table);
            root.addNotify();
            table.refreshOn(app.changes,DataTopic.NOTIFICATIONS);
            int before=table.refreshCount();
            NotifierService service=new NotifierService(clinic,NotifierConfig.simulated());
            service.send(notification,Channel.EMAIL);
            awaitRefresh(table,before);
            equal("Notifications table refreshes after send",true,table.refreshCount()>before);
            NotificationLog second=clinic.createEmergencyNotification("refresh");
            int afterCreate=table.refreshCount();
            awaitRefresh(table,afterCreate);
            int beforeSecondSend=table.refreshCount();
            service.send(second,Channel.EMAIL);
            awaitRefresh(table,beforeSecondSend);
            equal("Notifications table refreshes after every send",true,table.refreshCount()>beforeSecondSend);
            root.removeNotify();
        } finally { app.close(); }
    }

    private static void awaitRefresh(PagedTablePanel<NotificationLog> table,int before)throws InterruptedException {
        long deadline=System.currentTimeMillis()+2500;
        while(table.refreshCount()<=before&&System.currentTimeMillis()<deadline)Thread.sleep(25);
    }

}
