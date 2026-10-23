package clinic.app.ui.admin;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.*;

/** System totals and a searchable campus catalog. */
public final class AdminDashboardPanel extends BasePanel {
    private final KpiCard users = new KpiCard("User accounts:", "—", "System-wide total");
    private final KpiCard campuses = new KpiCard("Campuses", "—", "Catalog total");
    private final KpiCard auditToday = new KpiCard("Audit events today", "—", "Today");
    private final KpiCard denied = new KpiCard("Denied attempts", "—", "Recorded history");
    private final JLabel status = new JLabel("Loading administrator summary...");
    private int generation;

    public AdminDashboardPanel(ApplicationContext app) {
        super("Superadmin dashboard");
        JPanel content = new JPanel(new BorderLayout(Theme.GAP, Theme.SPACE_4));
        content.setOpaque(false);
        JPanel metrics = new JPanel(new GridLayout(1, 4, Theme.SPACE_3, 0));
        metrics.setOpaque(false);
        metrics.add(users);
        metrics.add(campuses);
        metrics.add(auditToday);
        metrics.add(denied);
        content.add(metrics, BorderLayout.NORTH);
        PagedTablePanel<clinic.model.Campus> catalog = new PagedTablePanel<>(
            new CampusesTableModel(), app.admin::campuses, "Campus ID / name");
        catalog.setEmptyText("No campuses registered");
        content.add(catalog, BorderLayout.CENTER);
        status.setForeground(Theme.MUTED);
        content.add(status, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        onRefresh(() -> load(app));
        action("Refresh", this::refreshPage);
        load(app);
    }

    private void load(ApplicationContext app) {
        int request = ++generation;
        status.setText("Refreshing system summary...");
        AsyncLoader.load(app.admin::summary, summary -> {
            if (request != generation) return;
            users.setValue(Integer.toString(summary.users()));
            campuses.setValue(Integer.toString(summary.campuses()));
            auditToday.setValue(Long.toString(summary.eventsToday()));
            denied.setValue(Long.toString(summary.denied()));
            status.setText("System-wide totals. Manage accounts in Users and campus entries in Campuses.");
        }, error -> {
            if (request != generation) return;
            users.setValue("—");
            campuses.setValue("—");
            auditToday.setValue("—");
            denied.setValue("—");
            status.setText("Unable to load summary. Use Refresh to retry.");
        });
    }
}
