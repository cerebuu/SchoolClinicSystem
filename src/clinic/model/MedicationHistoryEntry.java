package clinic.model;

import clinic.util.DateUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Paged display row that combines dispensing and inventory movement events. */
public record MedicationHistoryEntry(String id, Type type, LocalDateTime dateTime,
        MedicineProduct medicine, int quantity, String actorId, String campusId,
        String patientId, String visitId, LocalDate expiry, String batchId,
        List<BatchAllocation> batchAllocations, String remarks) {
    public enum Type { DISPENSE, INITIAL_STOCK, RESTOCK, REFILL }

    public MedicationHistoryEntry {
        id=clinic.util.Validation.required(id,"History ID");
        type=Objects.requireNonNull(type);
        dateTime=Objects.requireNonNull(dateTime);
        medicine=Objects.requireNonNull(medicine);
        clinic.util.Validation.positive(quantity);
        actorId=clinic.util.Validation.required(actorId,"Actor ID");
        campusId=campusId==null||campusId.isBlank()?null:clinic.util.Validation.required(campusId,"Campus ID");
        patientId=patientId==null||patientId.isBlank()?null:patientId.trim();
        visitId=visitId==null||visitId.isBlank()?null:visitId.trim();
        batchId=batchId==null||batchId.isBlank()?null:batchId.trim();
        batchAllocations=List.copyOf(batchAllocations==null?List.of():batchAllocations);
        remarks=remarks==null?"":remarks;
    }

    public static MedicationHistoryEntry from(MedicineTransaction transaction) {
        return new MedicationHistoryEntry(transaction.getTransactionId(),Type.DISPENSE,
            transaction.getDateTime(),transaction.getMedicineProduct(),transaction.getQuantity(),
            transaction.getStaffId(),transaction.getCampusId(),transaction.getPatientId(),
            transaction.getVisitId(),transaction.getExpirationDate(),null,
            transaction.getBatchAllocations(),transaction.getRemarks());
    }

    public static MedicationHistoryEntry from(InventoryMovement movement) {
        return new MedicationHistoryEntry(movement.id(),Type.valueOf(movement.type().name()),
            movement.dateTime(),movement.medicine(),movement.quantity(),movement.actorId(),
            movement.campusId(),null,null,movement.expiry(),movement.batchId(),List.of(),"");
    }

    public String quantityLabel() {
        return type==Type.DISPENSE?"−"+quantity:"+"+quantity;
    }

    public String toReport() {
        StringBuilder report=new StringBuilder(id).append(" | ").append(type).append(" | ")
            .append(medicine.displayName()).append(" | ").append(quantityLabel()).append(" | ")
            .append(DateUtil.format(dateTime)).append("\nActor: ").append(actorId)
            .append("\nCampus: ").append(campusId==null?"Unassigned":campusId);
        if(type==Type.DISPENSE) {
            report.append("\nPatient ID: ").append(patientId)
                .append("\nVisit ID: ").append(visitId==null?"—":visitId);
            String allocations=batchAllocations.isEmpty()?"Batch detail unavailable for this legacy transaction."
                :batchAllocations.stream().map(a->a.batchId()+" × "+a.quantity()+" expiring "+a.expiry())
                    .collect(java.util.stream.Collectors.joining("\n"));
            report.append("\nBatch allocations:\n").append(allocations);
            if(!remarks.isBlank())report.append("\nRemarks: ").append(remarks);
        } else {
            report.append("\nExpiry: ").append(expiry);
            if(batchId!=null)report.append("\nBatch ID: ").append(batchId);
        }
        return report.toString();
    }
}
