package clinic.app.ui.admin;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class UsersTableModel extends PagedTableModel<User> {
    public UsersTableModel() {
        super("ID", "Name", "Role");
    }
    protected Object value(User r, int c) {
        return switch(c) {
            case 0 -> r.getUserId();
            case 1 -> r.getName();
            case 2 -> r.getRole();
            default -> "";
        };
    }
}
