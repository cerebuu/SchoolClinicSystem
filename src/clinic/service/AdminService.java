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
        seedNurseCampuses();
    }
    public AdminService(Login login, AuditLog audit, clinic.persistence.AdminRepository repository) {
        this.login=login;
        this.audit=audit;
        this.repository=repository;
        repository.campuses().forEach(c -> campuses.put(c.id(),c));
        seedNurseCampuses();
    }
    /** Installs local defaults into the campus catalog; remote choices must already be stored. */
    private void seedNurseCampuses() {
        for(NurseCampus approved:NurseCampus.choices()) {
            Campus expected=approved.campus();
            if(!campuses.containsKey(approved.id())) {
                if(repository==null)campuses.put(expected.id(),expected);
                else {
                    repository.installDefaultCampus(expected);
                    repository.campuses().stream().filter(campus->campus.id().equals(expected.id()))
                        .findFirst().ifPresent(campus->campuses.put(campus.id(),campus));
                }
            }
        }
    }
    private User authorize(String action) {
        User u=login.getCurrentUser().orElse(null);
        boolean allowed=u!=null&&User.ADMIN.equals(u.getRole());
        // Auditing an audit/summary read would create an endless refresh -> audit -> refresh loop.
        if(!allowed || (!isMutation(action)&&!action.equals("viewAudit")&&!action.equals("adminSummary")))
            audit.record(LocalDateTime.now(),u==null?"unknown":u.getUserId(),u==null?"unknown":u.getName(),
                u==null?"unknown":u.getRole(),action,"administration",allowed,u==null?null:u.getCampusId());
        if(!allowed)throw new SecurityException("Administrator session required.");
        return u;
    }
    private static boolean isMutation(String action) {
        return action.equals("addUser")||action.equals("updateUser")||action.equals("saveCampus")
            ||action.equals("deleteCampus")||action.equals("resetStaffPassword");
    }
    private void completed(User actor,String action) {
        audit.record(LocalDateTime.now(),actor.getUserId(),actor.getName(),actor.getRole(),
            action,"administration",true,actor.getCampusId());
    }
    public synchronized void addUser(User u, String password) {
        synchronized (login) {
            User actor=authorize("addUser");
            validateCampus(u);
            login.addUser(u,password,audit,()->{
                completed(actor,"addUser");
                if(User.NURSE.equals(u.getRole()))recordNurseAssignment(actor,u.getUserId(),null,u.getCampusId());
            });
            changed.accept("users");
        }
    }
    public synchronized void updateUser(User u, boolean active) {
        synchronized (login) {
            User actor=authorize("updateUser");
            validateCampus(u);
            User previous=login.findUser(u.getUserId()).orElseThrow(()->new IllegalArgumentException("User not found."));
            boolean assignmentChanged=(User.NURSE.equals(previous.getRole())||User.NURSE.equals(u.getRole()))
                &&!Objects.equals(previous.getCampusId(),u.getCampusId());
            login.updateUser(u,active,audit,()->{
                completed(actor,"updateUser");
                if(assignmentChanged)recordNurseAssignment(actor,u.getUserId(),previous.getCampusId(),u.getCampusId());
            });
            changed.accept("users");
        }
    }
    public synchronized void resetStaffPassword(String id,String password) {
        synchronized(login) {
            User actor=authorize("resetStaffPassword");
            login.resetStaffPassword(id,password,audit,()->completed(actor,"resetStaffPassword"));
            changed.accept("users");
        }
    }
    private String campusDescription(String id) {
        if(id==null)return "Unassigned";
        Campus campus=campuses.get(id);
        return campus==null?"Unknown campus ("+id+")":campus.name()+" ["+id+"]";
    }
    private void recordNurseAssignment(User actor,String nurseId,String previousId,String newId) {
        String details="nurseId="+nurseId+"; previousCampus="+campusDescription(previousId)
            +"; newCampus="+campusDescription(newId);
        audit.record(LocalDateTime.now(),actor.getUserId(),actor.getName(),actor.getRole(),
            "nurse campus assignment changed",details,true,actor.getCampusId());
    }
    private void validateCampus(User user) {
        if(User.NURSE.equals(user.getRole())&&NurseCampus.forId(user.getCampusId()).isEmpty())
            throw new IllegalArgumentException("Assign the nurse to UIC - Bajada, UIC - Annex, or UIC - Main.");
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
            User actor=authorize("saveCampus");
            if(edit&&!campuses.containsKey(campus.id()))throw new IllegalArgumentException("Campus not found.");
            if(!edit&&campuses.containsKey(campus.id()))throw new IllegalArgumentException("Campus ID already exists.");
            audit.unitOfWork(()->{
                if (repository != null) repository.save(campus,edit);
                completed(actor,"saveCampus");
                return null;
            });
            campuses.put(campus.id(), campus);
            changed.accept("campuses");
        }
    }
    public synchronized void deleteCampus(String campusId) {
        synchronized(login) {
            User actor=authorize("deleteCampus");
            Campus existing=campuses.get(clinic.util.Validation.required(campusId,"Campus ID"));
            if(existing==null)throw new IllegalArgumentException("Campus not found.");
            if(NurseCampus.forId(existing.id()).isPresent())
                throw new IllegalArgumentException("Approved nurse campuses cannot be removed.");
            audit.unitOfWork(()->{
                if(repository!=null)repository.deleteCampus(existing.id());
                completed(actor,"deleteCampus");
                return null;
            });
            campuses.remove(existing.id());
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
            java.time.LocalDate today = java.time.LocalDate.now();
            AuditLog.AuditSummary auditSummary=audit.summary(today);
            return new Summary(login.userPage(0, 1, "").total(), campuses.size(),
                auditSummary.eventsToday(),auditSummary.denied());
        }
    }
    public record Summary(int users, int campuses, long eventsToday, long denied) {}
}
