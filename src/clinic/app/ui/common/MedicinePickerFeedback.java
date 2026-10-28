package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Read-only ATC display and inline medicine-selection message. */
final class MedicinePickerFeedback extends JPanel {
    private final JTextField atc=new JTextField();
    private final JLabel error=new JLabel(" ");
    MedicinePickerFeedback() {
        super(new BorderLayout(Theme.SPACE_2,0));setOpaque(false);
        atc.setEditable(false);atc.getAccessibleContext().setAccessibleName("ATC code");Theme.field(atc);
        JLabel label=new JLabel("ATC code");label.setForeground(Theme.MUTED);
        label.setFont(Theme.font(Theme.TYPE_CAPTION,Font.BOLD));
        add(label,BorderLayout.WEST);add(atc,BorderLayout.CENTER);
        error.setForeground(Theme.DANGER);error.setFont(Theme.font(Theme.TYPE_CAPTION,Font.PLAIN));
        add(error,BorderLayout.SOUTH);
    }
    void setAtc(String value){atc.setText(value);}
    void setError(String value){error.setText(value==null||value.isBlank()?" ":value);}
}
