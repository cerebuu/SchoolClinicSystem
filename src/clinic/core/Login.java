package clinic.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import clinic.model.*;
import clinic.service.AuditLog;

// This class is responsible for checking user IDs and passwords and tracking who is logged in.
public class Login {

    public static final int MIN_PASSWORD_CODE_POINTS=8;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCK_MILLIS = 60_000L;
    private final Map<String, User> users = new HashMap<>();
    private final Map<String, String> passwords = new HashMap<>();
    private final Map<String, Integer> failedAttempts = new HashMap<>();
    private final Map<String, Long> lockedUntil = new HashMap<>();
    private final AuditLog auditLog;
    private final Supplier<Long> clock;
    private User currentUser;
    private long sessionVersion;
    private Runnable sessionChanged=()->{};
    public synchronized void onSessionChanged(Runnable listener){sessionChanged=listener;}
    private final java.util.Set<String> inactive = new java.util.HashSet<>();
    private clinic.persistence.AccountRepository repository;
    public Login(AuditLog audit, clinic.persistence.AccountRepository repository) {
        this(audit, System::currentTimeMillis);
        this.repository = repository;
        for (var account : repository.load()) {
            users.put(account.user().getUserId(), account.user().immutableCopy());
            passwords.put(account.user().getUserId(), account.hash());
            if (!account.active()) inactive.add(account.user().getUserId());
            if(account.failures()>0) failedAttempts.put(account.user().getUserId(),account.failures());
            if(account.lockedUntil()>0) lockedUntil.put(account.user().getUserId(),account.lockedUntil());
        }
    }

    public Login() {
        this(null, System::currentTimeMillis);
    }

    public Login(AuditLog auditLog) {
        this(auditLog, System::currentTimeMillis);
    }

    public Login(Supplier<Long> clock) {
        this(null, clock);
    }

    public Login(AuditLog auditLog, Supplier<Long> clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock is required.");
        }
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** Register a user with a password. Throws if the ID is already taken. */
    public synchronized void addUser(User user, String password) {
        addUser(user,password,auditLog,()->{});
    }

    /** Stage the account cache until the durable write and its completion audit both commit. */
    public synchronized void addUser(User user,String password,AuditLog transactionAudit,Runnable completed) {
        java.util.Objects.requireNonNull(completed);
        if (user == null || password == null || password.isBlank()) {
            throw new IllegalArgumentException("User and password are required.");
        }
        if(!passwordMeetsMinimum(password))
            throw new IllegalArgumentException("Password must contain at least "+MIN_PASSWORD_CODE_POINTS+" characters.");
        if (!users.isEmpty()) requireAdmin();
        else if (!(user instanceof Administrator)) throw new SecurityException("Create an administrator first.");
        if (users.containsKey(user.getUserId())) {
            throw new IllegalArgumentException("User ID already exists: " + user.getUserId());
        }
        clinic.util.Validation.required(user.getUserId(), "User ID");
        clinic.util.Validation.required(user.getName(), "Name");
        validateNurseCampus(user);
        String userId = user.getUserId();
        String hashed = PasswordHasher.hash(password);
        User stored=user.immutableCopy();
        commitAccount(transactionAudit,()->{
            if (repository != null) repository.insert(stored, hashed);
            completed.run();
        });
        users.put(userId, stored);
        passwords.put(userId, hashed);
    }

    /** Returns the user if the ID and password match, otherwise empty. */
    public synchronized Optional<User> login(String userId, String password) {
        sessionChanged.run();
        // A failed reauthentication must never retain a previous privileged session.
        currentUser = null;
        sessionVersion++;
        if (userId == null || password == null) {
            record(null, null, "FAILURE");
            return Optional.empty();
        }
        String normalizedId = userId.trim();
        User user = users.get(normalizedId);
        long now = clock.get();
        Long lockEnd = lockedUntil.get(normalizedId);
        if (lockEnd != null && now < lockEnd) {
            record(normalizedId, user, "LOCKOUT");
            return Optional.empty();
        }
        if (lockEnd != null) {
            lockedUntil.remove(normalizedId);
            failedAttempts.remove(normalizedId);
        }
        if (user == null || inactive.contains(normalizedId) || !PasswordHasher.matches(password, passwords.get(normalizedId))) {
            int attempts = failedAttempts.getOrDefault(normalizedId, 0) + 1;
            if(repository!=null && user!=null) repository.attempts(normalizedId, attempts, attempts>=MAX_FAILED_ATTEMPTS?now+LOCK_MILLIS:0);
            failedAttempts.put(normalizedId, attempts);
            record(normalizedId, user, "FAILURE");
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                lockedUntil.put(normalizedId, now + LOCK_MILLIS);
                record(normalizedId, user, "LOCKOUT");
            }
            return Optional.empty();
        }
        if(repository!=null) repository.attempts(normalizedId,0,0);
        failedAttempts.remove(normalizedId);
        lockedUntil.remove(normalizedId);
        record(normalizedId, user, "SUCCESS");
        currentUser = user;
        sessionVersion++;
        return Optional.of(user);
    }

    public synchronized void logout() {
        User endingSession=currentUser;
        sessionChanged.run();
        currentUser = null;
        sessionVersion++;
        if(endingSession!=null&&auditLog!=null) {
            auditLog.record(java.time.LocalDateTime.now(),endingSession.getUserId(),endingSession.getName(),
                endingSession.getRole(),"logout","SESSION_ENDED",true,endingSession.getCampusId());
        }
    }

    public synchronized long getSessionVersion() { return sessionVersion; }

    public synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public synchronized Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    /** Test accounts for development. Replace or extend in SampleData later. */
    public static Login withSampleUsers() {
        return withSampleUsers(null);
    }

    public static Login withSampleUsers(AuditLog auditLog) {
        Login login = new Login(auditLog);
        login.addUser(new Administrator("AD001", "Admin Cy"), "admin123");
        login.login("AD001", "admin123");
        SchoolNurse nurse=new SchoolNurse("N001","Nurse Ann");
        nurse.setCampusId("uic-bajada");
        login.addUser(nurse,"nurse123");
        NurseAssistant assistant=new NurseAssistant("A001","Assistant Bo");
        assistant.setCampusId("C1");
        login.addUser(assistant,"assist123");
        Doctor doctor=new Doctor("D001","Dr. Di","Pediatrics");
        doctor.setCampusId("C1");
        login.addUser(doctor,"doctor123");
        login.logout();
        return login;
    }

    public synchronized boolean needsSetup() { return users.isEmpty(); }
    public synchronized void bootstrapAdministrator(String id, String name, String password) {
        if (!users.isEmpty()) throw new SecurityException("Setup is already complete.");
        addUser(new Administrator(id, name), password);
    }
    public synchronized clinic.service.PageResult<User> userPage(int page, int size, String filter) {
        requireAdmin();
        String query = filter.toLowerCase(java.util.Locale.ROOT);
        return clinic.service.PageResult.select(users.values(), page, size,
            u -> u.getUserId().toLowerCase(java.util.Locale.ROOT).contains(query)
                || u.getName().toLowerCase(java.util.Locale.ROOT).contains(query));
    }
    public synchronized boolean isActive(String id) { requireAdmin(); return !inactive.contains(id); }
    /** Read an account for an authorized administrator workflow. Returned users are immutable snapshots. */
    public synchronized Optional<User> findUser(String id) {
        requireAdmin();
        return Optional.ofNullable(users.get(id));
    }
    public synchronized void updateUser(User replacement, boolean active) {
        updateUser(replacement,active,auditLog,()->{});
    }

    /** Completion runs inside the durable unit; the cached identity changes only after commit. */
    public synchronized void updateUser(User replacement,boolean active,AuditLog transactionAudit,Runnable completed) {
        java.util.Objects.requireNonNull(completed);
        requireAdmin();
        if (!users.containsKey(replacement.getUserId())) throw new IllegalArgumentException("User not found.");
        if (currentUser.getUserId().equals(replacement.getUserId()))
            throw new IllegalArgumentException("Use another administrator to change your account.");
        validateNurseCampus(replacement);
        User stored=replacement.immutableCopy();
        commitAccount(transactionAudit,()->{
            if (repository != null) repository.update(stored, active);
            completed.run();
        });
        users.put(replacement.getUserId(), stored);
        if (active) inactive.remove(replacement.getUserId()); else inactive.add(replacement.getUserId());
    }
    private static void validateNurseCampus(User user) {
        if(User.NURSE.equals(user.getRole())&&NurseCampus.forId(user.getCampusId()).isEmpty())
            throw new IllegalArgumentException("Assign the nurse to UIC - Bajada, UIC - Annex, or UIC - Main.");
    }
    public static boolean passwordMeetsMinimum(String password) {
        return password!=null&&password.codePointCount(0,password.length())>=MIN_PASSWORD_CODE_POINTS;
    }
    private void commitAccount(AuditLog transactionAudit,Runnable work) {
        Supplier<Void> mutation=()->{work.run();return null;};
        // Keep audit-before-database lock order, also used by audit reads and clinical mutations.
        if(transactionAudit!=null)transactionAudit.unitOfWork(mutation);
        else if(repository!=null)repository.unitOfWork(mutation);
        else work.run();
    }
    private void requireAdmin() {
        if (currentUser == null || !User.ADMIN.equals(currentUser.getRole()))
            throw new SecurityException("Administrator session required.");
    }
    private void record(String userId, User user, String result) {
        if (auditLog == null) {
            return;
        }
        boolean knownUser = user != null && userId != null;
        String auditUserId = knownUser ? userId : "unknown";
        String name = knownUser ? user.getName() : "unknown";
        String role = knownUser ? user.getRole() : "unknown";
        auditLog.record(java.time.LocalDateTime.now(), auditUserId, name, role,
                "login", result, "SUCCESS".equals(result),knownUser?user.getCampusId():null);
    }
}
