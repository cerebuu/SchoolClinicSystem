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
    public static final String SEND_NOTIFICATIONS = "send_notifications";
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
    private String campusId;
    private boolean campusAssignmentLocked;

    public User(String userId, String name, String role) {
        this.userId = clinic.util.Validation.required(userId, "User ID");
        this.name = clinic.util.Validation.required(name, "Name");
        this.role = clinic.util.Validation.required(role, "Role");
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
    public String getCampusId() { return campusId; }
    public void setCampusId(String value) {
        if(campusAssignmentLocked)
            throw new IllegalStateException("Change account campus assignments through user management.");
        campusId=value==null||value.isBlank()?null:clinic.util.Validation.required(value,"Campus ID");
    }

    /** A service-owned identity whose assignment cannot be changed through a returned reference. */
    public final User immutableCopy() {
        User copy;
        if(getClass()==Administrator.class)copy=new Administrator(userId,name);
        else if(getClass()==SchoolNurse.class)copy=new SchoolNurse(userId,name);
        else if(getClass()==NurseAssistant.class)copy=new NurseAssistant(userId,name);
        else if(getClass()==Doctor.class)copy=new Doctor(userId,name,((Doctor)this).getSpecialty());
        else copy=new User(userId,name,role);
        copy.campusId=campusId;
        copy.campusAssignmentLocked=true;
        return copy;
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
        return name + " (" + role + ", ID " + userId
            + (campusId==null?", campus unassigned":", campus "+campusId)+")";
    }
}
