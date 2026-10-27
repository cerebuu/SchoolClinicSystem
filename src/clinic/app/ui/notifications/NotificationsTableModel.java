package clinic.app.ui.notifications;
import clinic.model.*;
import clinic.app.ui.common.PagedTableModel;
public final class NotificationsTableModel extends PagedTableModel<NotificationLog> {
    public NotificationsTableModel() {
        super("ID", "Patient ID", "Transaction ID", "Reason", "Date",
            "Channel", "Status", "Last attempt", "Attempts");
    }
    protected Object value(NotificationLog r, int c) {
        return switch(c) {
            case 0 -> r.getNotificationId();
            case 1 -> r.getPatientId();
            case 2 -> r.getTransactionId();
            case 3 -> r.getReason();
            case 4 -> r.getDateTime();
            case 5 -> r.getChannel()==null?"-":r.getChannel();
            case 6 -> r.getStatus();
            case 7 -> r.getLastAttemptTime()==null?"-":r.getLastAttemptTime();
            case 8 -> r.getAttemptCount();
            default -> "";
        };
    }
}
