package clinic.app.ui.dispense;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Reserves row height only for visible allergy and validation feedback. */
final class MedicineRowFeedback extends JPanel {
    private final JLabel safety;
    private final JLabel error;

    MedicineRowFeedback(JLabel safety, JLabel error) {
        this.safety=safety;
        this.error=error;
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        setOpaque(false);
        refresh();
    }

    void refresh() {
        removeAll();
        if (safety.isVisible()) add(safety);
        if (error.isVisible()) add(error);
        setVisible(getComponentCount()>0);
        revalidate();
        repaint();
    }
}
