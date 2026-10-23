package clinic.app;
import java.awt.BorderLayout;
import javax.swing.JPanel;
/** Session-bound compatibility entry point. */
public class ClinicWorkspace extends JPanel {
    public ClinicWorkspace(ApplicationContext app, Runnable signOut) {
        super(new BorderLayout());
        add(Router.createWorkspace(app, signOut), BorderLayout.CENTER);
    }
}
