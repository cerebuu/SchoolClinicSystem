package clinic.app.nav;

import clinic.app.ApplicationContext;
import clinic.app.ui.consultations.ConsultationsPanel;
import clinic.app.ui.assistant.AiNurseAssistantPanel;
import clinic.app.ui.dashboard.NurseDashboardPanel;
import clinic.app.ui.dispense.DispensePanel;
import clinic.app.ui.inventory.InventoryPanel;
import clinic.app.ui.notifications.NotificationsPanel;
import clinic.app.ui.patients.PatientsPanel;
import clinic.app.ui.reports.ReportsPanel;
import clinic.model.SchoolNurse;
import clinic.model.User;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;

public final class NurseShell extends JPanel {
    public NurseShell(ApplicationContext app, Runnable logout) {
        super(new BorderLayout());
        User user = app.current();
        if (!(user instanceof SchoolNurse)
                || !user.canAccess(User.RECORD_TRANSACTION)
                || !user.canAccess(User.DISPENSE)
                || !user.canAccess(User.VIEW_STOCK)
                || !user.canAccess(User.REPORTS)) {
            throw new SecurityException("Nurse workspace access denied.");
        }

        add(new Navigator(List.of(
            new NavItem("Nurse dashboard", true, () -> new NurseDashboardPanel(app)),
            new NavItem("AI Nurse Assistant", true, () -> new AiNurseAssistantPanel(app)),
            new NavItem("Patients", true, () -> new PatientsPanel(app)),
            new NavItem("Consultations", true, () -> new ConsultationsPanel(app)),
            new NavItem("Medication history", true, () -> new DispensePanel(app)),
            new NavItem("Inventory", true, () -> new InventoryPanel(app)),
            new NavItem("Notifications", true, () -> new NotificationsPanel(app)),
            new NavItem("Reports", true, () -> new ReportsPanel(app))
        ), logout, user, false,app.campusLabel()), BorderLayout.CENTER);
    }
}
