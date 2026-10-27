package clinic.service;

import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import clinic.util.Validation;

public final class DispenseItem {
    private final MedicineProduct product;
    private final int quantity;

    public DispenseItem(String medicine,int quantity) {
        this(new MedicineProduct(medicine,"",""),quantity);
    }

    public DispenseItem(Medicine medicine,int quantity) {
        this(medicine.getProduct(),quantity);
    }

    public DispenseItem(MedicineProduct product,int quantity) {
        this.product=java.util.Objects.requireNonNull(product);
        this.quantity=Validation.positive(quantity);
    }

    public String medicine() { return product.name(); }
    public MedicineProduct product() { return product; }
    public int quantity() { return quantity; }

    @Override public boolean equals(Object other) {
        return other instanceof DispenseItem item&&product.equals(item.product)&&quantity==item.quantity;
    }

    @Override public int hashCode() { return java.util.Objects.hash(product,quantity); }

    @Override public String toString() { return product.displayName()+" x "+quantity; }
}
