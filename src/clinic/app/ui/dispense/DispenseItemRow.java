package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.MedicinePicker;
import clinic.model.Patient;
import clinic.service.DispenseItem;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class DispenseItemRow extends JPanel {
    final MedicinePicker medicine;
    final PositiveQuantitySpinner quantity = new PositiveQuantitySpinner();
    private final JLabel stock = new JLabel("Available: —");
    private final JLabel error = new JLabel(" ");
    private MedicineSafetyStatus safety;
    private MedicineRowFeedback feedback;
    private final boolean wideMedicineField;
    private final boolean initialRow;
    private final Runnable layoutChanged;
    private boolean quantityEdited;
    private String validationMessage="";

    DispenseItemRow(ApplicationContext app, Runnable changed, Runnable layoutChanged,
            Runnable remove, boolean wideMedicineField,boolean initialRow) {
        super(new BorderLayout(0, Theme.SPACE_1));
        this.wideMedicineField = wideMedicineField;
        this.initialRow=initialRow;
        this.layoutChanged = layoutChanged;
        setOpaque(false);
        stock.setForeground(Theme.MUTED);
        stock.setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.PLAIN));
        medicine = new MedicinePicker(app, () -> {
            updateStock();
            clearError();
            safety.refresh();
            changed.run();
        });
        safety=new MedicineSafetyStatus(app,medicine::selected,this::refreshFeedback);
        medicine.getAccessibleContext().setAccessibleName("Medicine");
        quantity.getAccessibleContext().setAccessibleName("Quantity");
        quantity.setPreferredSize(new Dimension(70, Theme.CONTROL_HEIGHT));
        JPanel controls = new MedicineRowControls(quantity,stock,remove);
        if (wideMedicineField) {
            JPanel stacked = new JPanel(new BorderLayout(0, Theme.SPACE_1));
            stacked.setOpaque(false);
            stacked.add(medicine, BorderLayout.NORTH);
            stacked.add(controls, BorderLayout.SOUTH);
            add(stacked, BorderLayout.CENTER);
        } else {
            add(medicine, BorderLayout.CENTER);
            add(controls, BorderLayout.EAST);
        }
        error.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.PLAIN));
        error.setForeground(Theme.DANGER);
        error.setVisible(false);
        feedback=new MedicineRowFeedback(safety,error);
        add(feedback, BorderLayout.SOUTH);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(0, Theme.SPACE_2, 0, Theme.SPACE_2)));
        watchQuantityInput();
        updateHeight();
    }

    private void watchQuantityInput() {
        var input = ((PositiveQuantitySpinner.DefaultEditor) quantity.getEditor()).getTextField();
        input.getDocument().addDocumentListener(new DocumentListener() {
            private void edited() {
                quantityEdited = true;
                clearError();
            }
            @Override public void insertUpdate(DocumentEvent event) { edited(); }
            @Override public void removeUpdate(DocumentEvent event) { edited(); }
            @Override public void changedUpdate(DocumentEvent event) { edited(); }
        });
        quantity.addChangeListener(event -> {
            quantityEdited = true;
            clearError();
        });
    }

    private void updateStock() {
        stock.setText("Available: "
            + (medicine.selected() == null ? "—" : medicine.selected().getQuantity()));
    }

    boolean validate(boolean optional) {
        if (medicine.selected() == null) {
            if (optional&&initialRow&&!quantityEdited && !medicine.hasSearchInput()) {
                showError("");
                return true;
            }
            showError("Choose a medicine or remove this row.");
            medicine.focusSearch();
            return false;
        }
        try {
            int requested=quantity.positiveQuantity();
            int available=medicine.selected().getQuantity();
            if(requested>available) {
                showError("Only "+available+" left in stock for "+medicine.selected().getName()+".");
                quantity.requestFocusInWindow();
                return false;
            }
            showError("");
            return true;
        } catch (IllegalArgumentException failure) {
            showError(failure.getMessage());
            quantity.requestFocusInWindow();
            return false;
        }
    }

    void setPatient(Patient value) {
        safety.setPatient(value);
    }

    void focusInvalidField() {
        if(medicine.selected()==null)medicine.focusSearch();
        else quantity.requestFocusInWindow();
    }

    void scrollValidationErrorIntoView(JPanel list) {
        list.doLayout();
        doLayout();
        feedback.doLayout();
        Component target=medicine.selected()==null?medicine:error;
        Rectangle bounds=javax.swing.SwingUtilities.convertRectangle(target.getParent(),target.getBounds(),list);
        list.scrollRectToVisible(bounds);
    }

    boolean hasOptionalValue() {
        return medicine.selected() != null;
    }

    DispenseItem optionalItem() {
        return hasOptionalValue() ? item() : null;
    }

    DispenseItem item() {
        if (medicine.selected() == null) throw new IllegalArgumentException("Choose a medicine for every row.");
        return new DispenseItem(medicine.selected(), quantity.positiveQuantity());
    }

    int rowHeight() {
        return getPreferredSize().height;
    }

    String validationError() {
        return validationMessage;
    }

    private void clearError() {
        if (error.isVisible()) showError("");
    }

    private void showError(String message) {
        validationMessage=message==null?"":message;
        String next=medicine.selected()==null?"":validationMessage;
        if(medicine.selected()==null)medicine.showValidationError(validationMessage);
        if (error.isVisible() == !next.isBlank() && error.getText().equals(next)) return;
        error.setText(next.isBlank() ? " " : next);
        error.setVisible(!next.isBlank());
        feedback.refresh();
        updateHeight();
        layoutChanged.run();
    }

    private void refreshFeedback() {
        feedback.refresh();
        updateHeight();
        layoutChanged.run();
    }

    private void updateHeight() {
        int body=wideMedicineField?medicine.getPreferredSize().height+Theme.SPACE_1+Theme.CONTROL_HEIGHT
            :Math.max(medicine.getPreferredSize().height,Theme.CONTROL_HEIGHT);
        int feedbackHeight=feedback.isVisible()?feedback.getPreferredSize().height+Theme.SPACE_1:0;
        int height=body+Theme.SPACE_1+feedbackHeight+Theme.SPACE_2;
        Dimension size = new Dimension(0, height);
        setMinimumSize(size);
        setPreferredSize(size);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

}
