import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.consultations.ConsultationsPanel;
import clinic.model.Student;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Opens consultation-based medicine administration with an allergic demo patient. */
public final class AllergyConfirmationDemo {
    private AllergyConfirmationDemo() {}

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A desktop is required.");
        }
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            ApplicationContext app = new ApplicationContext(true);
            try {
                app.login.login("N001", "nurse123").orElseThrow();
                Student patient = new Student("ALLERGY-DEMO", "Allergy demo patient",
                    "demo", "BSIT", 2, "BSIT 2-A");
                patient.addAllergy("Penicillin");
                app.clinic().registerPatient(patient);
                app.clinic().addMedicine("Demo paracetamol", 20, LocalDate.now().plusMonths(6), "");
                app.clinic().addMedicine("Demo amoxicillin", 20, LocalDate.now().plusMonths(6), "");

                JFrame frame = new JFrame("Consultation medicine administration demo");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setContentPane(new ConsultationsPanel(app));
                frame.setSize(1100, 760);
                frame.setLocationByPlatform(true);
                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent event) {
                        app.close();
                    }
                });
                frame.setVisible(true);
                System.out.println("Demo patient ALLERGY-DEMO has a Penicillin allergy.");
                System.out.println("Choose + New Consultation, add a medicine, and Save to see the confirmation.");
            } catch (RuntimeException failure) {
                app.close();
                throw failure;
            }
        });
    }
}
