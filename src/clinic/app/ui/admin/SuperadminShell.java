package clinic.app.ui.admin;

import clinic.app.ApplicationContext;
import clinic.app.nav.NavItem;
import clinic.app.nav.Navigator;
import clinic.model.Administrator;
import clinic.model.User;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;

public final class SuperadminShell extends JPanel {
    public SuperadminShell(ApplicationContext app, Runnable logout) {
        super(new BorderLayout());
        User user = app.current();
        if (!(user instanceof Administrator)
                || !user.canAccess(User.USER_MANAGEMENT)
                || !user.canAccess(User.AUDIT_LOG)) {
            throw new SecurityException("Superadmin workspace access denied.");
        }

        add(new Navigator(List.of(
            new NavItem("Dashboard", true, () -> new AdminDashboardPanel(app)),
            new NavItem("Users", true, () -> new UserManagementPanel(app)),
            new NavItem("Campuses", true, () -> new CampusManagementPanel(app)),
            new NavItem("Audit log", true, () -> new AuditLogPanel(app))
        ), logout, user, true), BorderLayout.CENTER);
    }
}
