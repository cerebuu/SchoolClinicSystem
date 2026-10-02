package clinic.model;

// This class is responsible for the nurse assistant with limited permissions.
//
// Role is ASSISTANT. Constructor: NurseAssistant(String userId, String name) and call super(...).
// Override canAccess() to allow only recording transactions and viewing stock.
// It must deny restocking, reports, and user management.
public class NurseAssistant extends User {
}
