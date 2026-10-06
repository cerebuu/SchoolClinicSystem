package clinic.model;

import java.time.LocalDate;

// This class is responsible for carrying privacy-safe stock evidence for an alert.
public final class StockEvidence {

    private final String medicineName;
    private final int quantity;
    private final LocalDate expiryDate;

    public StockEvidence(String medicineName, int quantity, LocalDate expiryDate) {
        if (medicineName == null || medicineName.trim().isEmpty()
                || expiryDate == null || quantity < 0) {
            throw new IllegalArgumentException("Valid stock evidence is required.");
        }
        this.medicineName = medicineName;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}
