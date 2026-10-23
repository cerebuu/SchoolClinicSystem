package clinic.app.ui.dashboard;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.model.User;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.swing.*;

public final class DashboardPanel extends BasePanel {
    public DashboardPanel(ApplicationContext app) {
        super("Clinic dashboard", Theme.SAMPLE_CAMPUS);
        boolean canRecord = app.current().canAccess(User.RECORD_TRANSACTION);
        KpiCard patients = new KpiCard("Registered patients:", "—", "Loading clinic totals");
        KpiCard visits = new KpiCard("Consultations today", "—", "Today");
        KpiCard lowStock = new KpiCard("Low stock", "—", "Inventory watch");
        KpiCard expiring = new KpiCard("Expiring soon", "—", "Within 30 days");
        JPanel metrics = new JPanel(new GridLayout(1, 4, Theme.SPACE_3, 0));
        metrics.setOpaque(false);
        JLabel loading = new JLabel("Loading clinic summary…");
        loading.setForeground(Theme.MUTED);
        metrics.add(loading);
        JPanel content = new JPanel(new BorderLayout(Theme.SPACE_4, Theme.SPACE_4));
        content.setOpaque(false);
        content.add(metrics, BorderLayout.NORTH);
        if (canRecord) content.add(new RecentVisitsPanel(app, () -> load(app, patients, visits, lowStock, expiring, metrics, loading)), BorderLayout.CENTER);
        else content.add(new EmptyState("Clinic summary unavailable",
            "This account does not have permission to view clinic totals."), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        if (canRecord) {
            action("Refresh", () -> load(app, patients, visits, lowStock, expiring, metrics, loading));
            load(app, patients, visits, lowStock, expiring, metrics, loading);
        }
    }

    private void load(ApplicationContext app, KpiCard patients, KpiCard visits,
                      KpiCard lowStock, KpiCard expiring, JPanel metrics, JLabel loading) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        AsyncLoader.load(() -> {
            int patientTotal = app.patientCount();
            int todaysVisits = app.recentVisits(0, 1, "", dayStart, now).total();
            int lowStockTotal = app.current().canAccess(User.VIEW_STOCK)
                ? app.clinic().getLowStock().size() : -1;
            int expiringTotal = app.current().canAccess(User.VIEW_STOCK)
                ? app.clinic().getExpiringWithin(30).size() : -1;
            return new int[] {patientTotal, todaysVisits, lowStockTotal, expiringTotal};
        }, values -> {
            patients.setValue(Integer.toString(values[0]));
            patients.setCaption("All registered records");
            visits.setValue(Integer.toString(values[1]));
            visits.setCaption("Today");
            lowStock.setValue(values[2] < 0 ? "—" : Integer.toString(values[2]));
            lowStock.setCaption(values[2] < 0 ? "Not available to this role" : "Needs attention");
            expiring.setValue(values[3] < 0 ? "—" : Integer.toString(values[3]));
            expiring.setCaption(values[3] < 0 ? "Not available to this role" : "Within 30 days");
            metrics.remove(loading);
            if (patients.getParent() != metrics) {
                metrics.add(patients);
                metrics.add(visits);
                metrics.add(lowStock);
                metrics.add(expiring);
            }
            metrics.revalidate();
            metrics.repaint();
        }, error -> {
            patients.setValue("—");
            visits.setValue("—");
            lowStock.setValue("—");
            expiring.setValue("—");
            patients.setCaption("Unable to load · use Refresh to retry");
            loading.setText("Unable to load clinic summary · use Refresh to retry");
            if (isShowing()) MessagePopup.error(this, error);
        });
    }
}
