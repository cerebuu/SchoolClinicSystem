package clinic.service;

import java.time.LocalDateTime;

import clinic.util.DateUtil;

// This class is responsible for one immutable clinic audit event.
public final class AuditEntry {

    private final LocalDateTime timestamp;
    private final String userId;
    private final String userName;
    private final String role;
    private final String action;
    private final String details;
    private final boolean allowed;

    public AuditEntry(LocalDateTime timestamp, String userId, String userName,
                      String role, String action, String details, boolean allowed) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.userName = userName;
        this.role = role;
        this.action = action;
        this.details = details;
        this.allowed = allowed;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getRole() {
        return role;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public boolean isAllowed() {
        return allowed;
    }

    @Override
    public String toString() {
        String result = DateUtil.format(timestamp) + " - " + userName
                + " (" + role + ", " + userId + ") - " + action
                + " - " + details + " - "
                + (allowed ? "ALLOWED" : "DENIED");
        return result;
    }
}
