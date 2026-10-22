import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.dispense.DispensePanel;
import clinic.model.Student;
import clinic.service.AuditEntry;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import javax.swing.*;

/** Runs the real dispense panel with modal dialogs; automatically cancels, overrides, then dispenses safely. */
public final class SafetyPopupDemo {
    private final ApplicationContext app = new ApplicationContext(true);
    private JFrame frame;
    private DispensePanel panel;
    private Timer timer;
    private int stage;
    private int auditStart;
    private int warningsShown;
    private long deadline;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("This demo requires a desktop: java -cp out-demo SafetyPopupDemo");
        }
        SafetyPopupDemo demo = new SafetyPopupDemo();
        demo.seed();
        SwingUtilities.invokeLater(demo::open);
    }

    private void seed() {
        app.login.login("N001", "nurse123").orElseThrow();
        var clinic = app.clinic();
        clinic.registerPatient(new Student("DEMO-CONFLICT", "Confidential Demo Patient", "demo", "CS", 1, "A"));
        clinic.registerPatient(new Student("DEMO-SAFE", "Confidential Safe Patient", "demo", "CS", 1, "B"));
        clinic.addAllergy("DEMO-CONFLICT", "Penicillin");
        clinic.addMedicine("Amoxicillin", 20, LocalDate.now().plusDays(60), "Demo only");
        auditStart = app.audit.getAll().size();
    }

    private void open() {
        Theme.install();
        frame = new JFrame("Safety popup end-to-end demo");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        panel = new DispensePanel(app);
        frame.add(panel);
        frame.setSize(1000, 650);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        timer = new Timer(100, event -> {
            try { advance(); }
            catch (Throwable error) { fail(error); }
        });
        timer.start();
        fillAndSubmit("DEMO-CONFLICT");
    }

    private void advance() {
        if (System.nanoTime() > deadline) throw new AssertionError("Demo timed out at stage " + stage);
        JDialog dialog = visibleDialog();
        if (stage == 0 && dialog != null) {
            require(dialog.getTitle().equals("Safety warning"), "Conflict must open safety popup");
            require(dialog.isModal(), "Safety popup must be modal");
            JButton cancel = button(dialog, "Cancel");
            require(dialog.getRootPane().getDefaultButton() == cancel, "Cancel must be the default");
            JButton override = button(dialog, "Override with reason");
            require(!override.isEnabled(), "Override starts disabled");
            JTextField reason = namedField(dialog, "Override reason");
            reason.setText("short");
            require(!override.isEnabled(), "Short reason must not enable override");
            warningsShown++;
            stage = 1;
            cancel.doClick();
        } else if (stage == 1 && dialog == null && submit().isEnabled()) {
            require(app.clinic().getStock("Amoxicillin") == 20, "Cancel must not change stock");
            require(overrideCount() == 0, "Cancel must not log override");
            System.out.println("PASS cancel: popup appeared; short reason rejected; stock remains 20; no override.");
            stage = 2;
            fillAndSubmit("DEMO-CONFLICT");
        } else if (stage == 2 && dialog != null) {
            require(dialog.getTitle().equals("Safety warning"), "Retry must open safety popup");
            warningsShown++;
            namedField(dialog, "Override reason").setText("Clinician reviewed the recorded warning.");
            JButton override = button(dialog, "Override with reason");
            require(override.isEnabled(), "Valid reason must enable override");
            stage = 3;
            override.doClick();
        } else if (stage == 3 && dialog != null) {
            require(dialog.getTitle().equals("School clinic"), "Override should show shared success popup");
            require(app.clinic().getStock("Amoxicillin") == 18, "Override must dispense two units");
            require(overrideCount() == 1, "Secured service must log one override");
            System.out.println("PASS override: valid reason accepted; stock is 18; secured override entry written.");
            stage = 4;
            button(dialog, "OK").doClick();
        } else if (stage == 4 && dialog == null && submit().isEnabled()) {
            stage = 5;
            fillAndSubmit("DEMO-SAFE");
        } else if (stage == 5 && dialog != null) {
            require(!dialog.getTitle().equals("Safety warning"), "Safe patient must not open warning popup");
            require(dialog.getTitle().equals("School clinic"), "Safe dispense must show shared success popup");
            require(app.clinic().getStock("Amoxicillin") == 16, "Safe dispense must release two units");
            require(overrideCount() == 1 && warningsShown == 2, "Safe dispense must not override");
            stage = 6;
            button(dialog, "OK").doClick();
        } else if (stage == 6 && dialog == null) {
            timer.stop();
            System.out.println("PASS safe dispense: no warning popup; stock is 16; override count remains one.");
            List<AuditEntry> entries = app.audit.getAll().subList(auditStart, app.audit.getAll().size());
            for (AuditEntry entry : entries) {
                String text = entry.toString();
                require(!text.contains("Confidential") && !text.contains("Penicillin")
                    && !text.contains("Clinician reviewed"), "Sensitive data must not appear in audit");
                System.out.println(text);
            }
            System.out.println("PASS audit privacy: no patient names, allergy text, or free-text override reason.");
            frame.dispose();
        }
    }

    private void fillAndSubmit(String id) {
        namedField(panel, "Patient ID").setText(id);
        namedField(panel, "Medicine").setText("Amoxicillin");
        namedField(panel, "Quantity").setText("2");
        submit().doClick();
    }
    private JButton submit() { return button(panel, "Check safety and dispense"); }
    private long overrideCount() {
        return app.audit.getAll().stream().filter(e -> e.getAction().equals("safetyOverride")).count();
    }
    private static JDialog visibleDialog() {
        for (Window window : Window.getWindows())
            if (window instanceof JDialog dialog && dialog.isShowing()) return dialog;
        return null;
    }
    private static JButton button(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container child) {
                JButton match = button(child, text);
                if (match != null) return match;
            }
        }
        return null;
    }
    private static JTextField namedField(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (component instanceof JTextField field && name.equals(field.getName())) return field;
            if (component instanceof Container child) {
                JTextField match = namedField(child, name);
                if (match != null) return match;
            }
        }
        return null;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void fail(Throwable error) {
        timer.stop();
        error.printStackTrace();
        for (Window window : Window.getWindows()) window.dispose();
        System.exit(1);
    }
}
