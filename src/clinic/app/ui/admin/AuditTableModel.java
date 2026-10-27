package clinic.app.ui.admin;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class AuditTableModel extends PagedTableModel<clinic.service.AuditEntry> {
    public AuditTableModel() {
        super("Time", "User ID", "Role", "Campus", "Action", "Details", "Allowed");
    }
    protected Object value(clinic.service.AuditEntry r, int c) {
        return switch(c) {
            case 0 -> r.getTimestamp();
            case 1 -> r.getUserId();
            case 2 -> r.getRole();
            case 3 -> r.getCampusId()==null?"Unknown":r.getCampusId();
            case 4 -> r.getAction();
            case 5 -> r.getDetails();
            case 6 -> r.isAllowed();
            default -> "";
        };
    }
}
