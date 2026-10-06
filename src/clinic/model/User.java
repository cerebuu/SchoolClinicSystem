package clinic.model;

// This class is responsible for an authorized system user and their access role.
public class User {

    // Role names
    public static final String NURSE = "NURSE";
    public static final String ASSISTANT = "ASSISTANT";
    public static final String ADMIN = "ADMIN";
    public static final String DOCTOR = "DOCTOR";

    // Feature names passed to canAccess()
    public static final String DISPENSE = "dispense";
    public static final String RECORD_TRANSACTION = "record_transaction";
    public static final String INVENTORY = "inventory";
    public static final String VIEW_STOCK = "view_stock";
    public static final String RESTOCK = "restock";
    public static final String RECORDS = "records";
    public static final String REPORTS = "reports";
    public static final String VIEW_REPORTS = "view_reports";
    public static final String USER_MANAGEMENT = "user_management";
    public static final String AUDIT_LOG = "audit_log";

    private String userId;
    private String name;
    private String role;

    public User(String userId, String name, String role) {
        this.userId = userId;
        this.name = name;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    // Base users have no access. Each subclass overrides this with its own permissions.
    public boolean canAccess(String feature) {
        return false;
    }

    // Helper for subclasses: true if feature matches any allowed name.
    protected static boolean isOneOf(String feature, String... allowed) {
        if (feature == null) {
            return false;
        }
        String f = feature.trim();
        for (String a : allowed) {
            if (a.equalsIgnoreCase(f)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return name + " (" + role + ", ID " + userId + ")";
    }
}
