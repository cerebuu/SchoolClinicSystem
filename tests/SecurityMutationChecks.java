import clinic.core.*;
import clinic.model.*;
import clinic.service.*;
import java.util.*;

/** Audit completion events must reflect validated, completed writes. Uses synthetic data only. */
public final class SecurityMutationChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        patientAndNotificationWrites();
        administratorWrites();
        campusAssignmentIsolation();
        persistentAssignmentIsolation();
        administrativeAuditFailureIsolation();
        System.out.println("PASS: "+checks+" security mutation checks.");
    }
    private static void patientAndNotificationWrites() {
        var store=new InMemoryClinicSystem();
        var audit=new AuditLog();
        var nurse=new SchoolNurse("n","Nurse"); nurse.setCampusId("uic-bajada");
        ClinicSystem clinic=new SecuredClinicSystem(store,nurse,audit);
        var patient=new Student("p","Patient","contact","CS",1,"A");
        patient.setGuardianContact("guardian@example.test");
        clinic.registerPatient(patient);
        check(events(audit,"registerPatient",true)==1,"successful patient creation is audited once");
        rejects(IllegalArgumentException.class,()->clinic.registerPatient(patient));
        check(events(audit,"registerPatient",true)==1,"duplicate patient is not logged as completed registration");
        rejects(IllegalArgumentException.class,()->clinic.updatePatient("missing",patient));
        check(events(audit,"updatePatient",true)==0,"invalid profile change has no success event");
        clinic.updatePatient("p",new Student("p","Updated Patient","contact","CS",1,"A"));
        check(events(audit,"updatePatient",true)==1,"successful profile change is audited once");
        rejects(IllegalArgumentException.class,()->clinic.addAllergy("missing","Latex"));
        check(events(audit,"addAllergy",true)==0,"invalid allergy change has no success event");
        clinic.addAllergy("p","Latex");
        check(events(audit,"addAllergy",true)==1,"successful allergy addition is audited once");
        rejects(IllegalArgumentException.class,()->clinic.createEmergencyNotification("missing"));
        check(events(audit,"createEmergencyNotification",true)==0,"failed notification creation has no success event");
        NotificationLog notification=clinic.createEmergencyNotification("p");
        check(events(audit,"createEmergencyNotification",true)==1,"successful notification creation is audited once");
        clinic.updateNotificationStatus(notification.getNotificationId(),NotificationStatus.SENT);
        check(events(audit,"updateNotificationStatus",true)==1,"successful notification update is audited once");
        rejects(IllegalArgumentException.class,()->clinic.updateNotificationStatus(notification.getNotificationId(),NotificationStatus.SENT));
        check(events(audit,"updateNotificationStatus",true)==1,"repeated notification update adds no false success");
        Doctor doctor=new Doctor("d","Doctor",""); doctor.setCampusId("uic-bajada");
        ClinicSystem denied=new SecuredClinicSystem(store,doctor,audit);
        rejects(SecurityException.class,()->denied.registerPatient(patient));
        check(events(audit,"registerPatient",false)==1,"denied patient mutation remains auditable");
        RecipientNotificationWorkflow restrictedRecipients=(RecipientNotificationWorkflow)denied;
        rejects(SecurityException.class,()->restrictedRecipients.createGuardianNotificationWithRecipient(
            "p",null,"CLINIC_VISIT","Entered manually","+639171234567"));
        check(events(audit,"createGuardianNotification",false)==1
            &&!audit.getAll().toString().contains("+639171234567"),
            "recipient-aware notification creation enforces role access without auditing the contact");
        NotificationLog otherCampus=store.createEmergencyNotificationWithCampus("p","C2");
        rejects(SecurityException.class,()->clinic.updateNotificationStatus(otherCampus.getNotificationId(),NotificationStatus.SENT));
        check(events(audit,"updateNotificationStatus",false)==1,"domain campus denial remains auditable");
        check(events(audit,"updateNotificationStatus",true)==1,"domain campus denial adds no success event");
    }
    private static void administratorWrites() {
        var audit=new AuditLog(); var login=new Login(audit);
        login.bootstrapAdministrator("admin","Admin","password"); login.login("admin","password").orElseThrow();
        var admin=new AdminService(login,audit);
        admin.saveCampus(new Campus("C1","Campus One"),false);
        check(events(audit,"saveCampus",true)==1,"successful campus creation is audited once");
        rejects(IllegalArgumentException.class,()->admin.saveCampus(new Campus("C1","Duplicate"),false));
        check(events(audit,"saveCampus",true)==1,"duplicate campus adds no success event");
        var nurse=new SchoolNurse("n","Nurse"); nurse.setCampusId("uic-bajada");
        admin.addUser(nurse,"password");
        check(events(audit,"addUser",true)==1,"successful staff creation is audited once");
        rejects(IllegalArgumentException.class,()->admin.addUser(nurse,"password"));
        check(events(audit,"addUser",true)==1,"duplicate staff adds no success event");
        var unassigned=new SchoolNurse("unassigned","Nurse");
        rejects(IllegalArgumentException.class,()->admin.addUser(unassigned,"password"));
        check(events(audit,"addUser",true)==1,"missing campus adds no staff creation success");
        var missing=new SchoolNurse("missing","Missing"); missing.setCampusId("uic-bajada");
        rejects(IllegalArgumentException.class,()->admin.updateUser(missing,true));
        check(events(audit,"updateUser",true)==0,"unknown staff update has no success event");
        admin.updateUser(nurse,false);
        check(events(audit,"updateUser",true)==1,"successful staff update is audited once");
        login.logout();
        rejects(SecurityException.class,()->admin.saveCampus(new Campus("C2","Campus Two"),false));
        check(events(audit,"saveCampus",false)==1,"denied campus mutation remains auditable");
    }
    private static void campusAssignmentIsolation() {
        var login=new Login(); login.bootstrapAdministrator("admin","Admin","password");
        login.login("admin","password").orElseThrow();
        var submitted=new SchoolNurse("n","Nurse"); submitted.setCampusId("uic-bajada");
        login.addUser(submitted,"password");
        submitted.setCampusId("uic-annex");
        User listed=login.userPage(0,10,"n").rows().stream().filter(u->u.getUserId().equals("n")).findFirst().orElseThrow();
        check("uic-bajada".equals(listed.getCampusId()),"mutating submitted user cannot change stored assignment");
        rejects(IllegalStateException.class,()->listed.setCampusId("uic-annex"));
        login.logout(); User authenticated=login.login("n","password").orElseThrow();
        check("uic-bajada".equals(authenticated.getCampusId()),"sign-in retains the administrator-approved campus");
        rejects(IllegalStateException.class,()->authenticated.setCampusId("uic-annex"));
        check(login.getCurrentUser().orElseThrow()==authenticated,"current session identity remains stable");
        check("uic-bajada".equals(login.getCurrentUser().orElseThrow().getCampusId()),"authenticated output cannot bypass campus assignment");
        login.logout(); login.login("admin","password").orElseThrow();
        var replacement=new SchoolNurse("n","Updated Nurse"); replacement.setCampusId("uic-annex");
        login.updateUser(replacement,true); replacement.setCampusId("C3");
        login.logout();
        check("uic-annex".equals(login.login("n","password").orElseThrow().getCampusId()),"authorized replacement applies independently of mutable input");
        var store=new InMemoryClinicSystem(); var audit=new AuditLog();
        var actor=new SchoolNurse("direct","Direct Nurse"); actor.setCampusId("uic-bajada");
        ClinicSystem secured=new SecuredClinicSystem(store,actor,audit);
        actor.setCampusId("uic-annex");
        var patient=new Student("scope","Patient","contact","CS",1,"A");
        store.registerPatient(patient);
        MedicalRecord record=secured.recordVisit("scope",List.of("Headache"),"Concern","","direct");
        check("uic-bajada".equals(record.getCampusId()),"secured boundary captures its original trusted assignment");
        check("uic-bajada".equals(audit.getAll().get(0).getCampusId()),"mutation and audit use the same captured campus");
        User unprivileged=new User("unprivileged","Unprivileged",User.NURSE); unprivileged.setCampusId("uic-bajada");
        ClinicSystem restricted=new SecuredClinicSystem(store,unprivileged,audit);
        rejects(SecurityException.class,()->restricted.findPatient("scope"));
        check(!unprivileged.immutableCopy().canAccess(User.DISPENSE),"snapshots do not grant permissions based only on a role label");
    }
    private static void persistentAssignmentIsolation() throws Exception {
        java.nio.file.Path folder=java.nio.file.Files.createTempDirectory("scms-assignment-check-");
        try {
            try(var database=new clinic.persistence.Database(folder.resolve("clinic"))) {
                var login=new Login(new AuditLog(),new clinic.persistence.AccountRepository(database));
                login.bootstrapAdministrator("admin","Admin","password");
                login.login("admin","password").orElseThrow();
                var nurse=new SchoolNurse("n","Nurse"); nurse.setCampusId("uic-bajada");
                login.addUser(nurse,"password"); nurse.setCampusId("uic-annex");
            }
            try(var database=new clinic.persistence.Database(folder.resolve("clinic"))) {
                var login=new Login(new AuditLog(),new clinic.persistence.AccountRepository(database));
                User restored=login.login("n","password").orElseThrow();
                check("uic-bajada".equals(restored.getCampusId()),"approved assignment survives database reload");
                rejects(IllegalStateException.class,()->restored.setCampusId("uic-annex"));
                check("uic-bajada".equals(login.getCurrentUser().orElseThrow().getCampusId()),"restored identity cannot change campus through returned reference");
            }
        } finally {
            try(var paths=java.nio.file.Files.walk(folder)) {
                for(var path:paths.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(path);
            }
        }
    }
    private static void administrativeAuditFailureIsolation() throws Exception {
        java.nio.file.Path folder=java.nio.file.Files.createTempDirectory("scms-admin-audit-check-");
        try {
            try(var database=new clinic.persistence.Database(folder.resolve("clinic"))) {
                var repository=new clinic.persistence.AdminRepository(database);
                var accounts=new clinic.persistence.AccountRepository(database);
                var audit=new AuditLog(repository);
                var login=new Login(audit,accounts);
                var admin=new AdminService(login,audit,repository);
                login.bootstrapAdministrator("admin","Admin","password");
                login.login("admin","password").orElseThrow();
                admin.saveCampus(new Campus("C1","Campus One"),false);
                admin.saveCampus(new Campus("C2","Campus Two"),false);
                var nurse=new SchoolNurse("n","Original Nurse"); nurse.setCampusId("uic-bajada");
                admin.addUser(nurse,"password");
                var newNurse=new SchoolNurse("new","New Nurse"); newNurse.setCampusId("uic-bajada");
                rejectAudit(database,"addUser",()->admin.addUser(newNurse,"password"));
                check(admin.users(0,25,"new").total()==0,"failed staff audit leaves account cache unchanged");
                check(accounts.load().stream().noneMatch(a->a.user().getUserId().equals("new")),"failed staff audit rolls back persisted account");
                admin.addUser(newNurse,"password");
                check(admin.users(0,25,"new").total()==1,"staff creation can retry once after audit failure");
                var changed=new SchoolNurse("n","Changed Nurse"); changed.setCampusId("uic-annex");
                rejectAudit(database,"updateUser",()->admin.updateUser(changed,false));
                User cached=admin.users(0,25,"n").rows().stream().filter(u->u.getUserId().equals("n")).findFirst().orElseThrow();
                check(cached.getName().equals("Original Nurse")&&cached.getCampusId().equals("uic-bajada")&&login.isActive("n"),
                    "failed staff update audit preserves cached profile, campus and active status");
                var persisted=accounts.load().stream().filter(a->a.user().getUserId().equals("n")).findFirst().orElseThrow();
                check(persisted.user().getName().equals("Original Nurse")&&persisted.user().getCampusId().equals("uic-bajada")&&persisted.active(),
                    "failed staff update audit rolls back persisted profile, campus and status");
                rejectAudit(database,"saveCampus",()->admin.saveCampus(new Campus("C3","Campus Three"),false));
                check(admin.campusCount()==5&&repository.campuses().size()==5,"failed new campus audit leaves database and cache unchanged: cache="+admin.campusCount()+", database="+repository.campuses().size());
                rejectAudit(database,"saveCampus",()->admin.saveCampus(new Campus("C1","Changed Campus"),true));
                check(admin.campusName("C1").equals("Campus One")&&repository.campuses().stream()
                    .filter(c->c.id().equals("C1")).findFirst().orElseThrow().name().equals("Campus One"),
                    "failed campus edit audit preserves both stored and cached name");
            }
        } finally {
            try(var paths=java.nio.file.Files.walk(folder)) {
                for(var path:paths.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(path);
            }
        }
    }
    private static void rejectAudit(clinic.persistence.Database database,String action,Runnable mutation) {
        long cutoff=database.transaction(c->{
            try(var statement=c.createStatement();var rows=statement.executeQuery("SELECT COALESCE(MAX(seq),0) FROM audit_entries")) {
                rows.next();return rows.getLong(1);
            }
        });
        database.transaction(c->{
            try(var statement=c.createStatement()) {
                statement.execute("ALTER TABLE audit_entries ADD CONSTRAINT reject_admin_audit CHECK(seq<="+cutoff+" OR action<>'"+action+"')");
            }
            return null;
        });
        try { rejects(clinic.persistence.StorageException.class,mutation); }
        finally {
            database.transaction(c->{
                try(var statement=c.createStatement()) { statement.execute("ALTER TABLE audit_entries DROP CONSTRAINT reject_admin_audit"); }
                return null;
            });
        }
    }
    private static long events(AuditLog audit,String action,boolean allowed) {
        return audit.getAll().stream().filter(e->e.getAction().equals(action)&&e.isAllowed()==allowed).count();
    }
    private static void check(boolean passed,String message) {
        if(!passed)throw new AssertionError(message); checks++;
    }
    private static void rejects(Class<? extends RuntimeException> type,Runnable action) {
        try { action.run(); } catch(RuntimeException ex) {
            if(!type.isInstance(ex))throw new AssertionError("Expected "+type.getSimpleName(),ex);
            checks++; return;
        }
        throw new AssertionError("Expected "+type.getSimpleName());
    }
}
