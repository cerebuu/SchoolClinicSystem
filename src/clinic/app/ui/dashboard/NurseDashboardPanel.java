package clinic.app.ui.dashboard;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.AsyncLoader;
import clinic.app.ui.common.BasePanel;
import clinic.app.ui.common.KpiCard;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.model.SchoolNurse;
import clinic.model.User;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class NurseDashboardPanel extends BasePanel {
    private final KpiCard patients = new KpiCard("Registered patients", "—", "Clinic records");
    private final KpiCard consultations = new KpiCard("Today's consultations", "—", "Today");
    private final KpiCard lowStock = new KpiCard("Low-stock items", "—", "Inventory watch");
    private final KpiCard expiring = new KpiCard("Expiring soon", "—", "Next 30 days");
    private final JLabel status = new JLabel("Loading today's clinic summary…");

    public NurseDashboardPanel(ApplicationContext app) {
        super("Nurse dashboard", Theme.SAMPLE_CAMPUS);
        if (!(app.current() instanceof SchoolNurse)
                || !app.current().canAccess(User.RECORD_TRANSACTION)
                || !app.current().canAccess(User.VIEW_STOCK)) {
            throw new SecurityException("Nurse dashboard access denied.");
        }

        JPanel content = new JPanel(new BorderLayout(0, Theme.SPACE_4));
        content.setOpaque(false);
        JPanel metrics = new JPanel(new GridLayout(1, 4, Theme.SPACE_3, 0));
        metrics.setOpaque(false);
        metrics.add(patients);
        metrics.add(consultations);
        metrics.add(lowStock);
        metrics.add(expiring);
        content.add(metrics, BorderLayout.NORTH);
        content.add(new RecentVisitsPanel(app, () -> load(app)), BorderLayout.CENTER);
        status.setForeground(Theme.MUTED);
        content.add(status, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        action("Refresh", () -> load(app));
        load(app);
    }

    private void load(ApplicationContext app) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        status.setText("Refreshing today's clinic summary…");
        AsyncLoader.load(() -> new Summary(
            app.patientCount(),
            app.recentVisits(0, 1, "", dayStart, now).total(),
            app.clinic().getLowStock().size(),
            app.clinic().getExpiringWithin(30).size()
        ), summary -> {
            patients.setValue(Integer.toString(summary.patients()));
            consultations.setValue(Integer.toString(summary.consultations()));
            lowStock.setValue(Integer.toString(summary.lowStock()));
            expiring.setValue(Integer.toString(summary.expiring()));
            status.setText("Clinic operations for " + LocalDate.now() + " · Signed in as "
                + app.current().getName());
        }, error -> {
            status.setText("Unable to load clinic summary. Use Refresh to retry.");
            if (isShowing()) MessagePopup.error(this, error);
        });
    }

    private record Summary(int patients, int consultations, int lowStock, int expiring) {}
}