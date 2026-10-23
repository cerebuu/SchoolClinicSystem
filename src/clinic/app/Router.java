package clinic.app;
import clinic.app.nav.MainShell;
import clinic.app.nav.NurseShell;
import clinic.app.ui.admin.SuperadminShell;
import clinic.app.ui.auth.LoginPanel;
import clinic.model.User;
import clinic.model.Administrator;
import clinic.model.SchoolNurse;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JFrame;
public final class Router {
    private final JFrame frame;
    private final ApplicationContext app;
    private final Class<? extends User> requiredRole;
    public Router(JFrame frame, ApplicationContext app) {
        this(frame, app, null);
    }
    public Router(JFrame frame, ApplicationContext app, Class<? extends User> requiredRole) {
        this.frame=frame;
        this.app=app;
        this.requiredRole=requiredRole;
    }
    public void login() {
        frame.setContentPane(new LoginPanel(app, this::workspace));
        frame.revalidate();
        frame.repaint();
    }
    public void workspace() {
        User user = app.current();
        if (requiredRole != null && !requiredRole.isInstance(user)) {
            app.login.logout();
            login();
            JOptionPane.showMessageDialog(frame,
                "This launcher is for " + roleName(requiredRole) + " accounts. Sign in with an account for that role.",
                "Role does not match launcher", JOptionPane.WARNING_MESSAGE);
            return;
        }
        frame.setContentPane(createWorkspace(app, this::logout, requiredRole));
        frame.revalidate();
        frame.repaint();
    }
    public static JPanel createWorkspace(ApplicationContext app, Runnable logout) {
        return createWorkspace(app, logout, null);
    }
    public static JPanel createWorkspace(ApplicationContext app, Runnable logout,
            Class<? extends User> requiredRole) {
        User user = app.current();
        if (requiredRole != null && !requiredRole.isInstance(user)) {
            throw new SecurityException("This launcher requires a " + roleName(requiredRole) + " account.");
        }
        if (user instanceof Administrator) return new SuperadminShell(app, logout);
        if (user instanceof SchoolNurse) return new NurseShell(app, logout);
        return new MainShell(app, logout);
    }
    private static String roleName(Class<? extends User> role) {
        if (role == Administrator.class) return "Superadmin";
        if (role == SchoolNurse.class) return "Nurse";
        return role.getSimpleName();
    }
    public void logout() {
        app.login.logout();
        login();
    }
}
