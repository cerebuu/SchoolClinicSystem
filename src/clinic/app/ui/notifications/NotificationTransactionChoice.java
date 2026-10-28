package clinic.app.ui.notifications;

import clinic.model.MedicineTransaction;
import clinic.util.DateUtil;

final class NotificationTransactionChoice {
    private final MedicineTransaction transaction;
    NotificationTransactionChoice(MedicineTransaction transaction) { this.transaction=transaction; }
    static NotificationTransactionChoice none() { return new NotificationTransactionChoice(null); }
    MedicineTransaction transaction() { return transaction; }
    @Override public String toString() {
        return transaction==null?"None":DateUtil.format(transaction.getDateTime())+" · "+
            transaction.getMedicineDisplayName()+" × "+transaction.getQuantity();
    }
}
