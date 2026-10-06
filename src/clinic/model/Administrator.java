package clinic.model;

// This class is responsible for the administrator who manages accounts and access.
public class Administrator extends User {

    public Administrator(String userId, String name) {
        super(userId, name, ADMIN);
    }

    // User management and the audit log only. Dispensing medicine is not allowed.
    @Override
    public boolean canAccess(String feature) {
        return isOneOf(feature, USER_MANAGEMENT, AUDIT_LOG);
    }
}
