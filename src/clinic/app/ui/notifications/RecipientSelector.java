package clinic.app.ui.notifications;

import clinic.app.Theme;
import clinic.model.Patient;
import clinic.notify.NotificationRecipient;
import clinic.notify.Recipient;
import clinic.notify.Channel;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Selects a saved recipient or validates a one-time entered contact. */
final class RecipientSelector extends JPanel {
    private record Choice(String label, Channel channel, Recipient recipient, String masked) {
        @Override public String toString() { return label+" · "+masked; }
    }

    private final JComboBox<Choice> savedContacts=new JComboBox<>();
    private final JTextField manualContact=new JTextField(28);
    private final JLabel savedContactStatus=new JLabel(" ");
    private final JLabel channel=new JLabel("Select or enter a recipient.");
    private final JLabel error=new JLabel(" ");
    private final Consumer<Boolean> changed;
    private boolean initialized;

    RecipientSelector(Consumer<Boolean> changed) {
        this.changed=changed;
        setOpaque(false);
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("Recipient");
        title.setFont(Theme.font(Theme.TYPE_BODY,Font.BOLD));
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(Theme.SPACE_1));
        Theme.field(savedContacts);
        savedContacts.setName("Saved recipient contacts");
        savedContacts.setAlignmentX(LEFT_ALIGNMENT);
        add(savedContacts);
        add(Box.createVerticalStrut(Theme.SPACE_2));
        savedContactStatus.setFont(Theme.font(Theme.TYPE_CAPTION,Font.PLAIN));
        savedContactStatus.setForeground(Theme.MUTED);
        savedContactStatus.setAlignmentX(LEFT_ALIGNMENT);
        add(savedContactStatus);
        add(Box.createVerticalStrut(Theme.SPACE_2));
        JLabel manualLabel=new JLabel("Add contact number or email");
        manualLabel.setFont(Theme.font(Theme.TYPE_BODY,Font.BOLD));
        manualLabel.setAlignmentX(LEFT_ALIGNMENT);
        manualLabel.setLabelFor(manualContact);
        add(manualLabel);
        add(Box.createVerticalStrut(Theme.SPACE_1));
        manualContact.setName("Contact number or email");
        manualContact.getAccessibleContext().setAccessibleName("Add contact number or email");
        Theme.field(manualContact);
        manualContact.setAlignmentX(LEFT_ALIGNMENT);
        add(manualContact);
        JLabel hint=new JLabel("Leave blank to use the selected saved contact.");
        hint.setFont(Theme.font(Theme.TYPE_CAPTION,Font.PLAIN));
        hint.setForeground(Theme.MUTED);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        add(Box.createVerticalStrut(Theme.SPACE_1));
        add(hint);
        channel.setFont(Theme.font(Theme.TYPE_CAPTION,Font.PLAIN));
        channel.setForeground(Theme.MUTED);
        channel.setAlignmentX(LEFT_ALIGNMENT);
        add(Box.createVerticalStrut(Theme.SPACE_1));
        add(channel);
        error.setFont(Theme.font(Theme.TYPE_CAPTION,Font.PLAIN));
        error.setForeground(Theme.DANGER);
        error.setAlignmentX(LEFT_ALIGNMENT);
        add(error);
        setMaximumSize(new Dimension(Integer.MAX_VALUE,getPreferredSize().height));
        savedContacts.addActionListener(event->refresh());
        manualContact.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event){refresh();}
            public void removeUpdate(DocumentEvent event){refresh();}
            public void changedUpdate(DocumentEvent event){refresh();}
        });
        refresh();
        initialized=true;
    }

    void setPatient(Patient patient) {
        savedContacts.removeAllItems();
        if(patient!=null) {
            addContacts(patient.getContactInfo(),patient instanceof clinic.model.Employee
                ?"Employee contact":"Patient contact");
            addContacts(patient.getGuardianContact(),"Guardian contact");
        }
        savedContacts.setEnabled(savedContacts.getItemCount()>0);
        savedContactStatus.setText(patient!=null&&savedContacts.getItemCount()==0
            ?"No saved contact. Enter a number or email below to continue.":" ");
        manualContact.setText("");
        refresh();
    }

    private void addContacts(String contact,String label) {
        for(NotificationRecipient.Option option:NotificationRecipient.optionsForContact(contact)) {
            Choice choice=new Choice(label,option.channel(),option.recipient(),option.maskedAddress());
            savedContacts.addItem(choice);
        }
    }

    boolean isValidRecipient() {
        String entered=manualContact.getText().trim();
        return entered.isEmpty()?savedContacts.getSelectedItem() instanceof Choice
            :NotificationRecipient.manual(entered).isPresent();
    }

    String recipientLabel() {
        return manualContact.getText().isBlank()?(savedContacts.getSelectedItem() instanceof Choice choice
            ?choice.label():""):"Entered manually";
    }

    String recipientContact() {
        String entered=manualContact.getText().trim();
        if(!entered.isEmpty())return NotificationRecipient.manual(entered)
            .map(option->option.recipient().value()).orElse("");
        return savedContacts.getSelectedItem() instanceof Choice choice?choice.recipient().value():"";
    }
    JTextField manualTextField() { return manualContact; }
    String validationMessage() { return error.getText().trim(); }
    String savedContactMessage() { return savedContactStatus.getText().trim(); }
    String channelDescription() { return channel.getText(); }
    List<String> savedContactOptions() {
        List<String> values=new ArrayList<>();
        for(int i=0;i<savedContacts.getItemCount();i++)values.add(savedContacts.getItemAt(i).toString());
        return List.copyOf(values);
    }
    void selectSavedContact(int index) { savedContacts.setSelectedIndex(index); }

    private void refresh() {
        String entered=manualContact.getText().trim();
        if(!entered.isEmpty()) {
            var option=NotificationRecipient.manual(entered);
            if(option.isPresent()) {
                channel.setText("Entered manually · "+option.get().channel().name());
                error.setText(" ");
            } else {
                channel.setText("Entered manually");
                error.setText("Enter a Philippine mobile number or valid email address.");
            }
        } else if(savedContacts.getSelectedItem() instanceof Choice choice) {
            channel.setText(choice.channel().name());
            error.setText(" ");
        } else {
            channel.setText("Enter a contact number or email.");
            error.setText(" ");
        }
        if(initialized)changed.accept(isValidRecipient());
    }
}
