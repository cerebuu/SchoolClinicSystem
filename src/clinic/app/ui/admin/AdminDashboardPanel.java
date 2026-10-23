package clinic.app.ui.admin;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.BasePanel;
import clinic.app.ui.common.Card;
import clinic.app.ui.common.EmptyState;
import clinic.app.ui.common.KpiCard;
import clinic.app.ui.common.PageHeader;
import clinic.model.Campus;
import clinic.service.AuditEntry;
import clinic.service.PageResult;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class AdminDashboardPanel extends BasePanel {
    private final JComboBox<CampusOption> campusSelector = new JComboBox<>();
    private final JLabel scopeNote = new JLabel("System-wide totals are shown; campus selection is visual only.");
    private final JPanel comparison = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_3, 0));
    private final KpiCard users = new KpiCard("User accounts:", "—", "Current system total");
    private final KpiCard campuses = new KpiCard("Active campuses:", "—", "From the campus catalog");
    private final KpiCard auditToday = new KpiCard("Audit events today", "—", "Current session");
    private final KpiCard denied = new KpiCard("Denied attempts", "—", "Current session");
    private final JPanel metrics = new JPanel(new GridLayout(1, 4, Theme.SPACE_3, 0));
    private final JLabel loading = new JLabel("Loading administrator summary…");
    private boolean metricsInstalled;

    public AdminDashboardPanel(ApplicationContext app) {
        super("Superadmin dashboard");
        JPanel content = new JPanel(new BorderLayout(0, Theme.SPACE_6));
        content.setOpaque(false);

        JPanel filter = new JPanel(new BorderLayout(Theme.SPACE_3, Theme.SPACE_2));
        filter.setOpaque(false);
        JLabel campusLabel = new JLabel("Campus");
        campusLabel.setLabelFor(campusSelector);
        campusSelector.getAccessibleContext().setAccessibleName("Campus selector");
        Theme.field(campusSelector);
        campusSelector.setBackground(Theme.WHITE);
        campusSelector.addItem(new CampusOption("All campuses", null));
        filter.add(campusLabel, BorderLayout.WEST);
        filter.add(campusSelector, BorderLayout.CENTER);
        scopeNote.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.PLAIN));
        scopeNote.setForeground(Theme.MUTED);
        filter.add(scopeNote, BorderLayout.SOUTH);
        content.add(filter, BorderLayout.NORTH);

        metrics.setOpaque(false);
        loading.setForeground(Theme.MUTED);
        metrics.add(loading);

        Card overview = new Card();
        overview.setLayout(new BorderLayout(Theme.SPACE_3, Theme.SPACE_3));
        overview.setPreferredSize(new Dimension(900, 225));
        overview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 225));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);
        overview.add(new PageHeader("Campus overview",
            "Campus-level values are sample placeholders until records are scoped by campus.", null),
            BorderLayout.NORTH);
        comparison.setOpaque(false);
        overview.add(comparison, BorderLayout.CENTER);

        JPanel dashboard = new JPanel();
        dashboard.setOpaque(false);
        dashboard.setLayout(new javax.swing.BoxLayout(dashboard, javax.swing.BoxLayout.Y_AXIS));
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 116));
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        dashboard.add(metrics);
        dashboard.add(javax.swing.Box.createVerticalStrut(Theme.SPACE_4));
        dashboard.add(overview);
        content.add(dashboard, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        campusSelector.addActionListener(event -> updateScopeNote());
        action("Refresh", () -> load(app));
        load(app);
    }

    private void load(ApplicationContext app) {
        scopeNote.setText("Loading campus catalog and system-wide totals…");
        clinic.app.ui.common.AsyncLoader.load(() -> {
            int userCount = app.admin.userCount();
            PageResult<Campus> catalog = app.admin.campuses(0, 500, "");
            List<AuditEntry> events = app.audit.getAll();
            long todayCount = events.stream()
                .filter(entry -> entry.getTimestamp().toLocalDate().equals(LocalDate.now()))
                .count();
            long deniedCount = events.stream().filter(entry -> !entry.isAllowed()).count();
            return new Summary(userCount, catalog.total(), (int) todayCount,
                (int) deniedCount, catalog.rows());
        }, summary -> {
            users.setValue(Integer.toString(summary.users()));
            users.setCaption("System-wide total");
            campuses.setValue(Integer.toString(summary.campusCount()));
            campuses.setCaption("Catalog total");
            auditToday.setValue(Integer.toString(summary.auditToday()));
            denied.setValue(Integer.toString(summary.denied()));
            metrics.remove(loading);
            if (!metricsInstalled) {
                metrics.add(users);
                metrics.add(campuses);
                metrics.add(auditToday);
                metrics.add(denied);
                metricsInstalled = true;
            }
            metrics.revalidate();
            metrics.repaint();
            installCampuses(summary.catalog());
            updateScopeNote();
        }, error -> {
            users.setValue("—");
            campuses.setValue("—");
            auditToday.setValue("—");
            denied.setValue("—");
            loading.setText("Unable to load dashboard data. Use Refresh to retry.");
            scopeNote.setText("Dashboard data could not be loaded.");
            if (isShowing()) clinic.app.ui.common.popup.MessagePopup.error(this, error);
        });
    }

    private void installCampuses(List<Campus> catalog) {
        campusSelector.removeAllItems();
        campusSelector.addItem(new CampusOption("All campuses", null));
        for (Campus campus : catalog) {
            campusSelector.addItem(new CampusOption(campus.name(), campus));
        }
        comparison.removeAll();
        if (catalog.isEmpty()) {
            comparison.setLayout(new FlowLayout(FlowLayout.CENTER, Theme.SPACE_3, Theme.SPACE_2));
            comparison.add(new EmptyState("No campuses in the catalog",
                "Add campuses from Campus management to compare them here."));
        } else {
            comparison.setLayout(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_3, 0));
            for (Campus campus : catalog) {
                KpiCard sample = new KpiCard(campus.name(), "—",
                    "Sample · no campus totals");
                sample.setPreferredSize(new Dimension(286, 128));
                comparison.add(sample);
            }
        }
        comparison.revalidate();
        comparison.repaint();
    }

    private void updateScopeNote() {
        CampusOption selected = (CampusOption) campusSelector.getSelectedItem();
        String scope = selected == null || selected.campus() == null
            ? "All campuses selected" : selected.label() + " selected";
        scopeNote.setText(scope + " · system-wide totals remain unchanged (visual-only selector).");
    }

    private record CampusOption(String label, Campus campus) {
        @Override public String toString() { return label; }
    }

    private record Summary(int users, int campusCount, int auditToday, int denied,
                           List<Campus> catalog) {}
}
