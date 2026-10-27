package clinic.app.ui.notifications;
import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.*;
import clinic.notify.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
public final class NotificationsPanel extends BasePanel {
    public static final String CREATE_POPUP_TITLE="Create notification";
    public static final String EMERGENCY_POPUP_TITLE="Create emergency notification";
    public static final String SEND_POPUP_TITLE="Send notification";
    public static List<String> popupTitles() {
        return List.of(CREATE_POPUP_TITLE,EMERGENCY_POPUP_TITLE,SEND_POPUP_TITLE,"School clinic");
    }
    private final ApplicationContext app;
    private final NotifierService notifier;
    private final PagedTablePanel<NotificationLog> table;
    public NotificationsPanel(ApplicationContext app) {
        super("Notifications", app);
        this.app=app;
        notifier=NotifierService.fromDefault(app.clinic());
        table=new PagedTablePanel<>(new NotificationsTableModel(), app::notifications);
        table.refreshOn(app.changes, clinic.app.DataTopic.NOTIFICATIONS);
        if(app.current().canAccess(User.RECORDS)) {
            PagedTablePanel<Alert> alerts=new PagedTablePanel<>(new AlertsTableModel(),app::alerts,"Alert / message");
            alerts.refreshOn(app.changes, clinic.app.DataTopic.ALERTS);
            JTabbedPane views=new JTabbedPane();views.addTab("Messages",table);views.addTab("Alerts",alerts);
            add(views,BorderLayout.CENTER);
        } else add(table,BorderLayout.CENTER);
        action("Create notification", true, ()->FormPopup.show(this, CREATE_POPUP_TITLE, List.of(FormField.text("Patient ID"), FormField.text("Transaction ID"), FormField.text("Reason")), v->app.clinic().createGuardianNotification(v.get("Patient ID"), v.get("Transaction ID"), v.get("Reason")), ()->{}));
        if(app.current().canAccess(User.SEND_NOTIFICATIONS))addSendActions();
        action("View message", ()-> {
            NotificationLog n=table.selected();
            if(n==null) {
                MessagePopup.warning(this, "Select a notification.");
                return;
            }
            MessagePopup.info(this, n.messageBody());
        });
        if(app.current().canAccess(User.SEND_NOTIFICATIONS))action("Mark sent (manual)", ()-> {
            NotificationLog n=table.selected();
            if(n==null) {
                MessagePopup.warning(this, "Select a notification.");
                return;
            }
            if(MessagePopup.confirm(this, "Confirm this notification was sent manually by phone call or another approved method?"))Actions.run(this, ()-> {
                app.clinic().updateNotificationStatus(n.getNotificationId(), NotificationStatus.SENT);
                return true;
            }, ok->{});
        });
    }

    private void addSendActions() {
        action("Send",true,()->selected().ifPresentOrElse(this::openSendPopup,
            ()->MessagePopup.warning(this,"Select a notification.")));
        JButton retry=action("Retry",()->selected().ifPresentOrElse(this::openSendPopup,
            ()->MessagePopup.warning(this,"Select a failed notification.")));
        retry.setEnabled(false);
        table.addSelectionListener(()->{
            NotificationLog row=table.selected();
            retry.setEnabled(row!=null&&row.getStatus()==NotificationStatus.FAILED);
        });
        action("Emergency notify",()->createEmergency());
    }

    private java.util.Optional<NotificationLog> selected() {
        return java.util.Optional.ofNullable(table.selected());
    }

    private void createEmergency() {
        AtomicReference<NotificationLog> created=new AtomicReference<>();
        FormPopup.show(this,EMERGENCY_POPUP_TITLE,List.of(FormField.text("Patient ID")),
            values->created.set(app.clinic().createEmergencyNotification(values.get("Patient ID"))),
            ()->{if(created.get()!=null)openSendPopup(created.get());});
    }

    private void openSendPopup(NotificationLog notification) {
        List<NotificationRecipient.Option> options=notifier.recipients(notification);
        if(options.isEmpty()) {
            String label=notification.getRecipientLabel()==null?"Saved contact":notification.getRecipientLabel();
            MessagePopup.error(this,new IllegalArgumentException(label+": no valid saved email or phone number."));
            return;
        }
        JDialog dialog=PopupDialog.create(this,SEND_POPUP_TITLE);
        JPanel content=new JPanel();
        content.setBackground(Theme.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4,Theme.SPACE_6,Theme.SPACE_4,Theme.SPACE_6));
        content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        JComboBox<Channel> channels=new JComboBox<>(options.stream().map(NotificationRecipient.Option::channel)
            .distinct().toArray(Channel[]::new));
        Channel previous=notification.getChannel();
        if(previous!=null)channels.setSelectedItem(previous);
        JLabel recipient=new JLabel();
        JTextArea preview=new JTextArea(6,48);
        preview.setEditable(false);preview.setLineWrap(true);preview.setWrapStyleWord(true);
        preview.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.PLAIN));
        preview.setBackground(Theme.SUBTLE);
        JLabel state=new JLabel(" ");
        state.setForeground(Theme.MUTED);
        Runnable update=()->{
            Channel selected=(Channel)channels.getSelectedItem();
            NotificationRecipient.Option option=options.stream().filter(item->item.channel()==selected).findFirst().orElseThrow();
            recipient.setText(notification.getRecipientLabel()+": "+option.maskedAddress());
            preview.setText(notifier.preview(notification,selected));
            preview.setCaretPosition(0);
        };
        channels.addActionListener(event->{
            update.run();
            state.setText((Channel)channels.getSelectedItem()==Channel.SMS
                ?"SMS is limited to 160 characters; omitted text is shown as an ellipsis.":" ");
        });
        update.run();
        addField(content,"Recipient",recipient);
        addField(content,"Channel",channels);
        addField(content,"Message preview",new JScrollPane(preview));
        JCheckBox emergencyConfirm=new JCheckBox("I confirm sending this emergency notification.");
        emergencyConfirm.setOpaque(false);
        boolean emergency=notification.getMessageType()==clinic.service.GuardianMessageTemplates.MessageType.EMERGENCY;
        emergencyConfirm.setVisible(emergency);
        content.add(emergencyConfirm);
        if((Channel)channels.getSelectedItem()==Channel.SMS)
            state.setText("SMS is limited to 160 characters; omitted text is shown as an ellipsis.");
        JProgressBar progress=new JProgressBar();
        progress.setIndeterminate(true);progress.setVisible(false);
        JButton cancel=Theme.button("Cancel",false),send=Theme.button("Send now",true);
        JPanel buttons=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,Theme.SPACE_2,0));
        buttons.setOpaque(false);buttons.add(cancel);buttons.add(send);
        content.add(state);content.add(progress);content.add(buttons);
        cancel.addActionListener(event->dialog.dispose());
        send.addActionListener(event->{
            if(emergency&&!emergencyConfirm.isSelected()) {
                MessagePopup.warning(dialog,"Confirm the emergency notification before sending.");
                return;
            }
            Channel selected=(Channel)channels.getSelectedItem();
            send.setEnabled(false);cancel.setEnabled(false);channels.setEnabled(false);
            state.setText("Sending...");progress.setVisible(true);
            new SwingWorker<SendResult,Void>() {
                @Override protected SendResult doInBackground(){return notifier.send(notification,selected);}
                @Override protected void done(){
                    dialog.dispose();
                    try {
                        SendResult result=get();
                        if(result.success())MessagePopup.info(NotificationsPanel.this,result.statusMessage());
                        else MessagePopup.error(NotificationsPanel.this,new IllegalStateException(result.statusMessage()));
                    } catch(InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        MessagePopup.error(NotificationsPanel.this,new IllegalStateException("Sending was interrupted."));
                    } catch(java.util.concurrent.ExecutionException failure) {
                        MessagePopup.error(NotificationsPanel.this,failure.getCause());
                    }
                }
            }.execute();
        });
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(send);
        content.setPreferredSize(new Dimension(560,420));
        PopupDialog.show(this,dialog);
    }

    private static void addField(JPanel parent,String label,JComponent component) {
        JLabel title=new JLabel(label);
        title.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.BOLD));
        title.setAlignmentX(LEFT_ALIGNMENT);
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE,component instanceof JScrollPane?160:Theme.FIELD_HEIGHT));
        parent.add(title);parent.add(Box.createVerticalStrut(Theme.SPACE_1));
        parent.add(component);parent.add(Box.createVerticalStrut(Theme.SPACE_2));
    }
}
