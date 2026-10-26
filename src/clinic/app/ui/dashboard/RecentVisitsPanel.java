package clinic.app.ui.dashboard;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.*;
import clinic.app.ui.consultations.NewConsultationForm;
import clinic.model.*;
import clinic.service.PageResult;
import java.awt.BorderLayout;
import java.time.LocalDateTime;
import javax.swing.*;

/** Local visit search is independent of any global toolbar and is evaluated before paging. */
public final class RecentVisitsPanel extends Card {
    private volatile VisitPeriod period = VisitPeriod.THIS_WEEK;

    public RecentVisitsPanel(ApplicationContext app, Runnable saved) {
        super();
        setLayout(new BorderLayout(Theme.GAP, Theme.GAP));
        JPanel header = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        JPanel titleRow = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        JLabel title = new JLabel("Recent Clinic Visits");
        title.setFont(Theme.font(18, java.awt.Font.BOLD));
        titleRow.add(title, BorderLayout.WEST);
        header.add(titleRow, BorderLayout.NORTH);
        add(header, BorderLayout.NORTH);
        if (!app.current().canAccess(User.RECORD_TRANSACTION)) {
            add(new JLabel("Recent visits are available to clinic staff."), BorderLayout.CENTER);
            return;
        }
        PagedTablePanel<RecentVisitRow> table = new PagedTablePanel<>(new RecentVisitsTableModel(),
            (page, size, query) -> {
                VisitPeriod selected = period;
                LocalDateTime now = LocalDateTime.now();
                var clinic = app.clinic();
                PageResult<MedicalRecord> visits = app.recentVisits(page, size, query, selected.start(now), now);
                var rows = visits.rows().stream().map(visit -> new RecentVisitRow(visit,
                    clinic.findPatient(visit.getPatientId()).orElse(null))).toList();
                return new PageResult<>(rows, visits.total());
            }, "Search name or ID number", false);
        table.setEmptyMessage(query -> period.emptyMessage(query));
        table.refreshOn(app.changes,clinic.app.DataTopic.VISITS,clinic.app.DataTopic.PATIENTS);
        add(table, BorderLayout.CENTER);
        JLabel dates = new JLabel(period.description(LocalDateTime.now()));
        dates.setForeground(Theme.MUTED);
        SearchBar search = new SearchBar("Search name or ID number", query -> {
            dates.setText(period.description(LocalDateTime.now()));
            table.applyFilter(query);
        });
        JComboBox<VisitPeriod> periods = new JComboBox<>(VisitPeriod.values());
        periods.getAccessibleContext().setAccessibleName("Visit date scope");
        periods.addActionListener(event -> {
            period = (VisitPeriod) periods.getSelectedItem();
            dates.setText(period.description(LocalDateTime.now()));
            table.applyFilter(search.text());
        });
        JButton refresh = Theme.button("Refresh", false);
        refresh.addActionListener(event -> {
            dates.setText(period.description(LocalDateTime.now()));
            table.reload();
        });
        JPanel filters = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        filters.add(search, BorderLayout.CENTER);
        JPanel scope = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        scope.add(periods, BorderLayout.CENTER);
        scope.add(refresh, BorderLayout.EAST);
        filters.add(scope, BorderLayout.EAST);
        JPanel searchHeader = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        searchHeader.add(filters, BorderLayout.CENTER);
        searchHeader.add(dates, BorderLayout.SOUTH);
        header.add(searchHeader, BorderLayout.CENTER);
        JButton create = Theme.button("+ New Consultation", true);
        create.addActionListener(event -> NewConsultationForm.open(this, app, () -> {
        }));
        titleRow.add(create, BorderLayout.EAST);
    }
}
