import clinic.app.ApplicationContext;
import clinic.model.Student;
import clinic.service.PageResult;
import clinic.model.MedicalRecord;
import java.util.List;

/** Headless checks for the new query boundary and server-side newest-first pagination. */
public final class DashboardConsultationChecks {
    public static void main(String[] args) {
        ApplicationContext app = new ApplicationContext(true);
        app.login.login("N001", "nurse123").orElseThrow();
        var clinic = app.clinic();
        clinic.registerPatient(new Student("DEMO-P", "Demo Patient", "demo", "BSIT", 2, "BSIT 2-A"));
        for (int i = 0; i < 40; i++) clinic.recordVisit("DEMO-P", List.of("Fever"), "Visit " + i, "", "N001");
        PageResult<MedicalRecord> first = app.recentVisits(0, 25, "");
        PageResult<MedicalRecord> second = app.recentVisits(1, 25, "");
        require(first.rows().size() == 25 && first.total() == 40, "First page bounded");
        require(first.rows().get(0).getReason().equals("Visit 39"), "Newest visit first");
        require(second.rows().size() == 15 && second.rows().get(14).getReason().equals("Visit 0"), "Last page ordered");
        require(app.recentVisits(0, 25, "Visit 39").total() == 1, "Filter before pagination");
        app.login.logout();
        app.login.login("A001", "assist123").orElseThrow();
        require(app.recentVisits(0, 25, "").total() == 0, "Assistant cannot browse other staff history");
        app.clinic().recordVisit("DEMO-P", List.of("Cough"), "Assistant visit", "", "A001");
        require(app.recentVisits(0, 25, "").total() == 1, "Assistant sees their newly recorded visit");
        app.login.logout();
        for (String[] account : new String[][]{{"D001", "doctor123"}, {"AD001", "admin123"}}) {
            app.login.login(account[0], account[1]).orElseThrow();
            boolean queryDenied = false, writeDenied = false;
            try { app.recentVisits(0, 25, ""); } catch (SecurityException expected) { queryDenied = true; }
            try { app.clinic().recordVisit("DEMO-P", List.of("Fever"), "Denied", "", account[0]); }
            catch (SecurityException expected) { writeDenied = true; }
            require(queryDenied && writeDenied, "Restricted role cannot read recent visits or record one");
            app.login.logout();
        }
        System.out.println("PASS recent visits: newest first; page sizes 25 and 15; filtering before paging.");
        System.out.println("PASS permissions: assistant sees own visits only; doctor and administrator reads/writes denied.");
    }
    private static void require(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
}
