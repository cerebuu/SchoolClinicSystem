package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import clinic.app.nav.*;
import clinic.app.ui.settings.SettingsPanel;
import clinic.model.User;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;
public final class AdminShell extends JPanel {
    public AdminShell(ApplicationContext app, Runnable logout) {
        super(new BorderLayout());
        if(!User.ADMIN.equals(app.current().getRole()))throw new SecurityException("Administrator session required.");
        add(new Navigator(List.of( new NavItem("Admin dashboard", true, ()->new AdminDashboardPanel(app)), new NavItem("Users", true, ()->new UserManagementPanel(app)), new NavItem("Campuses", true, ()->new CampusManagementPanel(app)), new NavItem("Audit log", true, ()->new AuditLogPanel(app)), new NavItem("Settings", true, ()->new SettingsPanel(app))), logout), BorderLayout.CENTER);
    }
}
