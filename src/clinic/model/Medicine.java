package clinic.model;
import java.time.LocalDate;
import clinic.util.Validation;
public class Medicine {
    private final String name, remarks;
    private final LocalDate expirationDate, batchDate;
    private int quantity;
    public Medicine(String name, int quantity, LocalDate expiry, LocalDate batch, String remarks) {
        this.name=Validation.required(name, "Medicine");
        if(quantity<0||expiry==null||batch==null)throw new IllegalArgumentException("Valid stock and dates required.");
        this.quantity=quantity;
        expirationDate=expiry;
        batchDate=batch;
        this.remarks=remarks==null?"":remarks;
    }
    public String getName() {
        return name;
    }
    public String getRemarks() {
        return remarks;
    }
    public int getQuantity() {
        return quantity;
    }
    public LocalDate getExpirationDate() {
        return expirationDate;
    }
    public LocalDate getBatchDate() {
        return batchDate;
    }
    public void reduceQuantity(int n) {
        Validation.positive(n);
        if(n>quantity)throw new IllegalArgumentException("Insufficient stock.");
        quantity-=n;
    }
    public void addQuantity(int n) {
        quantity=Math.addExact(quantity, Validation.positive(n));
    }
    public boolean isExpired() {
        return expirationDate.isBefore(LocalDate.now());
    }
    public boolean isLowStock(int threshold) {
        return quantity<=threshold;
    }
}
