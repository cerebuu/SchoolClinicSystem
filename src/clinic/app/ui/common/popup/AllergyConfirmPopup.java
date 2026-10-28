package clinic.app.ui.common.popup;

import clinic.app.Theme;
import clinic.model.Patient;
import clinic.service.DispenseItem;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Displays recorded information and collects a written safety override reason. */
public final class AllergyConfirmPopup {
    private AllergyConfirmPopup() {}

    public static Optional<String> show(Component owner,Patient patient,List<DispenseItem> items) {
        if(!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Open confirmations on the event thread.");
        JDialog dialog=PopupDialog.create(owner,"Review allergy warning");
        JPanel content=new JPanel(new BorderLayout(Theme.GAP,Theme.GAP));
        content.setBackground(Theme.WHITE);content.setBorder(Theme.padding());
        JLabel title=new JLabel("Review allergies before dispensing");
        title.setForeground(Theme.WARNING);title.setFont(Theme.font(Theme.TYPE_TITLE,Font.BOLD));
        content.add(title,BorderLayout.NORTH);
        String medicines=String.join("\n",items.stream()
            .map(i->i.medicine()+" — quantity "+i.quantity()).toList());
        JTextArea details=new JTextArea("Patient ID: "+patient.getId()+"\n\nRecorded allergies:\n"
            +String.join("\n",patient.getAllergies())+"\n\nMedicines:\n"+medicines
            +"\n\nThis system does not check medicine ingredients. Compare each medicine's ingredients with the allergies above.",10,48);
        details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);
        details.setFont(Theme.font(Theme.TYPE_BODY,Font.PLAIN));details.setForeground(Theme.INK);
        details.setBackground(Theme.WARNING_TINT);
        JPanel center=new JPanel(new BorderLayout(0,Theme.SPACE_2));center.setOpaque(false);
        center.add(new JScrollPane(details),BorderLayout.CENTER);
        JTextArea reason=new JTextArea(3,48);reason.setLineWrap(true);reason.setWrapStyleWord(true);
        reason.setName("override-reason");reason.setFont(Theme.font(Theme.TYPE_BODY,Font.PLAIN));Theme.field(reason);
        reason.setPreferredSize(new java.awt.Dimension(480,68));
        JLabel label=new JLabel("Written override reason *");label.setFont(Theme.font(Theme.TYPE_BODY,Font.BOLD));
        JLabel error=new JLabel("Enter at least 10 characters.");error.setForeground(Theme.DANGER);
        error.setFont(Theme.font(Theme.TYPE_CAPTION,Font.PLAIN));
        JPanel reasonPanel=new JPanel(new BorderLayout(0,Theme.SPACE_1));reasonPanel.setOpaque(false);
        reasonPanel.add(label,BorderLayout.NORTH);reasonPanel.add(reason,BorderLayout.CENTER);
        reasonPanel.add(error,BorderLayout.SOUTH);center.add(reasonPanel,BorderLayout.SOUTH);
        content.add(center,BorderLayout.CENTER);
        JCheckBox checked=new JCheckBox("I have reviewed the recorded allergies and medicine details");
        checked.setName("override-reviewed");
        checked.setOpaque(false);checked.setForeground(Theme.INK);
        JButton cancel=Theme.button("Cancel",false),confirm=Theme.button("Confirm and save",true);
        confirm.setBackground(Theme.WARNING);confirm.setEnabled(false);
        Runnable readiness=()->confirm.setEnabled(checked.isSelected()&&reason.getText().trim().length()>=10);
        checked.addActionListener(event->readiness.run());
        reason.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){readiness.run();}
            public void removeUpdate(DocumentEvent e){readiness.run();}
            public void changedUpdate(DocumentEvent e){readiness.run();}
        });
        String[] result={null};cancel.addActionListener(event->dialog.dispose());
        confirm.addActionListener(event->{if(confirm.isEnabled()){result[0]=reason.getText().trim();dialog.dispose();}});
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.setOpaque(false);
        buttons.add(cancel);buttons.add(confirm);
        JPanel footer=new JPanel(new BorderLayout(0,Theme.SPACE_2));footer.setOpaque(false);
        footer.add(checked,BorderLayout.NORTH);footer.add(buttons,BorderLayout.SOUTH);
        content.add(footer,BorderLayout.SOUTH);dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(cancel);
        dialog.getRootPane().registerKeyboardAction(event->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        PopupDialog.show(owner,dialog);return Optional.ofNullable(result[0]);
    }
}
