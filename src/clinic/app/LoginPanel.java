package clinic.app;
import javax.swing.JPanel;
import java.awt.BorderLayout;
/** Compatibility wrapper; the routed login implementation lives in ui.auth. */
public class LoginPanel extends JPanel {
    public LoginPanel(ApplicationContext app, Runnable success) {
        super(new BorderLayout());
        add(new clinic.app.ui.auth.LoginPanel(app, success));
    }
}
