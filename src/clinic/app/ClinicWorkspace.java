package clinic.app;
import clinic.app.nav.MainShell;
import clinic.app.ui.admin.AdminShell;
import clinic.model.User;
import java.awt.BorderLayout;
import javax.swing.JPanel;
/** Session-bound compatibility entry point. */
public class ClinicWorkspace extends JPanel {
    public ClinicWorkspace(ApplicationContext app, Runnable signOut) {
        super(new BorderLayout());
        add(User.ADMIN.equals(app.current().getRole())?new AdminShell(app, signOut):new MainShell(app, signOut));
    }
}
