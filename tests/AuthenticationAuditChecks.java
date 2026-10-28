import clinic.core.Login;
import clinic.model.Administrator;
import clinic.model.SchoolNurse;
import clinic.persistence.AdminRepository;
import clinic.persistence.Database;
import clinic.service.AuditEntry;
import clinic.service.AuditLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Auth event attribution, logout idempotence, and secret-redaction checks. */
public final class AuthenticationAuditChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        passwordPolicy();
        memoryAudit();
        persistentAudit();
        System.out.println("PASS: "+checks+" authentication audit checks.");
    }

    private static void passwordPolicy() {
        Login login=new Login();
        try { login.bootstrapAdministrator("weak","Weak Admin","short");
            throw new AssertionError("weak first-run password should be rejected"); }
        catch(IllegalArgumentException expected) { check(login.needsSetup(),"weak first-run password does not create an account"); }
        login.bootstrapAdministrator("root","Administrator","eight888");
        login.login("root","eight888").orElseThrow();
        try { login.addUser(new Administrator("weak","Weak User"),"short");
            throw new AssertionError("weak new-account password should be rejected"); }
        catch(IllegalArgumentException expected) {
            check(login.findUser("weak").isEmpty(),"weak password does not persist a new account");
        }
        check(clinic.app.ui.common.popup.FormField.password().validator().apply("short")!=null,
            "password form explains the minimum length");
        check(clinic.app.ui.common.popup.FormField.password().validator().apply("eight888")==null,
            "password form accepts the minimum length");
    }

    private static void memoryAudit() {
        AuditLog audit=new AuditLog();
        Login login=new Login(audit,()->1_000L);
        login.bootstrapAdministrator("AD1","Administrator","admin-secret");
        check(audit.size()==0,"administrator bootstrap does not create a login event");
        check(login.login("AD1","admin-secret").isPresent(),"administrator login succeeds");
        check(audit.size()==1,"successful login creates one event");
        assertEvent(audit.getAll().get(0),"AD1","Administrator","ADMIN",null,
            "login","SUCCESS",true);

        SchoolNurse nurse=new SchoolNurse("N1","Nurse");
        nurse.setCampusId("uic-bajada");
        login.addUser(nurse,"nurse-secret");
        login.logout();
        check(audit.size()==2,"explicit administrator logout creates one event");
        assertEvent(audit.getAll().get(1),"AD1","Administrator","ADMIN",null,
            "logout","SESSION_ENDED",true);
        login.logout();
        check(audit.size()==2,"repeated logout while signed out adds no duplicate");

        check(login.login("N1","wrong-secret").isEmpty(),"incorrect password is rejected");
        assertEvent(audit.getAll().get(2),"N1","Nurse","NURSE","uic-bajada",
            "login","FAILURE",false);
        check(login.login("missing-user","unknown-secret").isEmpty(),"unknown account is rejected");
        assertEvent(audit.getAll().get(3),"unknown","unknown","unknown",null,
            "login","FAILURE",false);
        check(login.login("N1","nurse-secret").isPresent(),"nurse login succeeds");
        assertEvent(audit.getAll().get(4),"N1","Nurse","NURSE","uic-bajada",
            "login","SUCCESS",true);
        login.logout();
        assertEvent(audit.getAll().get(5),"N1","Nurse","NURSE","uic-bajada",
            "logout","SESSION_ENDED",true);

        String content=audit.getAll().toString();
        for(String secret:List.of("admin-secret","nurse-secret","wrong-secret","unknown-secret"))
            check(!content.contains(secret),"audit excludes password material: "+secret);
    }

    private static void persistentAudit() throws Exception {
        Path folder=Files.createTempDirectory("scms-auth-audit-check-");
        try {
            try(Database database=new Database(folder.resolve("clinic"))) {
                AuditLog audit=new AuditLog(new AdminRepository(database));
                Login login=new Login(audit);
                login.bootstrapAdministrator("ROOT","Persistent admin","persistent-secret");
                login.login("ROOT","persistent-secret").orElseThrow();
                login.logout();
                List<AuditEntry> entries=audit.getAll();
                check(entries.size()==2,"login and logout events persist");
                assertEvent(entries.get(0),"ROOT","Persistent admin","ADMIN",null,
                    "login","SUCCESS",true);
                assertEvent(entries.get(1),"ROOT","Persistent admin","ADMIN",null,
                    "logout","SESSION_ENDED",true);
                check(!entries.toString().contains("persistent-secret"),"persistent audit excludes password material");
            }
        } finally {
            try(var paths=Files.walk(folder)) {
                for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
    }

    private static void assertEvent(AuditEntry entry,String userId,String name,String role,String campus,
            String action,String details,boolean allowed) {
        check(entry.getTimestamp()!=null,"auth event has timestamp");
        check(userId.equals(entry.getUserId())&&name.equals(entry.getUserName())
            &&role.equals(entry.getRole()),"auth event has actor identity and role");
        check(java.util.Objects.equals(campus,entry.getCampusId()),"auth event has actor campus");
        check(action.equals(entry.getAction())&&details.equals(entry.getDetails())
            &&allowed==entry.isAllowed(),"auth event has generic action/result");
    }

    private static void check(boolean condition,String label) {
        if(!condition)throw new AssertionError(label);
        checks++;
    }
}
