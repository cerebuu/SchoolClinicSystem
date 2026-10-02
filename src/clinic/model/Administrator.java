package clinic.model;

// This class is responsible for the administrator who manages accounts and access.
//
// Role is ADMIN. Constructor: Administrator(String userId, String name) and call super(...).
// Override canAccess() to allow user management and viewing the audit log.
// It must not allow dispensing medicine.
public class Administrator extends User {
}
