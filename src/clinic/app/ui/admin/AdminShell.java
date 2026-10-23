package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import javax.swing.JPanel;
import java.awt.BorderLayout;
/** Compatibility wrapper for the Superadmin workspace. */
public final class AdminShell extends JPanel {
    public AdminShell(ApplicationContext app, Runnable logout) {
        super(new BorderLayout());
        add(new SuperadminShell(app, logout), BorderLayout.CENTER);
    }
}
