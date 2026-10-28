package clinic.app.ui.notifications;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.Actions;
import clinic.app.ui.common.PatientLookupField;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.app.ui.common.popup.PopupDialog;
import clinic.model.MedicineTransaction;
import clinic.model.Patient;
import clinic.model.User;
import clinic.service.GuardianMessageTemplates;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.*;

/** Patient-safe creation form with asynchronous profile and transaction lookup. */
public final class CreateNotificationPopup {
    private final ApplicationContext app;
    private final Runnable saved;
    private final User sessionUser;
    private final long sessionVersion;
    private final JDialog dialog;
    private final PatientLookupField patientField;
    private final JComboBox<GuardianMessageTemplates.MessageType> typeBox=new JComboBox<>(GuardianMessageTemplates.MessageType.values());
    private final JComboBox<NotificationTransactionChoice> transactionBox=new JComboBox<>();
    private final JTextField transactionId=new JTextField(28);
    private final JTextArea preview=new JTextArea(3,32);
    private final JLabel transactionStatus=new JLabel("Look up a patient to load transactions.");
    private final JButton saveButton=Theme.button("Save",true),cancelButton=Theme.button("Cancel",false);
    private SwingWorker<List<MedicineTransaction>,Void> transactionWorker;
    private SwingWorker<clinic.model.NotificationLog,Void> saveWorker;
    private int transactionGeneration;
    private boolean saving,closed;
    private String transactionMessage="Look up a patient to load transactions.";
    private java.awt.Color transactionColor=Theme.MUTED;

    public static void open(java.awt.Component owner,ApplicationContext app,Runnable saved) {
        CreateNotificationPopup popup=new CreateNotificationPopup(owner,app,saved);
        PopupDialog.show(owner,popup.dialog);
    }

    private CreateNotificationPopup(java.awt.Component owner,ApplicationContext app,Runnable saved) {
        this.app=app;this.saved=saved;
        synchronized(app.login) { sessionUser=app.current();sessionVersion=app.login.getSessionVersion(); }
        dialog=PopupDialog.create(owner,NotificationsPanel.CREATE_POPUP_TITLE);
        patientField=new PatientLookupField(app,PatientLookupField.Mode.EXISTING_WITH_CONTACT,"Patient ID");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        build();
        patientField.addPropertyChangeListener(PatientLookupField.PATIENT_PROPERTY,event->
            loadTransactions((Patient)event.getNewValue()));
        patientField.addPropertyChangeListener(clinic.app.ui.common.popup.FormValueComponent.READY_PROPERTY,event->refreshState());
        typeBox.addActionListener(event->refreshState());
        transactionBox.addActionListener(event->refreshState());
        saveButton.addActionListener(event->save());
        cancelButton.addActionListener(event->cancel());
        dialog.getRootPane().registerKeyboardAction(event->cancel(),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { closeWorkers(); }
        });
        refreshState();
    }

    private void build() {
        JPanel content=new JPanel();content.setBackground(Theme.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4,Theme.SPACE_6,Theme.SPACE_4,Theme.SPACE_6));
        content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        addField(content,"Patient ID",patientField);
        typeBox.setName("Notification type");Theme.field(typeBox);typeBox.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list,Object value,int index,
                    boolean selected,boolean focus) {
                return super.getListCellRendererComponent(list,value==null?"":
                    ((GuardianMessageTemplates.MessageType)value).displayName(),
                    index,selected,focus);
            }
        });
        addField(content,"Notification type",typeBox);
        preview.setEditable(false);preview.setLineWrap(true);preview.setWrapStyleWord(true);
        preview.setName("Message preview");preview.setBackground(Theme.SUBTLE);
        preview.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.PLAIN));
        addField(content,"Message preview",new JScrollPane(preview));
        transactionBox.setName("Notification transaction");Theme.field(transactionBox);
        transactionBox.addItem(NotificationTransactionChoice.none());
        transactionBox.setEnabled(false);
        addField(content,"Pick a transaction (optional)",transactionBox);
        transactionStatus.setForeground(Theme.MUTED);transactionStatus.setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.PLAIN));
        transactionStatus.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);content.add(transactionStatus);
        transactionId.setName("Transaction ID");transactionId.setEditable(false);Theme.field(transactionId);
        addField(content,"Transaction ID",transactionId);
        JScrollPane scroll=new JScrollPane(content);scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);scroll.setPreferredSize(new Dimension(560,470));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,Theme.SPACE_2,Theme.SPACE_2));
        buttons.setOpaque(false);buttons.add(cancelButton);buttons.add(saveButton);
        JPanel root=new JPanel(new BorderLayout());root.add(scroll,BorderLayout.CENTER);root.add(buttons,BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setMinimumSize(new Dimension(460,360));
    }

    private void loadTransactions(Patient patient) {
        int request=++transactionGeneration;
        if(transactionWorker!=null)transactionWorker.cancel(true);
        transactionBox.removeAllItems();transactionBox.addItem(NotificationTransactionChoice.none());
        transactionId.setText("");transactionBox.setEnabled(patient!=null);
        if(patient==null) {
            transactionMessage="Look up a patient to load transactions.";transactionColor=Theme.MUTED;
            refreshState();return;
        }
        transactionMessage="Loading recent transactions…";transactionColor=Theme.MUTED;refreshState();
        String patientId=patient.getId();
        transactionWorker=new SwingWorker<>() {
            @Override protected List<MedicineTransaction> doInBackground() {
                return NotificationTransactions.latestForPatient(app,patientId);
            }
            @Override protected void done() {
                if(isCancelled()||closed||request!=transactionGeneration||!sessionCurrent())return;
                try {
                    List<MedicineTransaction> transactions=get();
                    transactionBox.removeAllItems();transactionBox.addItem(NotificationTransactionChoice.none());
                    transactions.forEach(tx->transactionBox.addItem(new NotificationTransactionChoice(tx)));
                    transactionBox.setSelectedIndex(0);transactionBox.setEnabled(true);
                    transactionMessage=transactions.isEmpty()?"No transactions found for this patient.":"Newest transactions first.";
                    transactionColor=Theme.MUTED;refreshState();
                } catch(Exception failure) {
                    transactionBox.setEnabled(true);transactionMessage="Transactions could not be loaded. Retry the patient lookup.";
                    transactionColor=Theme.DANGER;refreshState();
                }
            }
        };
        transactionWorker.execute();refreshState();
    }

    private void refreshState() {
        Patient patient=patientField.foundPatient().orElse(null);
        GuardianMessageTemplates.MessageType type=(GuardianMessageTemplates.MessageType)typeBox.getSelectedItem();
        NotificationTransactionChoice choice=(NotificationTransactionChoice)transactionBox.getSelectedItem();
        MedicineTransaction tx=choice==null?null:choice.transaction();
        transactionId.setText(tx==null?"":tx.getTransactionId());
        boolean medicine=type==GuardianMessageTemplates.MessageType.MEDICINE_GIVEN;
        if(medicine&&patient!=null&&tx==null) {
            transactionStatus.setText("Select a transaction for medicine given.");transactionStatus.setForeground(Theme.DANGER);
        } else {transactionStatus.setText(transactionMessage);transactionStatus.setForeground(transactionColor);}
        saveButton.setEnabled(!saving&&patientField.isFormReady()&&type!=null&&(!medicine||tx!=null));
        if(patient==null||type==null)preview.setText("Select a patient and notification type to preview the message.");
        else if(medicine&&tx==null)preview.setText("Select a transaction to preview the medicine message.");
        else preview.setText(clinic.service.GuardianMessageTemplates.create(type,patient.getName(),type.name(),
            tx==null?null:tx.getMedicineDisplayName(),LocalDateTime.now(),"the school clinic"));
        preview.setCaretPosition(0);
    }

    private void save() {
        if(saving||!saveButton.isEnabled())return;
        Patient patient=patientField.foundPatient().orElse(null);
        GuardianMessageTemplates.MessageType type=(GuardianMessageTemplates.MessageType)typeBox.getSelectedItem();
        NotificationTransactionChoice choice=(NotificationTransactionChoice)transactionBox.getSelectedItem();
        if(patient==null||type==null)return;
        String transactionId=choice==null||choice.transaction()==null?null:choice.transaction().getTransactionId();
        saving=true;saveButton.setEnabled(false);cancelButton.setEnabled(false);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        saveWorker=new SwingWorker<>() {
            @Override protected clinic.model.NotificationLog doInBackground() {
                if(!sessionCurrent())throw new SecurityException("Session expired. Sign in again.");
                return app.clinic().createGuardianNotification(patient.getId(),transactionId,type.name());
            }
            @Override protected void done() {
                if(closed)return;
                try {
                    get();
                    if(!sessionCurrent()){dialog.dispose();return;}
                    dialog.dispose();saved.run();
                } catch(Exception failure) {
                    saving=false;cancelButton.setEnabled(true);dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    refreshState();MessagePopup.error(dialog,NotificationTransactions.cause(failure));
                }
            }
        };
        saveWorker.execute();
    }

    private void cancel() { if(!saving&&!closed)dialog.dispose(); }
    private boolean sessionCurrent() { return Actions.isCurrentSession(app.login,sessionVersion,sessionUser); }
    private void closeWorkers() {
        closed=true;patientField.close();transactionGeneration++;
        if(transactionWorker!=null)transactionWorker.cancel(true);
        if(saveWorker!=null&&!saveWorker.isDone())saveWorker.cancel(true);
    }
    private static void addField(JPanel parent,String label,JComponent component) {
        JLabel title=new JLabel(label);title.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.BOLD));title.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        component.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE,component.getPreferredSize().height));
        parent.add(title);parent.add(Box.createVerticalStrut(Theme.SPACE_1));parent.add(component);parent.add(Box.createVerticalStrut(Theme.SPACE_3));
    }
}
