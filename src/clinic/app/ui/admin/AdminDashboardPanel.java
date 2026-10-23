package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import javax.swing.JLabel;
import java.awt.BorderLayout;
public final class AdminDashboardPanel extends BasePanel {
    public AdminDashboardPanel(ApplicationContext app) {
        super("Administrator dashboard");
        JLabel summary=new JLabel("Loading...");
        javax.swing.JPanel content = new javax.swing.JPanel(new BorderLayout());
        content.setBorder(clinic.app.Theme.padding());
        content.add(summary, BorderLayout.NORTH);
        content.add(new JLabel("Use the navigation to manage records. Counts reflect this session."), BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        action("Refresh", ()->load(app, summary));
        load(app, summary);
    }
    private void load(ApplicationContext app, JLabel target) {
        Actions.run(this, ()->"User accounts: "+app.admin.userCount()+" | Campuses: "+app.admin.campusCount(), target::setText);
    }
}
