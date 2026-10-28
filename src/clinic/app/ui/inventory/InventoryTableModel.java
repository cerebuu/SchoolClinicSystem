package clinic.app.ui.inventory;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class InventoryTableModel extends PagedTableModel<InventoryProductRow> {
    @Override public Object key(int index) {
        return row(index).key();
    }
    public InventoryTableModel() {
        super("Medicine", "Strength", "Formulation", "Eligible quantity", "Next expiry", "Batches");
    }
    protected Object value(InventoryProductRow r, int c) {
        return switch(c) {
            case 0 -> r.product().name();
            case 1 -> r.product().strength().isBlank()?"—":r.product().strength();
            case 2 -> r.product().formulation().isBlank()?"—":r.product().formulation();
            case 3 -> r.eligibleQuantity();
            case 4 -> r.nextExpiry()==null?"—":r.nextExpiry();
            case 5 -> r.batches().size();
            default -> "";
        };
    }
}
