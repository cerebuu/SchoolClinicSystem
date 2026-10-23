package clinic.app;
import clinic.app.nav.MainShell;
import clinic.app.ui.admin.AdminShell;
import clinic.app.ui.auth.LoginPanel;
import clinic.model.User;
import javax.swing.JFrame;
public final class Router {
    private final JFrame frame;
    private final ApplicationContext app;
    public Router(JFrame frame, ApplicationContext app) {
        this.frame=frame;
        this.app=app;
    }
    public void login() {
        frame.setContentPane(new LoginPanel(app, this::workspace));
        frame.revalidate();
        frame.repaint();
    }
    public void workspace() {
        frame.setContentPane(User.ADMIN.equals(app.current().getRole())?new AdminShell(app, this::logout):new MainShell(app, this::logout));
        frame.revalidate();
        frame.repaint();
    }
    public void logout() {
        app.login.logout();
        login();
    }
}
