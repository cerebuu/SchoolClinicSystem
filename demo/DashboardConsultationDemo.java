import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.dashboard.DashboardPanel;
import clinic.model.Student;
import java.awt.*;
import javax.swing.*;

/** Drives real dashboard/FormPopup/MessagePopup components, without a duplicate form. */
public final class DashboardConsultationDemo {
    private final clinic.service.AuditLog audit = new clinic.service.AuditLog();
    private final ApplicationContext app = new ApplicationContext(true, audit);
    private JFrame frame;
    private DashboardPanel dashboard;
    private Timer timer;
    private int stage;
    private long deadline;

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("A desktop is required for this demo.");
        DashboardConsultationDemo demo = new DashboardConsultationDemo();
        demo.app.login.login("N001", "nurse123").orElseThrow();
        demo.app.clinic().registerPatient(new Student("DEMO-P", "Demo Patient", "demo", "BSIT", 2, "BSIT 2-A"));
        SwingUtilities.invokeLater(demo::open);
    }
    private void open() {
        Theme.install();
        frame = new JFrame("Dashboard consultation end-to-end demo");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        dashboard = new DashboardPanel(app);
        frame.add(dashboard);
        frame.setSize(1200, 750);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        timer = new Timer(100, e -> {
            try { advance(); } catch (Throwable failure) { fail(failure); }
        });
        timer.start();
    }
    private void advance() {
        if (System.nanoTime() > deadline) throw new AssertionError("Demo timed out at stage " + stage);
        JDialog dialog = visibleDialog();
        if (stage == 0 && labels(dashboard).contains("No visits yet. Click + New Consultation to add one")) {
            JButton create = button(dashboard, "+ New Consultation");
            require(create != null && create.isEnabled(), "Nurse button enabled");
            require(create.getBackground().equals(Theme.BRAND) && create.getForeground().equals(Theme.WHITE), "Primary theme styling");
            System.out.println("PASS dashboard: empty state visible; primary + New Consultation button enabled for nurse.");
            stage = 1;
            SwingUtilities.invokeLater(create::doClick);
        } else if (stage == 1 && dialog != null) {
            require(dialog.getTitle().equals("New Consultation"), "Shared form opened");
            button(dialog, "Save").doClick();
            require(labels(dialog).contains("Patient ID is required."), "Patient ID validation");
            field(dialog, "Patient ID").setText("DEMO-P");
            button(dialog, "Save").doClick();
            require(labels(dialog).contains("Symptoms is required."), "Symptoms validation");
            choice(dialog).setSelectedItem("Fever");
            button(dialog, "Save").doClick();
            require(labels(dialog).contains("Concern is required."), "Concern validation");
            System.out.println("PASS validation: required patient ID, symptoms, and reason produce clear messages.");
            stage = 2;
            button(dialog, "Cancel").doClick();
        } else if (stage == 2 && dialog == null) {
            require(app.visitCount() == 0 && table(dashboard).getRowCount() == 0, "Cancel saves nothing");
            System.out.println("PASS cancel: no consultation saved and recent visits remain empty.");
            stage = 3;
            SwingUtilities.invokeLater(() -> button(dashboard, "+ New Consultation").doClick());
        } else if (stage == 3 && dialog != null) {
            field(dialog, "Patient ID").setText("NOT-REGISTERED");
            choice(dialog).setSelectedItem("Fever");
            field(dialog, "Concern").setText("Dashboard consultation demo");
            stage = 4;
            button(dialog, "Save").doClick();
        } else if (stage == 4 && dialog != null && dialog.getTitle().equals("Unable to complete")) {
            require(labels(dialog).contains("Patient ID was not found"), "Patient lookup feedback");
            require(app.visitCount() == 0, "Unknown patient cannot create a visit");
            System.out.println("PASS patient lookup: unknown ID rejected through shared error popup; nothing saved.");
            stage = 5;
            button(dialog, "OK").doClick();
        } else if (stage == 5 && dialog != null && dialog.getTitle().equals("New Consultation")) {
            field(dialog, "Patient ID").setText("DEMO-P");
            stage = 6;
            button(dialog, "Save").doClick();
        } else if (stage == 6 && dialog != null && dialog.getTitle().equals("School clinic")) {
            stage = 7;
            button(dialog, "OK").doClick();
        } else if (stage == 7 && dialog == null && table(dashboard).getRowCount() == 1) {
            require(app.visitCount() == 1, "One consultation persisted");
            require(table(dashboard).getValueAt(0, 4).equals("Dashboard consultation demo"), "Table refreshed with saved visit");
            require(table(dashboard).getValueAt(0, 5).toString().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"), "Formatted date");
            require(audit.getAll().stream().anyMatch(e -> e.getAction().equals("recordVisit") && e.isAllowed()), "Secured recordVisit audited");
            System.out.println("PASS save: secured visit audited; recent table refreshed to one row with formatted date.");
            app.login.logout();
            for (String[] account : new String[][]{{"D001", "doctor123"}, {"AD001", "admin123"}}) {
                app.login.login(account[0], account[1]).orElseThrow();
                DashboardPanel restricted = new DashboardPanel(app);
                require(button(restricted, "+ New Consultation") == null, "Restricted role has no add button");
                app.login.logout();
            }
            System.out.println("PASS role UI: doctor and administrator cannot see the consultation button.");
            timer.stop();
            frame.dispose();
        }
    }
    private static JDialog visibleDialog() {
        JDialog visible = null;
        for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing()) visible = d;
        return visible;
    }
    private static JButton button(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && b.getText().equals(text)) return b;
            if (c instanceof Container child) { JButton found = button(child, text); if (found != null) return found; }
        }
        return null;
    }
    private static JTextField field(Container root, String label) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField f && label.equals(f.getAccessibleContext().getAccessibleName())) return f;
            if (c instanceof Container child) { JTextField found = field(child, label); if (found != null) return found; }
        }
        return null;
    }
    private static JComboBox<?> choice(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JComboBox<?> box) return box;
            if (c instanceof Container child) { JComboBox<?> found = choice(child); if (found != null) return found; }
        }
        return null;
    }
    private static JTable table(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTable t) return t;
            if (c instanceof Container child) { JTable found = table(child); if (found != null) return found; }
        }
        return null;
    }
    private static String labels(Container root) {
        StringBuilder text = new StringBuilder();
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel label) text.append(label.getText());
            if (c instanceof Container child) text.append(labels(child));
        }
        return text.toString();
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private void fail(Throwable failure) {
        timer.stop(); failure.printStackTrace();
        for (Window window : Window.getWindows()) window.dispose();
        System.exit(1);
    }
}
