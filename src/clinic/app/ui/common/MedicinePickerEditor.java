package clinic.app.ui.common;

import clinic.model.Medicine;
import javax.swing.plaf.basic.BasicComboBoxEditor;

/** Keeps the selected medicine's human-readable name in an editable picker. */
final class MedicinePickerEditor extends BasicComboBoxEditor {
    @Override
    public void setItem(Object value) {
        super.setItem(value instanceof Medicine medicine ? medicine.getDisplayName() : value);
    }
}
