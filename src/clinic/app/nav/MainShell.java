package clinic.app.nav;
import clinic.app.ApplicationContext;
import clinic.model.User;
import clinic.app.ui.dashboard.DashboardPanel;
import clinic.app.ui.patients.PatientsPanel;
import clinic.app.ui.consultations.ConsultationsPanel;
import clinic.app.ui.dispense.DispensePanel;
import clinic.app.ui.inventory.InventoryPanel;
import clinic.app.ui.notifications.NotificationsPanel;
import clinic.app.ui.reports.ReportsPanel;
import clinic.app.ui.settings.SettingsPanel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;
public final class MainShell extends JPanel {
    public MainShell(ApplicationContext app, Runnable logout) {
        super(new BorderLayout());
        User u=app.current();
        if(User.ADMIN.equals(u.getRole()))throw new SecurityException("Use administrator workspace.");
        add(new Navigator(List.of( new NavItem("Dashboard", u.canAccess(User.RECORD_TRANSACTION), ()->new DashboardPanel(app)), new NavItem("Patients", u.canAccess(User.RECORD_TRANSACTION), ()->new PatientsPanel(app)), new NavItem("Consultations", u.canAccess(User.RECORD_TRANSACTION), ()->new ConsultationsPanel(app)), new NavItem("Dispense medicine", u.canAccess(User.DISPENSE), ()->new DispensePanel(app)), new NavItem("Inventory", u.canAccess(User.VIEW_STOCK), ()->new InventoryPanel(app)), new NavItem("Notifications", u.canAccess(User.RECORD_TRANSACTION), ()->new NotificationsPanel(app)), new NavItem("Reports", u.canAccess(User.REPORTS)||u.canAccess(User.VIEW_REPORTS), ()->new ReportsPanel(app)), new NavItem("Settings", !User.DOCTOR.equals(u.getRole()), ()->new SettingsPanel(app))), logout), BorderLayout.CENTER);
    }
}
