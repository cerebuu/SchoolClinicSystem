package clinic.app.ui.consultations;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class ConsultationsTableModel extends PagedTableModel<MedicalRecord> {
    public ConsultationsTableModel() {
        super("Record ID", "Patient ID", "Symptoms", "Concern", "Medicines administered", "Campus", "Date");
    }
    protected Object value(MedicalRecord r, int c) {
        return switch(c) {
            case 0 -> r.getRecordId();
            case 1 -> r.getPatientId();
            case 2 -> String.join(", ", r.getSymptoms());
            case 3 -> r.getReason();
            case 4 -> r.getMedicationGiven().isBlank()?"—":r.getMedicationGiven();
            case 5 -> r.getCampusId()==null?"Unknown":r.getCampusId();
            case 6 -> r.getVisitDateTime();
            default -> "";
        };
    }
}
