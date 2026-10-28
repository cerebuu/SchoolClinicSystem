package clinic.app.ui.inventory;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class InventoryTableModel extends PagedTableModel<InventoryProductRow> {
    @Override public Object key(int index) {
        return row(index).key();
    }
    public InventoryTableModel() {
        super("Medicine", "ATC code", "Eligible quantity", "Next expiry", "Batches");
    }
    protected Object value(InventoryProductRow r, int c) {
        return switch(c) {
            case 0 -> r.product().name();
            case 1 -> r.product();
            case 2 -> r.eligibleQuantity()==0?"Out of stock":r.eligibleQuantity();
            case 3 -> r.nextExpiry()==null?"—":r.nextExpiry();
            case 4 -> r.batches().size();
            default -> "";
        };
    }
}
