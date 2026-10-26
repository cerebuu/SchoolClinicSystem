package clinic.app.ui.notifications;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.*;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JTabbedPane;
public final class NotificationsPanel extends BasePanel {
    public NotificationsPanel(ApplicationContext app) {
        super("Guardian notifications", "Clinic records");
        PagedTablePanel<NotificationLog> table=new PagedTablePanel<>(new NotificationsTableModel(), app::notifications);
        table.refreshOn(app.changes, clinic.app.DataTopic.NOTIFICATIONS);
        if(app.current().canAccess(User.RECORDS)) {
            PagedTablePanel<Alert> alerts=new PagedTablePanel<>(new AlertsTableModel(),app::alerts,"Alert / message");
            alerts.refreshOn(app.changes,clinic.app.DataTopic.ALERTS);
            JTabbedPane views=new JTabbedPane();views.addTab("Notifications",table);views.addTab("Alerts",alerts);
            add(views,BorderLayout.CENTER);
        } else add(table,BorderLayout.CENTER);
        action("Create notification", true, ()->FormPopup.show(this, "Create guardian notification", List.of(FormField.text("Patient ID"), FormField.text("Transaction ID"), FormField.text("Reason")), v->app.clinic().createGuardianNotification(v.get("Patient ID"), v.get("Transaction ID"), v.get("Reason")), ()->{}));
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
            }, ok->{});
        });
    }
}
