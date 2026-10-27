package clinic.service;
import clinic.core.Login;
import clinic.model.*;
import java.time.LocalDateTime;
import java.util.*;
public final class AdminService {
    private final Login login;
    private final AuditLog audit;
    private final Map<String, Campus> campuses=new LinkedHashMap<>();
    private clinic.persistence.AdminRepository repository;
    private java.util.function.Consumer<String> changed=action->{};
    public synchronized void onChanged(java.util.function.Consumer<String> listener){changed=listener;}
    public AdminService(Login login, AuditLog audit) {
        this.login=login;
        this.audit=audit;
    }
    public AdminService(Login login, AuditLog audit, clinic.persistence.AdminRepository repository) {
        this(login,audit);
        this.repository=repository;
        repository.campuses().forEach(c -> campuses.put(c.id(),c));
    }
    private User authorize(String action) {
        User u=login.getCurrentUser().orElse(null);
        boolean allowed=u!=null&&User.ADMIN.equals(u.getRole());
        // Auditing an audit/summary read would create an endless refresh -> audit -> refresh loop.
        if(!allowed || (!action.equals("viewAudit")&&!action.equals("adminSummary")))
            audit.record(LocalDateTime.now(),u==null?"unknown":u.getUserId(),u==null?"unknown":u.getName(),
                u==null?"unknown":u.getRole(),action,"administration",allowed,u==null?null:u.getCampusId());
        if(!allowed)throw new SecurityException("Administrator session required.");
        return u;
    }
    public synchronized void addUser(User u, String password) {
        synchronized (login) {
            authorize("addUser");
            validateCampus(u);
            login.addUser(u, password);
            changed.accept("users");
        }
    }
    public synchronized void updateUser(User u, boolean active) {
        synchronized (login) {
            authorize("updateUser");
            validateCampus(u);
            login.updateUser(u, active);
            changed.accept("users");
        }
    }
    private void validateCampus(User user) {
        if(!User.ADMIN.equals(user.getRole())
                &&(user.getCampusId()==null||!campuses.containsKey(user.getCampusId())))
            throw new IllegalArgumentException("Assign a valid campus to every clinical staff account.");
        if(user.getCampusId()!=null&&!campuses.containsKey(user.getCampusId()))
            throw new IllegalArgumentException("Selected campus does not exist.");
    }
    public synchronized PageResult<User> users(int page, int size, String filter) {
        synchronized (login) {
            authorize("listUsers");
            return login.userPage(page, size, filter);
        }
    }
    public synchronized void saveCampus(Campus campus, boolean edit) {
        synchronized (login) {
            authorize("saveCampus");
            if(edit&&!campuses.containsKey(campus.id()))throw new IllegalArgumentException("Campus not found.");
            if(!edit&&campuses.containsKey(campus.id()))throw new IllegalArgumentException("Campus ID already exists.");
            if (repository != null) repository.save(campus,edit);
            campuses.put(campus.id(), campus);
            changed.accept("campuses");
        }
    }
    public synchronized PageResult<Campus> campuses(int page, int size, String filter) {
        synchronized (login) {
            authorize("listCampuses");
            String q=filter.toLowerCase(Locale.ROOT);
            return PageResult.select(campuses.values(), page, size, c->c.id().toLowerCase(Locale.ROOT).contains(q)||c.name().toLowerCase(Locale.ROOT).contains(q));
        }
    }
    public synchronized String campusName(String campusId) {
        Campus campus=campusId==null?null:campuses.get(campusId);
        return campus==null?null:campus.name();
    }
    public synchronized PageResult<AuditEntry> audit(int page, int size, String query) {
        synchronized (login) {
            authorize("viewAudit");
            return audit.page(page, size, query);
        }
    }
    public synchronized int userCount() {
        synchronized (login) {
            authorize("adminSummary");
            return login.userPage(0, 1, "").total();
        }
    }
    public synchronized int campusCount() {
        synchronized (login) {
            authorize("adminSummary");
            return campuses.size();
        }
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
    public synchronized Summary summary() {
        synchronized (login) {
            authorize("adminSummary");
            List<AuditEntry> entries = audit.getAll();
            java.time.LocalDate today = java.time.LocalDate.now();
            return new Summary(login.userPage(0, 1, "").total(), campuses.size(),
                entries.stream().filter(e -> e.getTimestamp().toLocalDate().equals(today)).count(),
                entries.stream().filter(e -> !e.isAllowed()).count());
        }
    }
    public record Summary(int users, int campuses, long eventsToday, long denied) {}
}
