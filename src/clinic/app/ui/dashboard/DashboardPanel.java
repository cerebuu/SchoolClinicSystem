package clinic.app.ui.dashboard;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.model.User;
import java.awt.BorderLayout;
import javax.swing.*;

public final class DashboardPanel extends BasePanel {
    public DashboardPanel(ApplicationContext app) {
        super("Clinic dashboard");
        boolean canRecord = app.current().canAccess(User.RECORD_TRANSACTION);
        JLabel summary = new JLabel(canRecord ? "Loading clinic summary..."
            : "Consultation recording is not available for this account.");
        JPanel content = new JPanel(new BorderLayout(clinic.app.Theme.GAP, clinic.app.Theme.GAP));
        content.add(summary, BorderLayout.NORTH);
        content.add(new RecentVisitsPanel(app, () -> load(app, summary)), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        if (canRecord) {
            action("Refresh", () -> load(app, summary));
            load(app, summary);
        }
    }

    private void load(ApplicationContext app, JLabel target) {
        Actions.run(this, () -> "Registered patients: " + app.patientCount()
            + " | Consultations: " + app.visitCount(), target::setText);
    }
}
