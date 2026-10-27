package clinic.app;
import clinic.core.*;
import clinic.model.*;
import clinic.service.*;
import java.lang.reflect.*;
import java.util.List;
public final class ApplicationContext implements AutoCloseable {
    public final DataChangeBus changes=new DataChangeBus();
    private final AuditLog audit;
    public final Login login;
    public final AdminService admin;
    private final ClinicData store;
    private final clinic.persistence.Database database;
    public ApplicationContext(boolean demo) {
        this(demo,new AuditLog());
    }
    /** Explicit fixture injection for audit assertions; production uses the Path constructor. */
    public ApplicationContext(boolean demo, AuditLog audit) {
        database=null;
        this.audit=java.util.Objects.requireNonNull(audit);
        store=new InMemoryClinicSystem();
        login=demo?Login.withSampleUsers(audit):new Login(audit);
        admin=new AdminService(login, audit);
        if (demo) seedSampleCampuses();
        connectChanges();
    }
    /** Normal launch uses this durable composition; boolean constructor is an isolated fixture. */
    public ApplicationContext(java.nio.file.Path databaseFile) {
        database=new clinic.persistence.Database(databaseFile);
        try {
            var repository=new clinic.persistence.AdminRepository(database);
            audit=new AuditLog(repository);
            login=new Login(audit,new clinic.persistence.AccountRepository(database));
            admin=new AdminService(login,audit,repository);
            store=clinic.persistence.ClinicRepository.create(database);
            connectChanges();
        } catch (RuntimeException failure) {
            database.close();
            throw failure;
        }
    }
    public static ApplicationContext production() {
        return new ApplicationContext(java.nio.file.Path.of(System.getProperty("clinic.database", "data/scms")));
    }
    private void connectChanges() {
        login.onSessionChanged(changes::clearSession);
        audit.onRecorded(()->changes.publish(DataTopic.AUDIT));
        admin.onChanged(action->changes.publish(action.equals("users")?DataTopic.USERS:DataTopic.CAMPUSES));
    }
    public boolean isPersistent() { return database!=null; }
    public String storageDescription() {
        return isPersistent()?"Changes are saved to the local database.":"Demo/test data lasts only for this session.";
    }
    public String databaseLocation() { return isPersistent()?database.location():"In-memory fixture"; }
    @Override public void close() {
        synchronized(login) { login.logout(); if(database!=null)database.close(); }
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
    public String campusLabel() {
        User user=current();
        if(User.ADMIN.equals(user.getRole()))return "Administration";
        String id=user.getCampusId();
        if(id==null)return "Campus unassigned";
        String name=admin.campusName(id);
        return name==null?"Unknown campus · "+id:name;
    }
    /** Captures one nurse session; checks do not hold the login lock during network I/O. */
    public Runnable nurseSessionGuard() {
        synchronized (login) {
            User owner = current();
            if (!(owner instanceof SchoolNurse)) throw new SecurityException("Nurse access required.");
            long version = login.getSessionVersion();
            return () -> {
                synchronized (login) {
                    if (login.getSessionVersion() != version || current() != owner)
                        throw new SecurityException("Session expired. Sign in again.");
                }
            };
        }
    }
    public void require(String feature) {
        requireAny(feature);
    }
    private void requireAny(String... features) {
        User user = current();
        boolean allowed = java.util.Arrays.stream(features).anyMatch(user::canAccess);
        audit.record(java.time.LocalDateTime.now(), user.getUserId(), user.getName(),
            user.getRole(), "clinicQuery", "Read permission check", allowed,user.getCampusId());
        if (!allowed) throw new SecurityException("Access denied.");
        if(!User.ADMIN.equals(user.getRole())&&user.getCampusId()==null)
            throw new SecurityException("Your account has no assigned campus. Ask an administrator to assign one.");
    }
    public ClinicSystem clinic() { return ClinicSession.open(login,store,audit,changes); }
    public PageResult<Patient> patients(int page, int size, String q) {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            return store.patientPage(page, size, q);
        }
    }
    public java.util.function.Consumer<java.util.Map<String,String>> restockSession(String medicine) {
        return ClinicSession.restock(login,store,audit,changes,medicine);
    }
    public java.util.function.Consumer<java.util.Map<String,String>> inventoryBatchSession(String medicine,boolean restock,boolean merge) {
        return ClinicSession.inventoryBatch(login,store,audit,changes,medicine,restock,merge);
    }
    public java.util.function.Consumer<java.util.Map<String,String>> inventoryBatchSession(MedicineProduct medicine,boolean restock,boolean merge) {
        return ClinicSession.inventoryBatch(login,store,audit,changes,medicine,restock,merge);
    }
    public java.util.function.Consumer<java.util.Map<String,String>> refillBatchSession(String batchId,String medicine) {
        return ClinicSession.refillBatch(login,store,audit,changes,batchId,medicine);
    }
    public java.util.function.Consumer<BatchMatcher.Group> mergeDuplicateSession() {
        return ClinicSession.mergeDuplicates(login,store,audit,changes);
    }
    public List<Medicine> matchingMedicineBatches(String name,java.time.LocalDate expiry) {
        synchronized(login){require(User.VIEW_STOCK);return store.matchingMedicineBatches(name,expiry);}
    }
    public List<Medicine> matchingMedicineBatches(MedicineProduct product,java.time.LocalDate expiry) {
        synchronized(login){require(User.VIEW_STOCK);return store.matchingMedicineBatches(product,expiry);}
    }
    public PageResult<Medicine> medicines(int page, int size, String q) {
        synchronized (login) {
            require(User.VIEW_STOCK);
            return store.medicinePage(page, size, q);
        }
    }
    public PageResult<Medicine> dispensableMedicines(int page,int size,String query) {
        synchronized(login){require(User.DISPENSE);return store.dispensableMedicinePage(page,size,query);}
    }
    public PageResult<MedicineTransaction> transactions(int page,int size,String query,
            java.time.LocalDateTime from,java.time.LocalDateTime to) {
        synchronized(login){require(User.DISPENSE);return store.transactionPage(page,size,query,from,to);}
    }
    public DispenseWorkflow dispenseSession() {
        synchronized(login){require(User.DISPENSE);return new DispenseWorkflow(clinic(),current(),audit);}
    }
    public PageResult<MedicalRecord> records(int page, int size, String q) {
        synchronized (login) {
            requireAny(User.RECORDS, User.VIEW_REPORTS);
            return store.recordPage(page, size, q);
        }
    }
    public PageResult<MedicalRecord> recentVisits(int page, int size, String query) {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            User user = current();
            return store.recentRecordPage(page, size, query,
                user.canAccess(User.RECORDS) ? null : user.getUserId());
        }
    }
    public PageResult<MedicalRecord> recentVisits(int page, int size, String nameOrId,
            java.time.LocalDateTime fromInclusive, java.time.LocalDateTime toInclusive) {
        synchronized (login) {
            User user = current();
            boolean allowed = user.canAccess(User.RECORD_TRANSACTION);
            audit.record(java.time.LocalDateTime.now(), user.getUserId(), user.getName(), user.getRole(),
                "visitSearch", "visit search performed", allowed,user.getCampusId());
            if (!allowed) throw new SecurityException("Access denied.");
            if(user.getCampusId()==null)
                throw new SecurityException("Your account has no assigned campus. Ask an administrator to assign one.");
            return store.recentRecordPage(page, size, nameOrId,
                user.canAccess(User.RECORDS) ? null : user.getUserId(), fromInclusive, toInclusive);
        }
    }
    public PageResult<NotificationLog> notifications(int page, int size, String q) {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            return store.notificationPage(page, size, q);
        }
    }
    public PageResult<Alert> alerts(int page, int size, String q) {
        synchronized (login) {
            require(User.RECORDS);
            return store.alertPage(page, size, q);
        }
    }
    public int patientCount() {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            return store.patientCount();
        }
    }
    public int visitCount() {
        synchronized (login) {
            require(User.RECORD_TRANSACTION);
            return store.recordCount();
        }
    }
}
