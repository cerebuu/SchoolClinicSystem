package clinic.service;
import clinic.core.Login;
import clinic.model.*;
import java.time.LocalDateTime;
import java.util.*;
public final class AdminService {
    private final Login login;
    private final AuditLog audit;
    private final Map<String, Campus> campuses=new LinkedHashMap<>();
    public AdminService(Login login, AuditLog audit) {
        this.login=login;
        this.audit=audit;
    }
    private User authorize(String action) {
        User u=login.getCurrentUser().orElse(null);
        boolean allowed=u!=null&&User.ADMIN.equals(u.getRole());
        audit.record(LocalDateTime.now(), u==null?"unknown":u.getUserId(), u==null?"unknown":u.getName(), u==null?"unknown":u.getRole(), action, "administration", allowed);
        if(!allowed)throw new SecurityException("Administrator session required.");
        return u;
    }
    public synchronized void addUser(User u, String password) {
        authorize("addUser");
        login.addUser(u, password);
    }
    public synchronized void updateUser(User u, boolean active) {
        authorize("updateUser");
        login.updateUser(u, active);
    }
    public synchronized PageResult<User> users(int page, int size, String filter) {
        authorize("listUsers");
        return login.userPage(page, size, filter);
    }
    public synchronized void saveCampus(Campus campus, boolean edit) {
        authorize("saveCampus");
        if(edit&&!campuses.containsKey(campus.id()))throw new IllegalArgumentException("Campus not found.");
        if(!edit&&campuses.containsKey(campus.id()))throw new IllegalArgumentException("Campus ID already exists.");
        campuses.put(campus.id(), campus);
    }
    public synchronized PageResult<Campus> campuses(int page, int size, String filter) {
        authorize("listCampuses");
        String q=filter.toLowerCase(Locale.ROOT);
        return PageResult.select(campuses.values(), page, size, c->c.id().toLowerCase(Locale.ROOT).contains(q)||c.name().toLowerCase(Locale.ROOT).contains(q));
    }
    public synchronized PageResult<AuditEntry> audit(int page, int size, String query) {
        authorize("viewAudit");
        return audit.page(page, size, query);
    }
    public synchronized int userCount() {
        authorize("adminSummary");
        return login.userPage(0, 1, "").total();
    }
    public synchronized int campusCount() {
        authorize("adminSummary");
        return campuses.size();
    }
    public static User user(String id, String name, String role) {
        return switch(role) {
            case User.ADMIN->new Administrator(id, name);
            case User.NURSE->new SchoolNurse(id, name);
            case User.ASSISTANT->new NurseAssistant(id, name);
            case User.DOCTOR->new Doctor(id, name, "");
            default->throw new IllegalArgumentException("Invalid role.");
        };
    }
}
