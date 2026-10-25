package clinic.app.ui.dispense;
import clinic.app.*;
import clinic.app.ui.common.AsyncLoader;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;

public final class PatientLookupField extends JPanel {
    private final JTextField input=new JTextField(18);
    private final JLabel status=new JLabel("Look up a registered patient before dispensing.");
    private String confirmed;
    private int generation;
    public PatientLookupField(ApplicationContext app) {
        super(new BorderLayout(Theme.GAP,Theme.GAP));setOpaque(false);
        Theme.field(input);input.setName("Patient ID");input.getAccessibleContext().setAccessibleName("Patient ID");
        JButton lookup=Theme.button("Find patient",false);
        add(input,BorderLayout.CENTER);add(lookup,BorderLayout.EAST);add(status,BorderLayout.SOUTH);
        input.getDocument().addDocumentListener(new DocumentListener(){
            private void changed(){generation++;confirmed=null;status.setText("Confirm patient with Find patient.");}
            public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}
        });
        lookup.addActionListener(e->{
            int version=++generation;String id=input.getText().trim();
            status.setForeground(Theme.MUTED);status.setText("Looking up patient...");
            AsyncLoader.load(()->app.clinic().findPatient(id).orElseThrow(()->new IllegalArgumentException("Patient not found.")),p->{
                if(version!=generation)return;
                confirmed=p.getId();status.setForeground(Theme.INK);status.setText("Confirmed: "+p.getId()+" | "+p.getPatientType());
            },error->{if(version==generation){status.setForeground(Theme.DANGER);status.setText("Patient not found or unavailable. Retry lookup.");}});
        });
        input.addActionListener(e->lookup.doClick());
    }
    public String patientId(){if(confirmed==null)throw new IllegalArgumentException("Find and confirm a patient first.");return confirmed;}
    public void clear(){input.setText("");}
}
