package clinic.app.ui.notifications;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.*;
import java.awt.BorderLayout;
import java.util.List;
public final class NotificationsPanel extends BasePanel {
    public NotificationsPanel(ApplicationContext app) {
        super("Guardian notifications", clinic.app.Theme.SAMPLE_CAMPUS);
        PagedTablePanel<NotificationLog> table=new PagedTablePanel<>(new NotificationsTableModel(), app::notifications);
        add(table, BorderLayout.CENTER);
        action("Create notification", true, ()->FormPopup.show(this, "Create guardian notification", List.of(FormField.text("Patient ID"), FormField.text("Transaction ID"), FormField.text("Reason")), v->app.clinic().createGuardianNotification(v.get("Patient ID"), v.get("Transaction ID"), v.get("Reason")), table::reload));
        action("View message", ()-> {
            NotificationLog n=table.selected();
            if(n==null) {
                MessagePopup.warning(this, "Select a notification.");
                return;
            }
            MessagePopup.info(this, n.toMessage());
        });
        action("Mark sent", ()-> {
            NotificationLog n=table.selected();
            if(n==null) {
                MessagePopup.warning(this, "Select a notification.");
                return;
            }
            if(MessagePopup.confirm(this, "Confirm this message was sent outside the application?"))Actions.run(this, ()-> {
                app.clinic().updateNotificationStatus(n.getNotificationId(), NotificationStatus.SENT);
                return true;
            }, ok->table.reload());
        });
    }
}
