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
        audit.record(LocalDateTime.now(), u==null?"unknown":u.getUserId(), u==null?"unknown":u.getName(), u==null?"unknown":u.getRole(), action, "administration", allowed);
        if(!allowed)throw new SecurityException("Administrator session required.");
        return u;
    }
    public synchronized void addUser(User u, String password) {
        synchronized (login) {
            authorize("addUser");
            login.addUser(u, password);
        }
    }
    public synchronized void updateUser(User u, boolean active) {
        synchronized (login) {
            authorize("updateUser");
            login.updateUser(u, active);
        }
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
        }
    }
    public synchronized PageResult<Campus> campuses(int page, int size, String filter) {
        synchronized (login) {
            authorize("listCampuses");
            String q=filter.toLowerCase(Locale.ROOT);
            return PageResult.select(campuses.values(), page, size, c->c.id().toLowerCase(Locale.ROOT).contains(q)||c.name().toLowerCase(Locale.ROOT).contains(q));
        }
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
