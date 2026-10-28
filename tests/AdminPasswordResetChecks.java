import clinic.app.ApplicationContext;
import clinic.model.*;
import clinic.persistence.*;
import clinic.service.*;
import clinic.core.Login;
import java.nio.file.Files;
import java.util.List;

/** Isolated administrator recovery checks; never touches the configured remote database. */
public final class AdminPasswordResetChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var file=Files.createTempDirectory("scms-admin-reset-").resolve("clinic");
        try(var app=new ApplicationContext(file)) {
            app.login.bootstrapAdministrator("admin","Administrator","admin-fixture");
            app.login.login("admin","admin-fixture");
            for(User user:List.of(new SchoolNurse("n","Nurse"),new Doctor("d","Doctor",""),new NurseAssistant("a","Assistant"))) {
                user.setCampusId("uic-main");app.admin.addUser(user,"old-fixture");
            }
            app.login.logout();
            for(int i=0;i<3;i++)app.login.login("n","wrong");
            app.login.login("admin","admin-fixture");
            for(String id:List.of("n","d","a"))app.admin.resetStaffPassword(id,"new-fixture");
            rejects(IllegalArgumentException.class,()->app.admin.resetStaffPassword("admin","new-fixture"));
            rejects(IllegalArgumentException.class,()->app.admin.resetStaffPassword("missing","new-fixture"));
            rejects(IllegalArgumentException.class,()->app.admin.resetStaffPassword("n","short"));
            app.login.logout();
            for(String id:List.of("n","d","a")) {
                check(app.login.login(id,"old-fixture").isEmpty(),"old password rejected");
                check(app.login.login(id,"new-fixture").isPresent(),"new password works immediately; lockout cleared");
                rejects(SecurityException.class,()->app.admin.resetStaffPassword("d","forbidden-fixture"));
                app.login.logout();
            }
            app.login.login("admin","admin-fixture");
            User doctor=app.login.findUser("d").orElseThrow();
            app.admin.updateUser(doctor,false);
            app.admin.resetStaffPassword("d","inactive-fixture");
            check(!app.login.isActive("d"),"reset preserves inactive status");
            check(app.login.findUser("d").orElseThrow().getCampusId().equals("uic-main"),"campus preserved");
            check(app.admin.audit(0,1000,"").rows().stream().noneMatch(e->e.getDetails().contains("fixture")),"audit excludes passwords");
        }
        try(var app=new ApplicationContext(file)) {
            check(app.login.login("n","new-fixture").isPresent(),"password persists after restart");
            app.login.logout();
            check(app.login.login("d","inactive-fixture").isEmpty(),"inactive account remains blocked");
        }
        try(var db=new Database(file)) {
            var audit=new AuditLog(new AdminRepository(db));
            var login=new Login(audit,new AccountRepository(db));
            var admin=new AdminService(login,audit,new AdminRepository(db));
            login.login("admin","admin-fixture");
            db.transaction(c->{try(var s=c.createStatement()) {
                long last;
                try(var r=s.executeQuery("SELECT COALESCE(MAX(seq),0) FROM audit_entries")) { r.next();last=r.getLong(1); }
                s.execute("ALTER TABLE audit_entries ADD CONSTRAINT reset_audit_failure CHECK(action<>'resetStaffPassword' OR seq<="+last+")");
            }return null;});
            rejects(StorageException.class,()->admin.resetStaffPassword("n","rollback-fixture"));
            login.logout();
            check(login.login("n","rollback-fixture").isEmpty(),"failed audit rolls back cached password");
            check(login.login("n","new-fixture").isPresent(),"original password survives failed audit");
        }
        try(var app=new ApplicationContext(file)) {
            check(app.login.login("n","new-fixture").isPresent(),"failed audit rolls back stored password");
        }
        System.out.println("PASS: "+checks+" admin password-reset checks: all staff roles, permissions, validation, lockouts, immediate login, restart, inactive status, campus and audit privacy.");
    }
    private static void check(boolean ok,String label) { if(!ok)throw new AssertionError(label);checks++; }
    private static void rejects(Class<? extends RuntimeException> type,Runnable action) {
        try { action.run();throw new AssertionError("Expected "+type.getSimpleName()); }
        catch(RuntimeException failure) { if(!type.isInstance(failure))throw failure;checks++; }
    }
}
