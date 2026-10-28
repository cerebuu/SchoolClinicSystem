import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.dispense.DispensePanel;
import clinic.model.Student;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Opens the medication-history view with synthetic records for layout inspection. */
public final class DispenseScrollingDemo {
    private DispenseScrollingDemo() {}

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A desktop is required.");
        }
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            ApplicationContext app = new ApplicationContext(true);
            try {
                app.login.login("N001", "nurse123").orElseThrow();
                for (int i = 0; i < 40; i++) {
                    String patientId = "HISTORY-P" + i;
                    String medicine = "History demo medicine " + i;
                    app.clinic().registerPatient(new Student(patientId, "History patient " + i,
                        "demo", "BSIT", 2, "BSIT 2-A"));
                    app.clinic().addMedicine(medicine, 10, LocalDate.now().plusMonths(6), "");
                    app.clinic().dispenseMedicine(patientId, medicine, 1, "N001", false);
                }
                JFrame frame = new JFrame("Medication history demo");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setContentPane(new DispensePanel(app));
                frame.setSize(1000, 700);
                frame.setLocationByPlatform(true);
                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent event) {
                        app.close();
                    }
                });
                frame.setVisible(true);
                System.out.println("Opened medication history with 40 synthetic transactions.");
                System.out.println("Use + New consultation to exercise the integrated workflow.");
            } catch (RuntimeException failure) {
                app.close();
                throw failure;
            }
        });
    }
}
