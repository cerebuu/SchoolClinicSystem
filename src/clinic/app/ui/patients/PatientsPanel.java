package clinic.app.ui.patients;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.*;
import java.awt.BorderLayout;
import java.util.List;
public final class PatientsPanel extends BasePanel {
    private final PagedTablePanel<Patient> table;
    public PatientsPanel(ApplicationContext app) {
        super("Patients", "Clinic records");
        table=new PagedTablePanel<>(new PatientsTableModel(), app::patients, "Name / ID / section / position");
        add(table, BorderLayout.CENTER);
        action("Register patient", true, ()->FormPopup.show(this, "Register patient", List.of(FormField.text("ID"), FormField.text("Name"), FormField.text("Contact"), FormField.choice("Type", Patient.STUDENT, Patient.EMPLOYEE), FormField.text("Course / Department"), FormField.text("Section / Position"), FormField.value("Year level", "1")), v-> {
            Patient p=Patient.STUDENT.equals(v.get("Type"))?new Student(v.get("ID"), v.get("Name"), v.get("Contact"), v.get("Course / Department"), Integer.parseInt(v.get("Year level")), v.get("Section / Position")):new Employee(v.get("ID"), v.get("Name"), v.get("Contact"), v.get("Course / Department"), v.get("Section / Position"));
            app.clinic().registerPatient(p);
        }, table::reload));
        action("Add allergy", ()-> {
            Patient p=table.selected();
            if(p==null) {
                MessagePopup.warning(this, "Select a patient.");
                return;
            }
            FormPopup.show(this, "Add allergy", List.of(FormField.text("Allergy")), v->app.clinic().addAllergy(p.getId(), v.get("Allergy")), ()->MessagePopup.info(this, "Allergy saved."));
        });
        action("Details", ()-> {
            Patient p=table.selected();
            if(p==null) {
                MessagePopup.warning(this, "Select a patient.");
                return;
            }
            Actions.run(this, ()->app.clinic().findPatient(p.getId()).orElseThrow(), found->MessagePopup.info(this, found.getId()+" | "+found.getName()+"\nContact: "+found.getContactInfo()+"\nSection / Position: "+clinic.util.PatientDisplay.getSectionOrPosition(found)+"\nEmployee department: "+clinic.util.PatientDisplay.getEmployeeDepartment(found)+"\nAllergies: "+String.join(", ", found.getAllergies())));
        });
    }
}
