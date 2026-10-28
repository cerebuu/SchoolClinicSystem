package clinic.app.ui.common.popup;

import clinic.app.Theme;
import clinic.model.Patient;
import clinic.service.DispenseItem;
import java.awt.*;
import java.util.List;
import javax.swing.*;

/** Displays recorded information for staff review; performs no medicine/allergy matching. */
public final class AllergyConfirmPopup {
    private AllergyConfirmPopup() {}
    public static boolean show(Component owner,Patient patient,List<DispenseItem> items) {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Open confirmations on the event thread.");
        JDialog dialog=PopupDialog.create(owner,"Confirm allergies before dispensing");
        JPanel content=new JPanel(new BorderLayout(Theme.GAP,Theme.GAP));
        content.setBackground(Theme.WHITE);content.setBorder(Theme.padding());
        JLabel title=new JLabel("Confirm allergies before dispensing");
        title.setForeground(Theme.WARNING);title.setFont(Theme.font(Theme.TYPE_TITLE,Font.BOLD));
        content.add(title,BorderLayout.NORTH);
        String medicines=String.join("\n",items.stream().map(i->i.medicine()+" — quantity "+i.quantity()).toList());
        JTextArea text=new JTextArea("Patient ID: "+patient.getId()+"\n\nRecorded allergies:\n"
            +String.join("\n",patient.getAllergies())+"\n\nMedicines:\n"+medicines
            +"\n\nThis system does not check medicine ingredients. Compare each medicine's ingredients with the allergies above.",16,52);
        text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);
        text.setFont(Theme.font(Theme.TYPE_BODY,Font.PLAIN));text.setForeground(Theme.INK);text.setBackground(Theme.WARNING_TINT);
        content.add(new JScrollPane(text),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);
        JCheckBox checked=new JCheckBox("I have checked and it is safe to dispense");
        checked.setOpaque(false);checked.setForeground(Theme.INK);footer.add(checked,BorderLayout.NORTH);
        JButton cancel=Theme.button("Cancel",false), dispense=Theme.button("Dispense",true);
        dispense.setBackground(Theme.WARNING);
        dispense.setEnabled(false);checked.addActionListener(e->dispense.setEnabled(checked.isSelected()));
        boolean[] confirmed={false};cancel.addActionListener(e->dialog.dispose());
        dispense.addActionListener(e->{if(checked.isSelected()){confirmed[0]=true;dialog.dispose();}});
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.setOpaque(false);buttons.add(cancel);buttons.add(dispense);
        footer.add(buttons,BorderLayout.SOUTH);content.add(footer,BorderLayout.SOUTH);dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(cancel);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        PopupDialog.show(owner,dialog);return confirmed[0];
    }
}
