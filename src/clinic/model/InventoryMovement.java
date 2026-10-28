package clinic.model;

import clinic.util.Validation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** A patient-free stock addition event, recorded with its inventory update. */
public record InventoryMovement(String id, Type type, MedicineProduct medicine, int quantity,
        LocalDate expiry, String batchId, LocalDateTime dateTime, String actorId, String campusId) {
    public enum Type { INITIAL_STOCK, RESTOCK, REFILL }

    public InventoryMovement {
        id=id==null?UUID.randomUUID().toString():Validation.required(id,"Movement ID");
        type=Objects.requireNonNull(type,"Movement type is required.");
        medicine=Objects.requireNonNull(medicine,"Medicine is required.");
        quantity=Validation.positive(quantity);
        expiry=Objects.requireNonNull(expiry,"Expiry date is required.");
        batchId=batchId==null||batchId.isBlank()?null:Validation.required(batchId,"Batch ID");
        dateTime=Objects.requireNonNull(dateTime,"Movement time is required.");
        actorId=Validation.required(actorId,"Actor ID");
        campusId=Validation.required(campusId,"Campus ID");
    }
}
