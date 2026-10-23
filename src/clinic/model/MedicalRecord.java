package clinic.model;
import java.time.LocalDateTime;
import java.util.List;
import clinic.util.*;
public class MedicalRecord implements Formattable {
    private final String recordId, patientId, reason, assessment, medicationGiven, staffId;
    private final List<String> symptoms;
    private final LocalDateTime visitDateTime;
    public MedicalRecord(String id, String patient, List<String> symptoms, String reason, String assessment, String medicine, String staff) {
        this(id, patient, symptoms, reason, assessment, medicine, staff, LocalDateTime.now());
    }
    public MedicalRecord(String id, String patient, List<String> symptoms, String reason, String assessment, String medicine, String staff, LocalDateTime time) {
        visitDateTime=java.util.Objects.requireNonNull(time);
        recordId=Validation.required(id, "Record ID");
        patientId=Validation.required(patient, "Patient ID");
        if(symptoms==null||symptoms.isEmpty())throw new IllegalArgumentException("Symptoms are required.");
        this.symptoms=symptoms.stream().map(s -> Validation.required(s, "Symptom")).distinct().toList();
        this.reason=Validation.required(reason, "Reason");
        this.assessment=assessment==null?"":assessment.trim();
        medicationGiven=medicine==null?"":medicine.trim();
        staffId=Validation.required(staff, "Staff ID");
    }
    public String getRecordId() {
        return recordId;
    }
    public String getPatientId() {
        return patientId;
    }
    public String getReason() {
        return reason;
    }
    public String getAssessment() {
        return assessment;
    }
    public String getMedicationGiven() {
        return medicationGiven;
    }
    public String getStaffId() {
        return staffId;
    }
    public List<String> getSymptoms() {
        return symptoms;
    }
    public LocalDateTime getVisitDateTime() {
        return visitDateTime;
    }
    public String toReport() {
        return recordId+" | "+patientId+" | "+DateUtil.format(visitDateTime)+"\nSymptoms: "+String.join(", ", symptoms)+"\nConcern: "+reason+"\nRemarks: "+assessment;
    }
}
