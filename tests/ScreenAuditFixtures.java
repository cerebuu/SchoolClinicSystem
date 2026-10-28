import clinic.app.ApplicationContext;
import clinic.app.ui.admin.AdminDashboardPanel;
import clinic.app.ui.admin.AuditLogPanel;
import clinic.app.ui.admin.CampusManagementPanel;
import clinic.app.ui.assistant.AiNurseAssistantPanel;
import clinic.app.ui.consultations.ConsultationsPanel;
import clinic.app.ui.dashboard.DashboardPanel;
import clinic.app.ui.dashboard.NurseDashboardPanel;
import clinic.app.ui.dispense.DispensePanel;
import clinic.app.ui.inventory.InventoryPanel;
import clinic.app.ui.notifications.NotificationsPanel;
import clinic.app.ui.patients.PatientsPanel;
import clinic.app.ui.reports.ReportsPanel;
import clinic.model.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Isolated demo records and permission-filtered screens for the control audit. */
final class ScreenAuditFixtures {
    private ScreenAuditFixtures() {}

    static String password(String id) {
        return switch(id) { case "N001" -> "nurse123"; case "A001" -> "assist123";
            case "D001" -> "doctor123"; default -> "admin123"; };
    }

    static void seed(ApplicationContext app) {
        app.login.login("N001","nurse123").orElseThrow();
        var clinic=app.clinic();
        clinic.registerPatient(new Student("AUDIT-1","Synthetic patient","fixture","CS",1,"A"));
        clinic.addMedicine(new MedicineProduct("Audit medicine","A01",""),6,
            LocalDate.now().plusDays(60),"");
        clinic.recordVisit("AUDIT-1",List.of("Cough"),"Sample concern","","N001");
        app.login.logout();
    }

    static List<ScreenControlAudit.Screen> screens(ApplicationContext app,User user) {
        List<ScreenControlAudit.Screen> result=new ArrayList<>();
        if(user.canAccess(User.USER_MANAGEMENT)) {
            result.add(new ScreenControlAudit.Screen("Admin dashboard",new AdminDashboardPanel(app)));
            result.add(new ScreenControlAudit.Screen("Users",new clinic.app.ui.admin.UserManagementPanel(app)));
            result.add(new ScreenControlAudit.Screen("Campuses",new CampusManagementPanel(app)));
            result.add(new ScreenControlAudit.Screen("Audit log",new AuditLogPanel(app)));
            return result;
        }
        if(user instanceof SchoolNurse)result.add(new ScreenControlAudit.Screen("Nurse dashboard",new NurseDashboardPanel(app)));
        else if(user.canAccess(User.RECORD_TRANSACTION))result.add(new ScreenControlAudit.Screen("Dashboard",new DashboardPanel(app)));
        if(user instanceof SchoolNurse)result.add(new ScreenControlAudit.Screen("AI Nurse Assistant",new AiNurseAssistantPanel(app)));
        if(user.canAccess(User.RECORD_TRANSACTION)) {
            result.add(new ScreenControlAudit.Screen("Patients",new PatientsPanel(app)));
            result.add(new ScreenControlAudit.Screen("Consultations",new ConsultationsPanel(app)));
        }
        if(user.canAccess(User.DISPENSE))result.add(new ScreenControlAudit.Screen("Medication history",new DispensePanel(app)));
        if(user.canAccess(User.VIEW_STOCK))result.add(new ScreenControlAudit.Screen("Inventory",new InventoryPanel(app)));
        if(user.canAccess(User.RECORD_TRANSACTION))result.add(new ScreenControlAudit.Screen("Notifications",new NotificationsPanel(app)));
        if(user.canAccess(User.REPORTS)||user.canAccess(User.VIEW_REPORTS))
            result.add(new ScreenControlAudit.Screen("Reports",new ReportsPanel(app)));
        return result;
    }
}
