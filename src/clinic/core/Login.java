package clinic.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import clinic.model.*;
import clinic.service.AuditLog;

// This class is responsible for checking user IDs and passwords and tracking who is logged in.
public class Login {

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCK_MILLIS = 60_000L;
    private final Map<String, User> users = new HashMap<>();
    private final Map<String, String> passwords = new HashMap<>();
    private final Map<String, Integer> failedAttempts = new HashMap<>();
    private final Map<String, Long> lockedUntil = new HashMap<>();
    private final AuditLog auditLog;
    private final Supplier<Long> clock;
    private final SecureRandom random = new SecureRandom();
    private User currentUser;

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
        if (user == null || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("User and password are required.");
        }
        if (users.containsKey(user.getUserId())) {
            throw new IllegalArgumentException("User ID already exists: " + user.getUserId());
        }
        String userId = user.getUserId();
        users.put(userId, user);
        passwords.put(userId, hashPassword(password));
    }

    /** Returns the user if the ID and password match, otherwise empty. */
    public synchronized Optional<User> login(String userId, String password) {
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
        if (user == null || !matches(password, passwords.get(normalizedId))) {
            int attempts = failedAttempts.getOrDefault(normalizedId, 0) + 1;
            failedAttempts.put(normalizedId, attempts);
            record(normalizedId, user, "FAILURE");
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                lockedUntil.put(normalizedId, now + LOCK_MILLIS);
                record(normalizedId, user, "LOCKOUT");
            }
            return Optional.empty();
        }
        failedAttempts.remove(normalizedId);
        lockedUntil.remove(normalizedId);
        currentUser = user;
        record(normalizedId, user, "SUCCESS");
        return Optional.of(user);
    }

    public synchronized void logout() {
        currentUser = null;
    }

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
        login.addUser(new SchoolNurse("N001", "Nurse Ann"), "nurse123");
        login.addUser(new NurseAssistant("A001", "Assistant Bo"), "assist123");
        login.addUser(new Administrator("AD001", "Admin Cy"), "admin123");
        login.addUser(new Doctor("D001", "Dr. Di", "Pediatrics"), "doctor123");
        return login;
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = digest(salt, password);
        return encode(salt) + ":" + encode(hash);
    }

    private boolean matches(String password, String stored) {
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split(":", -1);
        if (parts.length != 2) {
            return false;
        }
        byte[] salt = decode(parts[0]);
        byte[] expected = decode(parts[1]);
        byte[] actual = digest(salt, password);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] digest(byte[] salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String encode(byte[] value) {
        return java.util.Base64.getEncoder().encodeToString(value);
    }

    private byte[] decode(String value) {
        try {
            return java.util.Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
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
                "login", result, "SUCCESS".equals(result));
    }
}
