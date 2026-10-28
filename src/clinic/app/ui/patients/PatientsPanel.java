package clinic.app.ui.patients;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.*;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
public final class PatientsPanel extends BasePanel {
    private final ApplicationContext app;
    private final PagedTablePanel<Patient> table;
    public PatientsPanel(ApplicationContext app) {
        super("Patients", app);
        this.app=app;
        table=new PagedTablePanel<>(new PatientsTableModel(), app::patients, "Name / ID / section / position");
        table.refreshOn(app.changes, clinic.app.DataTopic.PATIENTS);
        table.setRowActions(patient->List.of(
            new RowAction("View details",()->showDetails(patient)),
            new RowAction("Edit patient",()->Actions.runInSession(this,app.login,
                ()->app.clinic().findPatient(patient.getId()).orElseThrow(),this::showEditForm)),
            new RowAction("Add allergy",()->showAddAllergy(patient))));
        add(table, BorderLayout.CENTER);
        action("Register patient", true, ()->FormPopup.show(this, "Register patient", List.of(FormField.text("ID"), FormField.text("Name"), FormField.text("Contact"), FormField.optional("Guardian contact"), FormField.choice("Type", Patient.STUDENT, Patient.EMPLOYEE), FormField.text("Course / Department"), FormField.text("Section / Position"), FormField.value("Year level", "1"), FormField.allergies()), v-> {
            Patient p=Patient.STUDENT.equals(v.get("Type"))?new Student(v.get("ID"), v.get("Name"), v.get("Contact"), v.get("Course / Department"), Integer.parseInt(v.get("Year level")), v.get("Section / Position")):new Employee(v.get("ID"), v.get("Name"), v.get("Contact"), v.get("Course / Department"), v.get("Section / Position"));
            p.setGuardianContact(v.get("Guardian contact").isBlank()?null:v.get("Guardian contact").trim());
            clinic.util.AllergyParser.parse(v.get("Allergies")).forEach(p::addAllergy);
            app.clinic().registerPatient(p);
        }, ()->{}));
    }

    private void showDetails(Patient patient) {
        Actions.runInSession(this,app.login,()->app.clinic().findPatient(patient.getId()).orElseThrow(),found->
            MessagePopup.info(this,found.getId()+" | "+found.getName()+"\nContact: "+found.getContactInfo()
                +"\nSection / Position: "+clinic.util.PatientDisplay.getSectionOrPosition(found)
                +"\nEmployee department: "+clinic.util.PatientDisplay.getEmployeeDepartment(found)
                +"\nAllergies: "+String.join(", ",found.getAllergies())));
    }

    private void showAddAllergy(Patient patient) {
        FormPopup.show(this,"Add allergy",List.of(FormField.allergies()),v->
            clinic.util.AllergyParser.parse(v.get("Allergies"))
                .forEach(allergy->app.clinic().addAllergy(patient.getId(),allergy)),
            ()->MessagePopup.info(this,"Allergy saved."));
    }

    private void showEditForm(Patient patient) {
        List<FormField> fields=new ArrayList<>(List.of(
            new FormField("Name",patient.getName(),true,false,null),
            new FormField("Contact",patient.getContactInfo(),true,false,null),
            new FormField("Guardian contact",patient.getGuardianContact()==null?"":patient.getGuardianContact(),false,false,null)));
        if(patient instanceof Student student) {
            fields.add(new FormField("Course",student.getCourse(),true,false,null));
            fields.add(new FormField("Year level",Integer.toString(student.getYearLevel()),true,false,null,0,0,
                value->{
                    try { if(value.matches("[0-9]+")&&Integer.parseInt(value)>0)return null; }
                    catch(NumberFormatException ignored) { }
                    return "Year level must be a whole number of at least 1.";
                }));
            fields.add(new FormField("Section",student.getSection(),true,false,null));
        } else if(patient instanceof Employee employee) {
            fields.add(new FormField("Department",employee.getDepartment(),true,false,null));
            fields.add(new FormField("Position",employee.getPosition(),true,false,null));
        } else {
            MessagePopup.warning(this,"This patient type cannot be edited.");
            return;
        }
        FormPopup.show(this,"Edit patient — ID "+patient.getId(),fields,values->{
            Patient updated;
            if(patient instanceof Student) {
                updated=new Student(patient.getId(),values.get("Name"),values.get("Contact"),
                    values.get("Course"),Integer.parseInt(values.get("Year level")),values.get("Section"));
            } else {
                updated=new Employee(patient.getId(),values.get("Name"),values.get("Contact"),
                    values.get("Department"),values.get("Position"));
            }
            updated.setGuardianContact(values.get("Guardian contact").isBlank()?null:values.get("Guardian contact"));
            app.clinic().updatePatient(patient.getId(),updated);
        },()->MessagePopup.info(this,"Patient updated."));
    }
}
