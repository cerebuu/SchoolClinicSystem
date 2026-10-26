package clinic.app.ui.inventory;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class InventoryTableModel extends PagedTableModel<Medicine> {
    @Override public Object key(int index) {
        return row(index).getBatchId();
    }
    public InventoryTableModel() {
        super("Medicine", "Quantity", "Expiry", "Batch date", "Remarks");
    }
    protected Object value(Medicine r, int c) {
        return switch(c) {
            case 0 -> r.getName();
            case 1 -> r.getQuantity();
            case 2 -> r.getExpirationDate();
            case 3 -> r.getBatchDate();
            case 4 -> r.getRemarks();
            default -> "";
        };
    }
}
