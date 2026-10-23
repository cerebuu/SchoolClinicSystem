import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.*;
import clinic.app.ui.dashboard.*;
import clinic.model.*;
import clinic.service.*;
import clinic.util.DateUtil;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;

/** Real bounded query + shared controls, with historic fixtures and 10,000 stored dummy visits. */
public final class VisitSearchDemo {
    private final LocalDateTime now = LocalDateTime.now();
    private final Map<String, Patient> patients = new HashMap<>();
    private final List<MedicalRecord> records = new ArrayList<>();
    private final AtomicReference<VisitPeriod> period = new AtomicReference<>(VisitPeriod.THIS_WEEK);
    private final AtomicBoolean background = new AtomicBoolean(true);
    private JFrame frame;
    private SearchBar search;
    private PagedTablePanel<RecentVisitRow> table;
    private final RecentVisitsTableModel model = new RecentVisitsTableModel();
    private int stage;
    private long deadline;
    private javax.swing.Timer timer;

    public static void main(String[] args) throws Exception {
        VisitSearchDemo demo = new VisitSearchDemo();
        demo.seed(); demo.verifyAndPrint();
        if (Arrays.asList(args).contains("--show")) SwingUtilities.invokeAndWait(demo::open);
    }
    private void seed() {
        patients.put("2022-001", new Student("2022-001", "Ana Cruz", "demo", "BSIT", 2, "BSIT 2-A"));
        patients.put("2022-002", new Student("2022-002", "Juan Mendoza", "demo", "BSCS", 2, "BSCS 2-B"));
        patients.put("2020-003", new Employee("2020-003", "Pedro Cruz", "demo", "Administration", "Registrar Staff"));
        records.add(new DatedDemoVisit("OLD-CRUZ", "2020-003", now.minusMonths(1), "Last month"));
        records.add(new DatedDemoVisit("WEEK-CRUZ", "2022-001", VisitPeriod.THIS_WEEK.start(now), "Monday visit"));
        records.add(new DatedDemoVisit("TODAY-2022", "2022-002", now, "Today visit"));
        records.add(new DatedDemoVisit("FOLLOWUP-CRUZ", "2022-001", now, "Follow-up visit"));
        for (int i = 0; i < 10000; i++) {
            String id = "D-" + i;
            patients.put(id, new Student(id, "Dummy patient " + i, "demo", "BSIT", 1, "BSIT 1-A"));
            records.add(new DatedDemoVisit("DUMMY-" + i, id, now.minusMonths(1).plusSeconds(i), "Dummy visit"));
        }
        records.sort(Comparator.comparing(MedicalRecord::getVisitDateTime));
    }
    private PageResult<MedicalRecord> query(int page, int size, String text) {
        return VisitSearch.page(records, patients, page, size, text, null, period.get().start(now), now);
    }
    private void print(String text, VisitPeriod scope, int count) {
        period.set(scope);
        PageResult<MedicalRecord> result = query(0, 25, text);
        require(result.total() == count, "Expected result count for " + text + " / " + scope);
        System.out.println("SEARCH '" + text + "' / " + scope + ": " + result.total() + " visits");
        for (MedicalRecord visit : result.rows()) System.out.println("  " + visit.getPatientId() + " | "
            + patients.get(visit.getPatientId()).getName() + " | " + DateUtil.format(visit.getVisitDateTime()));
    }
    private void verifyAndPrint() {
        System.out.println(VisitPeriod.THIS_WEEK.description(now));
        print("Cruz", VisitPeriod.THIS_WEEK, 2);
        print("2022", VisitPeriod.THIS_WEEK, 3);
        print("xyz", VisitPeriod.THIS_WEEK, 0);
        print("Cruz", VisitPeriod.ALL, 3);
        require(query(0, 25, "").rows().size() == 25 && query(400, 25, "").rows().size() == 4, "Bounded pages");
        period.set(VisitPeriod.TODAY);
        require(query(0, 25, "").total() >= 2, "Today scope");
        period.set(VisitPeriod.THIS_WEEK);
        require(query(0, 25, "cRuZ").total() == 2, "Case-insensitive partial names");
        LocalDateTime monday = LocalDateTime.of(2026, 10, 19, 0, 0);
        require(VisitPeriod.THIS_WEEK.start(LocalDateTime.of(2026, 10, 23, 12, 0)).equals(monday), "Monday midnight");
        List<MedicalRecord> bounds = List.of(new DatedDemoVisit("BEFORE", "2022-001", monday.minusNanos(1), "Before"),
            new DatedDemoVisit("START", "2022-001", monday, "Start"),
            new DatedDemoVisit("END", "2022-001", now, "End"),
            new DatedDemoVisit("FUTURE", "2022-001", now.plusNanos(1), "Future"));
        require(VisitSearch.page(bounds, patients, 0, 25, "Cruz", null, monday, now).total() == 2, "Inclusive start/end; future excluded");
        clinic.service.AuditLog audit = new clinic.service.AuditLog();
        ApplicationContext app = new ApplicationContext(true, audit);
        app.login.login("N001", "nurse123");
        app.recentVisits(0, 25, "PrivateSearchCruz", monday, now);
        require(audit.getAll().stream().noneMatch(e -> e.getDetails().contains("PrivateSearchCruz")), "Search privacy");
        require(audit.getAll().stream().anyMatch(e -> e.getDetails().equals("visit search performed")), "Generic search audit");
        System.out.println("PASS: 10,004 stored visits; pages retain at most 25 rows; name/ID/date filters precede paging.");
        System.out.println("PASS: Monday boundary, Today/All scopes, no results, future exclusion, and search audit privacy.");
    }
    private void open() {
        Theme.install();
        table = new PagedTablePanel<>(model, (page, size, text) -> {
            if (SwingUtilities.isEventDispatchThread()) background.set(false);
            PageResult<MedicalRecord> visits = query(page, size, text);
            return new PageResult<>(visits.rows().stream().map(v -> new RecentVisitRow(v, patients.get(v.getPatientId()))).toList(), visits.total());
        }, "Search name or ID number", false);
        table.setEmptyMessage(text -> period.get().emptyMessage(text));
        search = new SearchBar("Search name or ID number", table::applyFilter);
        JComboBox<VisitPeriod> scope = new JComboBox<>(VisitPeriod.values());
        scope.addActionListener(e -> { period.set((VisitPeriod) scope.getSelectedItem()); table.applyFilter(search.text()); });
        JPanel filters = new JPanel(new BorderLayout()); filters.add(search); filters.add(scope, BorderLayout.EAST);
        JPanel card = new JPanel(new BorderLayout()); card.setBorder(Theme.padding()); card.add(filters, BorderLayout.NORTH); card.add(table);
        frame = new JFrame("Recent visits local search - 10,000 dummy visits");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); frame.add(card); frame.setSize(1200, 750); frame.setLocationRelativeTo(null); frame.setVisible(true);
        deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        timer = new javax.swing.Timer(100, e -> {
            try {
                if (System.nanoTime() > deadline) throw new AssertionError("Demo timeout at " + stage);
                if (stage == 0 && table.totalCount() == 3) { search.setText("C"); search.setText("Cr"); search.setText("cRuZ"); stage++; }
                else if (stage == 1 && table.totalCount() == 2) { search.setText("2022"); stage++; }
                else if (stage == 2 && table.totalCount() == 3) { search.setText("xyz"); stage++; }
                else if (stage == 3 && table.totalCount() == 0) { require(labels(card).contains("Switch to All"), "No-result hint"); scope.setSelectedItem(VisitPeriod.ALL); search.setText("Cruz"); stage++; }
                else if (stage == 4 && table.totalCount() == 3) { button(search, "x").doClick(); stage++; }
                else if (stage == 5 && table.totalCount() == 10004) { table.setPage(1); stage++; }
                else if (stage == 6 && labels(card).contains("Page 2 of")) { search.setText("Dummy"); stage++; }
                else if (stage == 7 && table.totalCount() == 10000 && labels(card).contains("Page 1 of")) {
                    require(background.get() && model.getRowCount() == 25, "Background bounded rendering");
                    System.out.println("PASS desktop: debounced name/ID search, no-result hint, All toggle, clear x, page reset, and 25-row background rendering.");
                    timer.stop(); frame.dispose();
                }
            } catch (Throwable failure) { timer.stop(); failure.printStackTrace(); frame.dispose(); System.exit(1); }
        }); timer.start();
    }
    private static String labels(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component c : root.getComponents()) { if (c instanceof JLabel l) result.append(l.getText()); if (c instanceof Container child) result.append(labels(child)); }
        return result.toString();
    }
    private static JButton button(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) return b;
            if (c instanceof Container child) { JButton found = button(child, text); if (found != null) return found; }
        } return null;
    }
    private static void require(boolean ok, String description) { if (!ok) throw new AssertionError(description); }
}
