package clinic.app;
import clinic.core.*;
import clinic.model.*;
import clinic.service.*;
import java.lang.reflect.*;
public final class ApplicationContext {
    public final AuditLog audit=new AuditLog();
    public final Login login;
    public final AdminService admin;
    private final InMemoryClinicSystem store=new InMemoryClinicSystem();
    public ApplicationContext(boolean demo) {
        login=demo?Login.withSampleUsers(audit):new Login(audit);
        admin=new AdminService(login, audit);
        if (demo) seedSampleCampuses();
    }
    private void seedSampleCampuses() {
        login.login("AD001", "admin123").orElseThrow(
            () -> new IllegalStateException("Demo administrator account is unavailable."));
        try {
            admin.saveCampus(new Campus("C1", "Campus 1 (sample)"), false);
            admin.saveCampus(new Campus("C2", "Campus 2 (sample)"), false);
            admin.saveCampus(new Campus("C3", "Campus 3 (sample)"), false);
        } finally {
            login.logout();
        }
    }
    public User current() {
        return login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
    }
    public void require(String feature) {
        requireAny(feature);
    }
    private void requireAny(String... features) {
        User user = current();
        boolean allowed = java.util.Arrays.stream(features).anyMatch(user::canAccess);
        audit.record(java.time.LocalDateTime.now(), user.getUserId(), user.getName(),
            user.getRole(), "clinicQuery", "Read permission check", allowed);
        if (!allowed) throw new SecurityException("Access denied.");
    }
    // The proxy rechecks session identity on every call, invalidating retained screen references after logout.
    public ClinicSystem clinic() {
        User owner=current();
        long version = login.getSessionVersion();
        ClinicSystem secured=new SecuredClinicSystem(store, owner, audit);
        return (ClinicSystem)Proxy.newProxyInstance(ClinicSystem.class.getClassLoader(), new Class<?>[] {
            ClinicSystem.class
        }, (p, m, a)-> {
            if(current()!=owner || login.getSessionVersion()!=version)throw new SecurityException("Session expired.");
            try {
                return m.invoke(secured, a);
            }
            catch(InvocationTargetException ex) {
                throw ex.getCause();
            }
        });
    }
    public PageResult<Patient> patients(int page, int size, String q) {
        require(User.RECORD_TRANSACTION);
        return store.patientPage(page, size, q);
    }
    public PageResult<Medicine> medicines(int page, int size, String q) {
        require(User.VIEW_STOCK);
        return store.medicinePage(page, size, q);
    }
    public PageResult<MedicalRecord> records(int page, int size, String q) {
        requireAny(User.RECORDS, User.VIEW_REPORTS);
        return store.recordPage(page, size, q);
    }
    public PageResult<MedicalRecord> recentVisits(int page, int size, String query) {
        require(User.RECORD_TRANSACTION);
        User user = current();
        return store.recentRecordPage(page, size, query,
            user.canAccess(User.RECORDS) ? null : user.getUserId());
    }
    public PageResult<MedicalRecord> recentVisits(int page, int size, String nameOrId,
            java.time.LocalDateTime fromInclusive, java.time.LocalDateTime toInclusive) {
        User user = current();
        boolean allowed = user.canAccess(User.RECORD_TRANSACTION);
        audit.record(java.time.LocalDateTime.now(), user.getUserId(), user.getName(), user.getRole(),
            "visitSearch", "visit search performed", allowed);
        if (!allowed) throw new SecurityException("Access denied.");
        return store.recentRecordPage(page, size, nameOrId,
            user.canAccess(User.RECORDS) ? null : user.getUserId(), fromInclusive, toInclusive);
    }
    public PageResult<NotificationLog> notifications(int page, int size, String q) {
        require(User.RECORD_TRANSACTION);
        return store.notificationPage(page, size, q);
    }
    public PageResult<Alert> alerts(int page, int size, String q) {
        require(User.RECORDS);
        return store.alertPage(page, size, q);
    }
    public int patientCount() {
        require(User.RECORD_TRANSACTION);
        return store.patientCount();
    }
    public int visitCount() {
        require(User.RECORD_TRANSACTION);
        return store.recordCount();
    }
}
