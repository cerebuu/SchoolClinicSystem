package clinic.app.ui.admin;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
import java.util.Objects;
import java.util.function.Function;
public final class UsersTableModel extends PagedTableModel<User> {
    private final Function<String,String> campusName;

    public UsersTableModel() {
        this(id -> null);
    }

    public UsersTableModel(Function<String,String> campusName) {
        super("ID", "Name", "Role", "Campus");
        this.campusName=Objects.requireNonNull(campusName);
    }
    protected Object value(User r, int c) {
        return switch(c) {
            case 0 -> r.getUserId();
            case 1 -> r.getName();
            case 2 -> r.getRole();
            case 3 -> {
                String id=r.getCampusId();
                if(id==null)yield "Unassigned";
                String name=campusName.apply(id);
                yield name==null||name.isBlank()?id:id+" | "+name;
            }
            default -> "";
        };
    }
}
