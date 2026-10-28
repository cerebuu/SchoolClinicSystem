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
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.swing.JLabel;

public final class NurseDashboardPanel extends BasePanel {
    private int generation;
    private final KpiCard patients;
    private final KpiCard consultations = new KpiCard("Today's consultations", "—", "Today");
    private final KpiCard lowStock = new KpiCard("Low-stock items", "—", "Shared inventory");
    private final KpiCard expiring = new KpiCard("Expiring soon", "—", "Shared inventory");
    private final JLabel status = new JLabel("Loading today's clinic summary…");

    public NurseDashboardPanel(ApplicationContext app) {
        super("Nurse dashboard", app);
        patients=new KpiCard("Registered patients","—","Shared patient registry");
        if (!(app.current() instanceof SchoolNurse)
                || !app.current().canAccess(User.RECORD_TRANSACTION)
                || !app.current().canAccess(User.VIEW_STOCK)) {
            throw new SecurityException("Nurse dashboard access denied.");
        }

        DashboardContentPanel content = new DashboardContentPanel();
        ResponsiveKpiGrid metrics = new ResponsiveKpiGrid(
            patients, consultations, lowStock, expiring);
        content.addSection(metrics);
        content.addSection(new RecentVisitsPanel(app, () -> {}));
        content.addSection(new MedicinePredictionsPanel(app));
        status.setForeground(Theme.MUTED);
        content.addSection(status);
        add(DashboardLayout.scrollPage(content), BorderLayout.CENTER);
        action("Refresh", this::refreshPage);
        onRefresh(() -> load(app));
        refreshOn(app.changes,clinic.app.DataTopic.PATIENTS,clinic.app.DataTopic.VISITS,clinic.app.DataTopic.INVENTORY);
    }

    private void load(ApplicationContext app) {
        int request = ++generation;
        String staffName = app.current().getName();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        status.setText("Refreshing today's clinic summary…");
        AsyncLoader.load(() -> new Summary(
            app.patientCount(),
            app.recentVisits(0, 1, "", dayStart, now).total(),
            app.clinic().getLowStock().size(),
            app.clinic().getExpiringWithin(30).size()
        ), summary -> {
            if (request != generation || !refreshActive()) return;
            patients.setValue(Integer.toString(summary.patients()));
            consultations.setValue(Integer.toString(summary.consultations()));
            lowStock.setValue(Integer.toString(summary.lowStock()));
            expiring.setValue(Integer.toString(summary.expiring()));
            status.setText("Clinic operations for " + LocalDate.now() + " · Signed in as "
                + staffName);
        }, error -> {
            if (request != generation || !refreshActive()) return;
            status.setText("Unable to load clinic summary. Use Refresh to retry.");
            patients.setValue("Unavailable");
            consultations.setValue("Unavailable");
            lowStock.setValue("Unavailable");
            expiring.setValue("Unavailable");
            if (isShowing()) MessagePopup.error(this, error);
        });
    }

    private record Summary(int patients, int consultations, int lowStock, int expiring) {}
}
