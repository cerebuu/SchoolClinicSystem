package clinic.model;

// This class is responsible for the nurse user who runs clinic transactions.
public class SchoolNurse extends User {

    public SchoolNurse(String userId, String name) {
        super(userId, name, NURSE);
    }

    // The nurse can use every clinic feature, but not account management or the audit log.
    @Override
    public boolean canAccess(String feature) {
        return isOneOf(feature, DISPENSE, RECORD_TRANSACTION, INVENTORY,
                VIEW_STOCK, RESTOCK, RECORDS, REPORTS);
    }
}
