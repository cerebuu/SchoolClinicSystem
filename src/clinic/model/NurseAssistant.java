package clinic.model;

// This class is responsible for the nurse assistant with limited permissions.
public class NurseAssistant extends User {

    public NurseAssistant(String userId, String name) {
        super(userId, name, ASSISTANT);
    }

    // Only recording transactions and viewing stock.
    // Restocking, reports, and user management are denied.
    @Override
    public boolean canAccess(String feature) {
        return isOneOf(feature, RECORD_TRANSACTION, DISPENSE, VIEW_STOCK);
    }
}
