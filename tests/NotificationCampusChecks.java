import clinic.app.ApplicationContext;
import clinic.core.ClinicSystem;
import clinic.model.*;
import clinic.notify.*;
import clinic.persistence.Database;
import clinic.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Campus isolation checks for notifications, including unassigned legacy rows. */
public final class NotificationCampusChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        memoryChecks();
        persistentChecks();
        System.out.println("PASS: "+checks+" notification campus checks.");
    }

    private static void memoryChecks() {
        InMemoryClinicSystem data=new InMemoryClinicSystem();
        Student patient=student("shared-memory");
        data.registerPatient(patient);
        NotificationLog legacy=data.createEmergencyNotification(patient.getId());
        SchoolNurse c1User=nurse("n1","uic-bajada");
        SchoolNurse c2User=nurse("n2","uic-annex");
        ClinicSystem c1=new SecuredClinicSystem(data,c1User,new AuditLog());
        ClinicSystem c2=new SecuredClinicSystem(data,c2User,new AuditLog());
        MedicineProduct product=new MedicineProduct("Vitamin C","A11GA01","");
        c1.addMedicine(product,10,LocalDate.now().plusDays(30),"");
        NotificationLog n1=makeMedicineNotification(c1,patient.getId(),product,c1User.getUserId());
        NotificationLog n2=makeMedicineNotification(c2,patient.getId(),product,c2User.getUserId());

        check("new memory notification records actor campus", "uic-bajada", n1.getCampusId());
        check("second memory notification records its own campus", "uic-annex", n2.getCampusId());
        check("C1 notification page excludes C2 and null legacy rows", List.of(n1.getNotificationId()),
            data.notificationPageForCampus(0,25,"","uic-bajada").rows().stream().map(NotificationLog::getNotificationId).toList());
        check("C2 patient notification lookup is scoped", List.of(n2.getNotificationId()),
            c2.getNotifications(patient.getId()).stream().map(NotificationLog::getNotificationId).toList());
        check("unscoped legacy notification remains stored", 1L,
            data.notificationPage(0,25,"").rows().stream().filter(n->n.getNotificationId().equals(legacy.getNotificationId())).count());
        rejects(SecurityException.class,()->c1.updateNotificationStatus(n2.getNotificationId(),NotificationStatus.SENT),
            "C1 cannot manually mark C2 notification sent");
        rejects(SecurityException.class,()->c1.beginNotificationAttempt(n2.getNotificationId(),Channel.EMAIL,
            LocalDateTime.now()),"C1 cannot start a C2 send attempt");
        check("cross-campus attempt leaves C2 retry state unchanged",0,
            find(data.notificationPageForCampus(0,25,"","uic-annex"),n2.getNotificationId()).getAttemptCount());
        data.beginNotificationAttemptForCampus(n2.getNotificationId(),Channel.EMAIL,LocalDateTime.now().plusMinutes(5),"uic-annex");
        rejects(SecurityException.class,()->data.completeNotificationAttemptForCampus(n2.getNotificationId(),
            NotificationStatus.SENT,"uic-bajada"),"C1 cannot complete a C2 send attempt");
        check("rejected cross-campus completion leaves notification pending",NotificationStatus.PENDING,
            find(data.notificationPageForCampus(0,25,"","uic-annex"),n2.getNotificationId()).getStatus());
        rejects(SecurityException.class,()->data.updateNotificationStatusForCampus(legacy.getNotificationId(),
            NotificationStatus.SENT,"uic-bajada"),"clinical campus cannot mutate legacy null-campus notification");
    }

    private static void persistentChecks() throws Exception {
        Path folder=Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")),"scms-notification-campus-");
        Path database=folder.resolve("clinic");
        String ownId,otherId,legacyId;
        try {
            try(ApplicationContext app=new ApplicationContext(database)) {
                app.login.bootstrapAdministrator("root","Administrator","admin-password");
                app.login.login("root","admin-password").orElseThrow();
                app.admin.addUser(nurse("n1","uic-bajada"),"nurse-one-password");
                app.admin.addUser(nurse("n2","uic-annex"),"nurse-two-password");
                app.login.logout();

                app.login.login("n1","nurse-one-password").orElseThrow();
                ClinicSystem c1=app.clinic();
                Student patient=student("shared-persistent");
                c1.registerPatient(patient);
                MedicineProduct product=new MedicineProduct("Acetaminophen","N02BE01","");
                c1.addMedicine(product,12,LocalDate.now().plusDays(60),"");
                NotificationLog own=makeMedicineNotification(c1,patient.getId(),product,"n1");
                NotificationLog legacy=c1.createEmergencyNotification(patient.getId());
                ownId=own.getNotificationId();
                legacyId=legacy.getNotificationId();
                check("C1 persistent notification is tagged", "uic-bajada", own.getCampusId());

                app.login.logout();
                app.login.login("n2","nurse-two-password").orElseThrow();
                ClinicSystem c2=app.clinic();
                NotificationLog other=makeMedicineNotification(c2,patient.getId(),product,"n2");
                otherId=other.getNotificationId();
                check("C2 persistent notification is tagged", "uic-annex", other.getCampusId());
                check("C2 listing sees only its own notification",List.of(otherId),
                    ids(app.notifications(0,25,"")));

                app.login.logout();
                app.login.login("n1","nurse-one-password").orElseThrow();
                ClinicSystem c1Again=app.clinic();
                check("C1 listing hides C2 notification",List.of(legacyId,ownId),ids(app.notifications(0,25,"")));
                check("C1 patient lookup hides C2 notification",List.of(legacyId,ownId),
                    c1Again.getNotifications(patient.getId()).stream().map(NotificationLog::getNotificationId).toList());
                rejects(SecurityException.class,()->c1Again.updateNotificationStatus(otherId,NotificationStatus.SENT),
                    "persistent C1 cannot update C2 notification");
                rejects(SecurityException.class,()->c1Again.beginNotificationAttempt(otherId,Channel.EMAIL,
                    LocalDateTime.now()),"persistent C1 cannot begin C2 send");
                SendResult blocked=notifier(c1Again).send(other,Channel.EMAIL);
                check("Notifier rejects C2 notification from C1",false,blocked.success());
                app.login.logout();
                app.login.login("n2","nurse-two-password").orElseThrow();
                check("rejected send does not alter C2 retry state",0,
                    findForPatient(app.clinic(),patient.getId(),otherId).getAttemptCount());
                app.login.logout();
                app.login.login("n1","nurse-one-password").orElseThrow();
                ClinicSystem c1ForSend=app.clinic();
                SendResult sent=notifier(c1ForSend).send(own,Channel.EMAIL);
                check("same-campus send still succeeds",true,sent.success());
                NotificationLog afterSend=findForPatient(c1ForSend,patient.getId(),ownId);
                check("successful send attempt survives through current session",1,afterSend.getAttemptCount());
                check("successful send keeps selected channel",Channel.EMAIL,afterSend.getChannel());
                check("recipient contact remains available to the sender", "guardian@example.test",afterSend.getRecipientContact());
            }

            // Model an older row after the additive migration: keep it and leave ownership unknown.
            try(Database db=new Database(database)) {
                db.transaction(connection->{
                    try(PreparedStatement update=connection.prepareStatement(
                            "UPDATE notifications SET campus_id=NULL WHERE id=?")) {
                        update.setString(1,legacyId);
                        if(update.executeUpdate()!=1)throw new AssertionError("legacy fixture row missing");
                    }
                    return null;
                });
            }

            try(ApplicationContext app=new ApplicationContext(database)) {
                app.login.login("n1","nurse-one-password").orElseThrow();
                ClinicSystem c1=app.clinic();
                check("C1 page after restart contains its assigned notification only",List.of(ownId),
                    ids(app.notifications(0,25,"")));
                NotificationLog restored=findForPatient(c1,"shared-persistent",ownId);
                check("campus survives restart", "uic-bajada",restored.getCampusId());
                check("successful status survives restart",NotificationStatus.SENT,restored.getStatus());
                check("attempt count survives restart",1,restored.getAttemptCount());
                check("attempt channel survives restart",Channel.EMAIL,restored.getChannel());
                rejects(SecurityException.class,()->c1.updateNotificationStatus(legacyId,NotificationStatus.SENT),
                    "legacy null-campus row cannot be mutated by C1 after restart");
                SendResult blockedLegacy=notifier(c1).send(
                    new NotificationLog(legacyId,"shared-persistent",null,"Emergency notification",LocalDateTime.now(),
                        "Shared patient",null,GuardianMessageTemplates.MessageType.EMERGENCY,"the school clinic"),Channel.EMAIL);
                check("Notifier hides legacy null-campus row",false,blockedLegacy.success());
                check("C2 page after restart contains its assigned notification",List.of(otherId),
                    asCampus(app,"n2","nurse-two-password"));
            }

            try(Database db=new Database(database)) {
                db.transaction(connection->{
                    try(var query=connection.prepareStatement("SELECT campus_id,status,attempt_count FROM notifications WHERE id=?")) {
                        query.setString(1,legacyId);
                        try(var rows=query.executeQuery()) {
                            if(!rows.next())throw new AssertionError("legacy notification was deleted");
                            check("legacy campus remains null",null,rows.getString("campus_id"));
                            check("legacy notification state remains intact",NotificationStatus.PENDING.name(),rows.getString("status"));
                            check("legacy attempt count remains intact",0,rows.getInt("attempt_count"));
                        }
                    }
                    return null;
                });
            }
        } finally {
            try(var paths=Files.walk(folder)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
    }

    private static List<String> asCampus(ApplicationContext app,String userId,String password) {
        app.login.logout();
        app.login.login(userId,password).orElseThrow();
        return ids(app.notifications(0,25,""));
    }

    private static List<String> ids(PageResult<NotificationLog> page) {
        return page.rows().stream().map(NotificationLog::getNotificationId).toList();
    }

    private static NotificationLog makeMedicineNotification(ClinicSystem clinic,String patientId,
            MedicineProduct product,String staffId) {
        MedicineTransaction transaction=clinic.dispenseMedicine(patientId,product,1,staffId,false);
        return clinic.createGuardianNotification(patientId,transaction.getTransactionId(),"Follow up");
    }

    private static Student student(String id) {
        Student result=new Student(id,"Shared patient","student@example.test","CS",1,"A");
        result.setGuardianContact("guardian@example.test");
        return result;
    }

    private static SchoolNurse nurse(String id,String campus) {
        SchoolNurse user=new SchoolNurse(id,"Nurse "+id);
        user.setCampusId(campus);
        return user;
    }

    private static NotifierService notifier(ClinicSystem clinic) {
        return new NotifierService(clinic,NotifierConfig.simulated(),
            Map.of(Channel.EMAIL,(recipient,subject,body)->SendResult.success("Sent in test", "test-id")),
            ()->LocalDateTime.now().plusMinutes(10));
    }

    private static NotificationLog findForPatient(ClinicSystem clinic,String patientId,String notificationId) {
        return clinic.getNotifications(patientId).stream().filter(row->row.getNotificationId().equals(notificationId))
            .findFirst().orElseThrow();
    }

    private static NotificationLog find(PageResult<NotificationLog> page,String notificationId) {
        return page.rows().stream().filter(row->row.getNotificationId().equals(notificationId)).findFirst().orElseThrow();
    }

    private static void check(String description,Object expected,Object actual) {
        if(!java.util.Objects.equals(expected,actual))
            throw new AssertionError(description+"; expected="+expected+", actual="+actual);
        checks++;
        System.out.println("PASS: "+description);
    }

    private static void rejects(Class<? extends Throwable> type,Runnable action,String description) {
        try { action.run(); }
        catch(Throwable error) {
            if(!type.isInstance(error))throw new AssertionError(description+"; unexpected "+error.getClass().getName(),error);
            checks++;
            System.out.println("PASS: "+description);
            return;
        }
        throw new AssertionError(description+"; expected "+type.getSimpleName());
    }
}
