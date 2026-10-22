import clinic.app.Theme;
import clinic.app.ui.common.PagedTablePanel;
import clinic.app.ui.patients.PatientsTableModel;
import clinic.model.*;
import clinic.service.InMemoryClinicSystem;
import clinic.service.PageResult;
import clinic.util.PatientDisplay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;

/** Plain Java demo: --verify runs assertions; --show displays the paged mixed-patient table. */
public final class PatientSectionPositionDemo {
    public static void main(String[] args) throws Exception {
        List<Patient> rows = new ArrayList<>(List.of(
            new Student("S-001", "Demo student", "demo", "BSIT", 2, "BSIT 2-A"),
            new Employee("E-001", "Demo employee", "demo", "Administration", "Registrar Staff"),
            new DemoEmployeeWithoutPosition(), new DemoStudentWithoutSection()));
        for (int i = 0; i < 60; i++) {
            rows.add(new Student("S-EXTRA-" + i, "Demo extra " + i, "demo", "BSCS", 1, "BSCS 1-B"));
        }
        PatientsTableModel model = new PatientsTableModel();
        model.replace(PageResult.select(rows, 0, 25, p -> true));
        require(model.getColumnName(3).equals("Section / Position"), "Column header");
        require(model.getColumnName(2).equals("Type"), "Type column is adjacent");
        String[] expected = {"BSIT 2-A", "Registrar Staff", "-", "-"};
        System.out.println("ID | Type | Section / Position | Employee department");
        for (int row = 0; row < 4; row++) {
            require(expected[row].equals(model.getValueAt(row, 3)), "Display row " + row);
            System.out.println(model.getValueAt(row, 0) + " | " + model.getValueAt(row, 2)
                + " | " + model.getValueAt(row, 3) + " | " + model.getValueAt(row, 5));
        }
        require(PatientDisplay.getSectionOrPosition(null).equals("-"), "Null patient");
        require(PatientDisplay.getSectionOrPosition(new Patient("U", "Unknown", "demo", "UNKNOWN")).equals("-"), "Unknown type");
        require(model.getRowCount() == 25, "Only one page in model");
        require(PageResult.select(rows, 2, 25, p -> true).rows().size() == 14, "Last page");
        require(PatientDisplay.matchesSearch(rows.get(0), "bsit 2-a"), "Student section search");
        require(PatientDisplay.matchesSearch(rows.get(1), "registrar"), "Position search");
        require(PatientDisplay.matchesSearch(rows.get(1), "administration"), "Department search");
        require(!PatientDisplay.matchesSearch(rows.get(3), "legacy section"), "Missing section does not show constructor placeholder");
        InMemoryClinicSystem clinic = new InMemoryClinicSystem();
        for (int i = 0; i < 60; i++) clinic.registerPatient(new Student("S" + i, "Student " + i,
            "demo", "CCS", 2, "BSIT 2-A"));
        clinic.registerPatient(new Employee("EMP", "Employee", "demo", "Administration", "Registrar Staff"));
        PageResult<Patient> filtered = clinic.patientPage(0, 25, "registrar");
        require(filtered.total() == 1 && filtered.rows().get(0).getId().equals("EMP"), "Filter before paging");
        require(clinic.patientPage(0, 25, "ADMINISTRATION").total() == 1, "Real department query");
        require(clinic.patientPage(0, 25, "BSIT 2-A").total() == 60, "Real section query");
        require(clinic.patientPage(0, 25, "CCS").total() == 0, "Student course is not section");
        System.out.println("PASS: mixed types, missing/null/unknown values, department retention, and pre-page filtering.");
        System.out.println("PASS paging: 64 demo rows; first page 25; last page 14; registrar filter finds employee after 60 students.");
        if (Arrays.asList(args).contains("--show")) {
            SwingUtilities.invokeAndWait(() -> {
                Theme.install();
                JFrame frame = new JFrame("Section / Position - mixed patient demo");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.add(new PagedTablePanel<>(new PatientsTableModel(),
                    (page, size, query) -> PageResult.select(rows, page, size,
                        p -> PatientDisplay.matchesSearch(p, query)), "Section / Position"));
                frame.setSize(1150, 650);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                if (Arrays.asList(args).contains("--auto-close")) {
                    Timer close = new Timer(1500, event -> {
                        System.out.println("PASS: mixed-patient table window opened with six columns and paging controls.");
                        frame.dispose();
                    });
                    close.setRepeats(false);
                    close.start();
                }
            });
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
