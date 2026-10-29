import clinic.app.ApplicationContext;
import clinic.model.*;
import clinic.service.AuditEntry;
import java.util.List;

/** Exercises the approved school assignments at both the admin service and login boundaries. */
public final class NurseCampusAssignmentChecks {
    private static int checks;

    public static void main(String[] args) {
        try(ApplicationContext app=new ApplicationContext(false)) {
            check(NurseCampus.choices().stream().map(NurseCampus::displayName).toList().equals(List.of(
                "UIC - Bajada","UIC - Annex","UIC - Main")),"exact approved choices are centralized");
            app.login.bootstrapAdministrator("root","Super Admin","root-password");
            app.login.login("root","root-password").orElseThrow();
            check(app.admin.campuses(0,25,"").rows().stream()
                .filter(campus->NurseCampus.forId(campus.id()).isPresent()).count()==3,
                "the campus catalog contains all three approved nurse choices");
            for(NurseCampus campus:NurseCampus.choices())
                check(app.admin.campusName(campus.id()).equals(campus.displayName()),
                    "campus name is consistent for "+campus.displayName());

            app.admin.saveCampus(new Campus(NurseCampus.BAJADA.id(),"Renamed school"),true);
            check(app.admin.campusName(NurseCampus.BAJADA.id()).equals("Renamed school"),
                "campus labels use the current stored campus name");
            app.admin.saveCampus(NurseCampus.BAJADA.campus(),true);
            app.admin.saveCampus(new Campus("legacy-campus","Old school"),false);
            SchoolNurse invalid=new SchoolNurse("invalid","Invalid assignment");
            invalid.setCampusId("legacy-campus");
            rejects(IllegalArgumentException.class,()->app.admin.addUser(invalid,"nurse-password"),
                "administrator service rejects a non-approved nurse campus");
            rejects(IllegalArgumentException.class,()->app.login.addUser(invalid,"nurse-password"),
                "direct account API rejects a non-approved nurse campus");

            SchoolNurse nurse=new SchoolNurse("nurse-1","Nurse One");
            nurse.setCampusId(NurseCampus.BAJADA.id());
            app.admin.addUser(nurse,"nurse-password");
            AuditEntry assigned=latestAssignment(app);
            check(assigned.getUserId().equals("root")&&assigned.getUserName().equals("Super Admin")
                    &&assigned.getRole().equals(User.ADMIN),"assignment audit identifies the acting Super Admin");
            check(assigned.getDetails().contains("nurseId=nurse-1")
                    &&assigned.getDetails().contains("previousCampus=Unassigned")
                    &&assigned.getDetails().contains("newCampus=UIC - Bajada"),
                "initial nurse assignment audit records target and new campus");
            check(assigned.getTimestamp()!=null&&assigned.isAllowed(),"assignment audit records time and success");

            SchoolNurse changed=new SchoolNurse("nurse-1","Nurse One");
            changed.setCampusId(NurseCampus.ANNEX.id());
            app.admin.updateUser(changed,true);
            AuditEntry moved=latestAssignment(app);
            check(moved.getDetails().contains("previousCampus=UIC - Bajada")
                    &&moved.getDetails().contains("newCampus=UIC - Annex"),
                "campus change audit preserves both campus names");
            app.login.logout();
            User signedIn=app.login.login("nurse-1","nurse-password").orElseThrow();
            check(signedIn.getCampusId().equals(NurseCampus.ANNEX.id()),
                "new session uses the updated campus assignment");
            SchoolNurse attemptedChange=new SchoolNurse("nurse-1","Nurse One");
            attemptedChange.setCampusId(NurseCampus.MAIN.id());
            rejects(SecurityException.class,()->app.admin.updateUser(attemptedChange,true),
                "nurse cannot change campus through the admin service");
            rejects(SecurityException.class,()->app.login.updateUser(attemptedChange,true),
                "nurse cannot change campus through the direct account API");
        }
        System.out.println("PASS: "+checks+" nurse campus assignment checks.");
    }

    private static AuditEntry latestAssignment(ApplicationContext app) {
        return app.admin.audit(0,100,"action:nurse campus assignment changed").rows().stream()
            .findFirst().orElseThrow();
    }

    private static void rejects(Class<? extends Throwable> type,Runnable action,String message) {
        try { action.run(); }
        catch(Throwable failure) {
            if(type.isInstance(failure)){checks++;return;}
            throw new AssertionError(message,failure);
        }
        throw new AssertionError(message);
    }

    private static void check(boolean passed,String message) {
        if(!passed)throw new AssertionError(message);
        checks++;
    }
}
