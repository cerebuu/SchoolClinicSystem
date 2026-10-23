package clinic.app.ui.admin;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class CampusesTableModel extends PagedTableModel<Campus> {
    public CampusesTableModel() {
        super("Campus ID", "Name");
    }
    protected Object value(Campus r, int c) {
        return switch(c) {
            case 0 -> r.id();
            case 1 -> r.name();
            default -> "";
        };
    }
}
