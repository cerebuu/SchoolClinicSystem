package clinic.app.ui.dispense;

import clinic.app.Theme;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Quantity, available-stock label, and remove control for one dispense row. */
final class MedicineRowControls extends JPanel {
    MedicineRowControls(PositiveQuantitySpinner quantity,JLabel stock,Runnable remove) {
        super(new GridBagLayout());
        setOpaque(false);
        GridBagConstraints c=new GridBagConstraints();
        c.gridy=0;c.insets=new Insets(0,Theme.SPACE_1,0,Theme.SPACE_1);c.anchor=GridBagConstraints.WEST;
        JLabel label=new JLabel("Qty");
        label.setForeground(Theme.MUTED);label.setFont(Theme.font(Theme.TYPE_CAPTION,Font.BOLD));
        add(label,c);
        c.gridx=1;add(quantity,c);
        c.gridx=2;c.weightx=1;add(stock,c);
        JButton delete=Theme.button("Remove",false);
        delete.setFont(Theme.font(Theme.TYPE_CAPTION,Font.BOLD));
        delete.setPreferredSize(new Dimension(82,Theme.CONTROL_HEIGHT));
        delete.addActionListener(event->remove.run());
        c.gridx=3;c.weightx=0;c.anchor=GridBagConstraints.EAST;add(delete,c);
    }
}
