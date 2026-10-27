package clinic.model;
import java.time.*;
import clinic.util.Validation;
public class MedicineTransaction implements Formattable {
    private final String transactionId, patientId, staffId, remarks, visitId;
    private final String campusId;
    private final MedicineProduct medicineProduct;
    private final java.util.List<BatchAllocation> batchAllocations;
    private final int quantity;
    private final LocalDate expirationDate;
    private final LocalDateTime dateTime;
    public MedicineTransaction(String id, String medicine, String patient, String staff, int quantity, LocalDate expiry, String remarks) {
        this(id, medicine, patient, staff, quantity, expiry, remarks, LocalDateTime.now());
    }
    public MedicineTransaction(String id, String medicine, String patient, String staff, int quantity, LocalDate expiry, String remarks, LocalDateTime time) {
        this(id,new MedicineProduct(medicine,"",""),patient,staff,quantity,expiry,remarks,time,null,java.util.List.of());
    }
    public MedicineTransaction(String id, MedicineProduct medicine, String patient, String staff, int quantity,
            LocalDate expiry, String remarks, LocalDateTime time, String visitId,
            java.util.List<BatchAllocation> batchAllocations) {
        this(id,medicine,patient,staff,quantity,expiry,remarks,time,visitId,batchAllocations,null);
    }
    public MedicineTransaction(String id,MedicineProduct medicine,String patient,String staff,int quantity,
            LocalDate expiry,String remarks,LocalDateTime time,String visitId,
            java.util.List<BatchAllocation> batchAllocations,String campusId) {
        dateTime=java.util.Objects.requireNonNull(time);
        transactionId=Validation.required(id, "Transaction ID");
        medicineProduct=java.util.Objects.requireNonNull(medicine);
        patientId=Validation.required(patient, "Patient ID");
        staffId=Validation.required(staff, "Staff ID");
        this.quantity=Validation.positive(quantity);
        expirationDate=expiry;
        this.remarks=remarks;
        this.visitId=visitId==null||visitId.isBlank()?null:visitId.trim();
        this.campusId=campusId==null||campusId.isBlank()?null:Validation.required(campusId,"Campus ID");
        this.batchAllocations=java.util.List.copyOf(batchAllocations);
        int allocated=this.batchAllocations.stream().mapToInt(BatchAllocation::quantity).sum();
        if(allocated!=0&&allocated!=quantity)throw new IllegalArgumentException("Batch allocations must equal the transaction quantity.");
    }
    public String getTransactionId() {
        return transactionId;
    }
    public String getMedicineName() {
        return medicineProduct.name();
    }
    public String getMedicineDisplayName() { return medicineProduct.displayName(); }
    public MedicineProduct getMedicineProduct() { return medicineProduct; }
    public String getVisitId() { return visitId; }
    public String getCampusId() { return campusId; }
    public java.util.List<BatchAllocation> getBatchAllocations() { return batchAllocations; }
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
        String batches=batchAllocations.isEmpty()?"Batch detail unavailable for this legacy transaction."
            :batchAllocations.stream().map(a->a.batchId()+" × "+a.quantity()+" expiring "+a.expiry())
                .collect(java.util.stream.Collectors.joining("\n"));
        return transactionId+" | "+patientId+" | "+medicineProduct.displayName()+" | "+quantity+" | "
            +clinic.util.DateUtil.format(dateTime)+"\nVisit ID: "+(visitId==null?"—":visitId)
            +"\nCampus: "+(campusId==null?"Unknown":campusId)
            +"\nBatch allocations:\n"+batches+"\nRemarks: "+remarks;
    }
}
