package clinic.app.ui.inventory;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class InventoryTableModel extends PagedTableModel<Medicine> {
    @Override public Object key(int index) {
        return row(index).getBatchId();
    }
    public InventoryTableModel() {
        super("Medicine", "Strength", "Formulation", "Quantity", "Expiry", "Batch date", "Remarks");
    }
    protected Object value(Medicine r, int c) {
        return switch(c) {
            case 0 -> r.getName();
            case 1 -> r.getStrength();
            case 2 -> r.getFormulation();
            case 3 -> r.getQuantity();
            case 4 -> r.getExpirationDate();
            case 5 -> r.getBatchDate();
            case 6 -> r.getRemarks();
            default -> "";
        };
    }
}
