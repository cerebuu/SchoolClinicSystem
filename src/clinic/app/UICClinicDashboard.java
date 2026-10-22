package clinic.app;
import javax.swing.JPanel;
import java.awt.BorderLayout;
/** Legacy dashboard entry point delegates to the shared reports view. */
public class UICClinicDashboard extends JPanel {
    public UICClinicDashboard(ApplicationContext app) {
        super(new BorderLayout());
        app.require(clinic.model.User.RECORDS);
        add(new clinic.app.ui.reports.ReportsPanel(app));
    }
}
