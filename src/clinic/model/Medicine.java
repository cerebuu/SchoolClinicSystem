package clinic.model;
import java.time.LocalDate;
import java.util.UUID;
import clinic.util.Validation;
public class Medicine {
    private final String batchId;
    private final String name;
    private final String strength;
    private final String formulation;
    private String remarks;
    private LocalDate expirationDate, batchDate;
    private int quantity;
    public Medicine(String name, int quantity, LocalDate expiry, LocalDate batch, String remarks) {
        this(UUID.randomUUID().toString(),new MedicineProduct(name,"",""),quantity,expiry,batch,remarks);
    }
    public Medicine(String batchId,String name,int quantity,LocalDate expiry,LocalDate batch,String remarks) {
        this(batchId,new MedicineProduct(name,"",""),quantity,expiry,batch,remarks);
    }
    public Medicine(MedicineProduct product,int quantity,LocalDate expiry,LocalDate batch,String remarks) {
        this(UUID.randomUUID().toString(),product,quantity,expiry,batch,remarks);
    }
    public Medicine(String batchId,MedicineProduct product,int quantity,LocalDate expiry,LocalDate batch,String remarks) {
        this.batchId=java.util.UUID.fromString(Validation.required(batchId,"Batch ID")).toString();
        MedicineProduct validProduct=java.util.Objects.requireNonNull(product);
        this.name=validProduct.name();
        this.strength=validProduct.strength();
        this.formulation=validProduct.formulation();
        if(quantity<0||expiry==null||batch==null)throw new IllegalArgumentException("Valid stock and dates required.");
        this.quantity=quantity;
        expirationDate=expiry;
        batchDate=batch;
        this.remarks=remarks==null?"":remarks;
    }
    public String getName() {
        return name;
    }
    public String getStrength() { return strength; }
    public String getFormulation() { return formulation; }
    public MedicineProduct getProduct() { return new MedicineProduct(name,strength,formulation); }
    public String getDisplayName() { return getProduct().displayName(); }
    public String getBatchId() { return batchId; }
    public String getRemarks() {
        return remarks;
    }
    public void setRemarks(String value) {
        String normalized=value==null?"":value.trim();
        if(normalized.length()>200)throw new IllegalArgumentException("Remarks must be at most 200 characters.");
        remarks=normalized;
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
    public void refillFromEmpty(int amount,LocalDate expiry,String value) {
        if(quantity!=0)throw new IllegalStateException("Batch still has stock");
        Validation.positive(amount);
        if(expiry==null||!expiry.isAfter(LocalDate.now()))throw new IllegalArgumentException("Expiry must be a valid future date (YYYY-MM-DD).");
        String normalized=value==null?"":value.trim();
        if(normalized.length()>200)throw new IllegalArgumentException("Remarks must be at most 200 characters.");
        quantity=amount;expirationDate=expiry;batchDate=LocalDate.now();remarks=normalized;
    }
    public boolean isExpired() {
        return expirationDate.isBefore(LocalDate.now());
    }
    public boolean isLowStock(int threshold) {
        return quantity<=threshold;
    }
}
