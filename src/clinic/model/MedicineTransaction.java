package clinic.model;
import java.time.*;
import clinic.util.Validation;
public class MedicineTransaction implements Formattable {
    private final String transactionId, medicineName, patientId, staffId, remarks;
    private final int quantity;
    private final LocalDate expirationDate;
    private final LocalDateTime dateTime=LocalDateTime.now();
    public MedicineTransaction(String id, String medicine, String patient, String staff, int quantity, LocalDate expiry, String remarks) {
        transactionId=Validation.required(id, "Transaction ID");
        medicineName=Validation.required(medicine, "Medicine");
        patientId=Validation.required(patient, "Patient ID");
        staffId=Validation.required(staff, "Staff ID");
        this.quantity=Validation.positive(quantity);
        expirationDate=expiry;
        this.remarks=remarks;
    }
    public String getTransactionId() {
        return transactionId;
    }
    public String getMedicineName() {
        return medicineName;
    }
    public String getPatientId() {
        return patientId;
    }
    public String getStaffId() {
        return staffId;
    }
    public int getQuantity() {
        return quantity;
    }
    public LocalDateTime getDateTime() {
        return dateTime;
    }
    public LocalDate getExpirationDate() {
        return expirationDate;
    }
    public String getRemarks() {
        return remarks;
    }
    public String toReport() {
        return transactionId+" | "+patientId+" | "+medicineName+" | "+quantity+" | "+dateTime;
    }
}
