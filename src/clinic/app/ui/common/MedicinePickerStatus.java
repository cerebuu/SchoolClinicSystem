package clinic.app.ui.common;

import javax.swing.JComboBox;

/** Updates accessible loading and result status for the medicine picker. */
final class MedicinePickerStatus {
    private MedicinePickerStatus() {}
    static void show(JComboBox<?> choices,String message) {
        choices.setToolTipText(message);
        choices.getAccessibleContext().setAccessibleDescription(message);
    }
}
