package clinic.app.ui.notifications;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class NotificationsTableModel extends PagedTableModel<NotificationLog> {
    public NotificationsTableModel() {
        super("ID", "Patient ID", "Transaction ID", "Reason", "Date", "Status");
    }
    protected Object value(NotificationLog r, int c) {
        return switch(c) {
            case 0 -> r.getNotificationId();
            case 1 -> r.getPatientId();
            case 2 -> r.getTransactionId();
            case 3 -> r.getReason();
            case 4 -> r.getDateTime();
            case 5 -> r.getStatus();
            default -> "";
        };
    }
}
