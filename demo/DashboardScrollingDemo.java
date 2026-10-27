import clinic.app.ApplicationContext;
import clinic.app.DataTopic;
import clinic.app.Theme;
import clinic.app.ui.common.PagedTablePanel;
import clinic.app.ui.dashboard.NurseDashboardPanel;
import clinic.model.Student;
import java.awt.GraphicsEnvironment;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Visible integration check for dashboard scrolling and the existing table controls. */
public final class DashboardScrollingDemo {
    private final ApplicationContext app = new ApplicationContext(true);
    private final JFrame frame = new JFrame("Dashboard scrolling verification");
    private final NurseDashboardPanel dashboard;
    private final Timer timer;
    private PagedTablePanel<?> visits;
    private PagedTablePanel<?> medicines;
    private JScrollPane page;
    private int stage;
    private long deadline;
    private int refreshBaselineVisits;
    private int refreshBaselineMedicines;

    private DashboardScrollingDemo() {
        app.login.login("N001", "nurse123").orElseThrow();
        app.clinic().registerPatient(new Student(
            "SCROLL-P", "Dashboard Scroll Fixture", "demo", "BSIT", 2, "BSIT 2-A"));
        app.clinic().registerPatient(new Student(
            "SCROLL-Q", "Dashboard Search Fixture", "demo", "BSCS", 1, "BSCS 1-A"));
        for (int i = 0; i < 40; i++) {
            String patientId = i == 0 ? "SCROLL-P" : "SCROLL-Q";
            app.clinic().recordVisit(patientId, List.of("Fever"), "Visit " + i, "", "N001");
            app.clinic().addMedicine("Scroll medicine " + i, 100 + i,
                LocalDate.now().plusMonths(6), "");
        }
        int filteredVisits = app.recentVisits(0, 25, "SCROLL-P",
            LocalDate.now().atStartOfDay(), LocalDateTime.now()).total();
        if (filteredVisits != 1) throw new AssertionError("Fixture should have one SCROLL-P visit.");
        dashboard = new NurseDashboardPanel(app);
        timer = new Timer(150, event -> advance());
        timer.setRepeats(true);
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A desktop is required for this demo.");
        }
        SwingUtilities.invokeLater(() -> new DashboardScrollingDemo().open());
    }

    private void open() {
        Theme.install();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(dashboard);
        frame.setSize(1000, 700);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        try {
            visits = DashboardScrollingDemoSupport.findTable(dashboard, "RecentVisitsTableModel");
            medicines = DashboardScrollingDemoSupport.findTable(dashboard, "PredictionsTableModel");
            page = DashboardScrollingDemoSupport.findPage(dashboard);
        } catch (RuntimeException | AssertionError failure) {
            frame.dispose();
            app.close();
            throw failure;
        }
        deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        timer.start();
    }

    private void advance() {
        try {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Demo timed out at stage " + stage + ", total="
                    + visits.totalCount() + ", filter=" + visits.filterText());
            }
            if (stage == 0 && visits.totalCount() == 40 && medicines.totalCount() == 40) {
                verifySize(1000, 700);
                DashboardScrollingDemoSupport.verifyPageWheel(page);
                DashboardScrollingDemoSupport.verifyTableBoundaryWheel(dashboard, page);
                verifySize(1366, 768);
                verifySize(800, 500);
                DashboardScrollingDemoSupport.verifyMinimums(dashboard);
                System.out.println("PASS resize: both cards retain minimum heights; page scrollbar remains visible.");
                stage = 1;
            } else if (stage == 1) {
                DashboardScrollingDemoSupport.verifySearchAndPeriod(dashboard);
                stage = 2;
            } else if (stage == 2 && visits.totalCount() == 1) {
                System.out.println("PASS search: filtering 'SCROLL-P' returns 1 matching visit.");
                DashboardScrollingDemoSupport.verifyNoInnerScrollbar(dashboard);
                DashboardScrollingDemoSupport.searchField(dashboard).setText("");
                stage = 3;
            } else if (stage == 3 && visits.totalCount() == 40) {
                DashboardScrollingDemoSupport.button(dashboard, "Next").doClick();
                stage = 4;
            } else if (stage == 4 && visits.currentPage() == 1
                    && DashboardScrollingDemoSupport.button(dashboard, "Prev").isEnabled()) {
                System.out.println("PASS paging: Next reaches page 2 of 2; 25-row page size is unchanged.");
                DashboardScrollingDemoSupport.button(dashboard, "Prev").doClick();
                stage = 5;
            } else if (stage == 5 && visits.currentPage() == 0
                    && DashboardScrollingDemoSupport.button(dashboard, "Next").isEnabled()) {
                stage = 6;
                SwingUtilities.invokeLater(() ->
                    DashboardScrollingDemoSupport.button(dashboard, "+ New Consultation").doClick());
            } else if (stage == 6) {
                JDialog dialog = DashboardScrollingDemoSupport.visibleDialog();
                if (dialog != null && DashboardScrollingDemoSupport.button(dialog, "Cancel") != null) {
                    DashboardScrollingDemoSupport.button(dialog, "Cancel").doClick();
                    System.out.println("PASS consultation: + New Consultation opens the existing form.");
                    stage = 7;
                }
            } else if (stage == 7) {
                refreshBaselineVisits = visits.refreshCount();
                refreshBaselineMedicines = medicines.refreshCount();
                app.changes.publish(DataTopic.VISITS, DataTopic.INVENTORY);
                stage = 8;
                deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            } else if (stage == 8
                    && visits.refreshCount() > refreshBaselineVisits
                    && medicines.refreshCount() > refreshBaselineMedicines) {
                DashboardScrollingDemoSupport.verifyFooterReachable(dashboard, page);
                System.out.println("PASS automatic refresh: visit and medicine tables respond to data changes.");
                System.out.println("PASS horizontal scrolling: dashboard policy is NEVER at all tested sizes.");
                timer.stop();
                frame.dispose();
                app.close();
            }
        } catch (RuntimeException | AssertionError failure) {
            timer.stop();
            failure.printStackTrace();
            frame.dispose();
            app.close();
            System.exit(1);
        }
    }

    private void verifySize(int width, int height) {
        frame.setSize(width, height);
        frame.validate();
        DashboardScrollingDemoSupport.layoutTree(frame);
        int visitRows = DashboardScrollingDemoSupport.visibleRows(visits);
        int medicineRows = DashboardScrollingDemoSupport.visibleRows(medicines);
        DashboardScrollingDemoSupport.require(visitRows >= 9 && visitRows <= 11,
            "Recent visits shows about 10 rows");
        DashboardScrollingDemoSupport.require(medicineRows >= 9 && medicineRows <= 11,
            "Medicine predictions shows about 10 rows");
        DashboardScrollingDemoSupport.require(page.getVerticalScrollBar().isVisible(),
            "Page scrollbar is visible");
        DashboardScrollingDemoSupport.require(
            page.getHorizontalScrollBarPolicy() == JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
            "Page horizontal scrollbar is disabled");
        DashboardScrollingDemoSupport.require(!page.getHorizontalScrollBar().isVisible(),
            "Page horizontal scrollbar is not visible");
        System.out.printf(
            "SIZE %dx%d: visits viewport=%d px rows=%d; medicines viewport=%d px rows=%d; page scrollbar=%s; horizontal=%s%n",
            width, height, DashboardScrollingDemoSupport.viewportHeight(visits), visitRows,
            DashboardScrollingDemoSupport.viewportHeight(medicines), medicineRows,
            page.getVerticalScrollBar().isVisible(), page.getHorizontalScrollBar().isVisible());
    }

}
