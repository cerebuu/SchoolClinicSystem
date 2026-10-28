package clinic.model;

import clinic.util.Validation;
import java.time.LocalDate;

public record BatchAllocation(String batchId, int quantity, LocalDate expiry) {
    public BatchAllocation {
        batchId = Validation.required(batchId, "Batch ID");
        Validation.positive(quantity);
        java.util.Objects.requireNonNull(expiry);
    }
}
