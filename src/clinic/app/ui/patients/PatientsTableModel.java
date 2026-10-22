package clinic.app.ui.patients;
import clinic.model.*;
import clinic.util.PatientDisplay;
import clinic.app.ui.common.PagedTableModel;
public final class PatientsTableModel extends PagedTableModel<Patient> {
    public PatientsTableModel() {
        super("ID", "Name", "Type", "Section / Position", "Contact", "Employee department");
    }
    protected Object value(Patient r, int c) {
        return switch(c) {
            case 0 -> r == null ? "-" : r.getId();
            case 1 -> r == null ? "-" : r.getName();
            case 2 -> PatientDisplay.getType(r);
            case 3 -> PatientDisplay.getSectionOrPosition(r);
            case 4 -> r == null ? "-" : r.getContactInfo();
            case 5 -> PatientDisplay.getEmployeeDepartment(r);
            default -> "";
        };
    }
}
