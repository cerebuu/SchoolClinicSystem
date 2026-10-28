package clinic.app.ui.inventory;

import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** One catalog row for a medicine identity and its individual stored batches. */
public record InventoryProductRow(MedicineProduct product, int eligibleQuantity,
        LocalDate nextExpiry, List<Medicine> batches) {
    public InventoryProductRow {
        Objects.requireNonNull(product);
        if (eligibleQuantity < 0) throw new IllegalArgumentException("Eligible quantity cannot be negative.");
        batches = List.copyOf(batches);
    }

    public String key() {
        return product.key();
    }
}
