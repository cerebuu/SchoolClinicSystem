package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.MedicinePicker;
import clinic.service.DispenseItem;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class DispenseItemRow extends JPanel {
    final MedicinePicker medicine;
    final PositiveQuantitySpinner quantity = new PositiveQuantitySpinner();
    private final JLabel stock = new JLabel("Available: —");
    private final JLabel error = new JLabel(" ");
    private final boolean wideMedicineField;
    private final Runnable layoutChanged;
    private boolean quantityEdited;

    DispenseItemRow(ApplicationContext app, Runnable changed, Runnable layoutChanged,
            Runnable remove, boolean wideMedicineField) {
        super(new BorderLayout(0, Theme.SPACE_1));
        this.wideMedicineField = wideMedicineField;
        this.layoutChanged = layoutChanged;
        setOpaque(false);
        medicine = new MedicinePicker(app, () -> {
            updateStock();
            clearError();
            changed.run();
        });
        medicine.getAccessibleContext().setAccessibleName("Medicine");
        quantity.getAccessibleContext().setAccessibleName("Quantity");
        quantity.setPreferredSize(new Dimension(70, Theme.CONTROL_HEIGHT));
        JPanel controls = controls(remove);
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
        add(error, BorderLayout.SOUTH);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(0, Theme.SPACE_2, 0, Theme.SPACE_2)));
        watchQuantityInput();
        updateHeight();
    }

    private JPanel controls(Runnable remove) {
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, Theme.SPACE_1, 0, Theme.SPACE_1);
        c.anchor = GridBagConstraints.WEST;
        controls.add(new JLabel("Qty"), c);
        c.gridx = 1;
        controls.add(quantity, c);
        c.gridx = 2;
        c.weightx = 1;
        controls.add(stock, c);
        JButton delete = Theme.button("Remove", false);
        delete.setPreferredSize(new Dimension(82, Theme.CONTROL_HEIGHT));
        delete.addActionListener(event -> remove.run());
        c.gridx = 3;
        c.weightx = 0;
        c.anchor = GridBagConstraints.EAST;
        controls.add(delete, c);
        return controls;
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

    boolean validateOptional() {
        if (medicine.selected() == null) {
            if (!quantityEdited && !medicine.hasSearchInput()) {
                showError("");
                return true;
            }
            showError("Choose a medicine from the list or remove this row.");
            return false;
        }
        try {
            quantity.positiveQuantity();
            showError("");
            return true;
        } catch (IllegalArgumentException failure) {
            showError(failure.getMessage());
            return false;
        }
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
        return error.isVisible() ? error.getText() : "";
    }

    private void clearError() {
        if (error.isVisible()) showError("");
    }

    private void showError(String message) {
        String next = message == null ? "" : message;
        if (error.isVisible() == !next.isBlank() && error.getText().equals(next)) return;
        error.setText(next.isBlank() ? " " : next);
        error.setVisible(!next.isBlank());
        updateHeight();
        layoutChanged.run();
    }

    private void updateHeight() {
        int base = wideMedicineField
            ? Theme.CONTROL_HEIGHT * 2 + Theme.SPACE_2 + Theme.SPACE_1 + Theme.SPACE_1
            : DispenseLayout.ITEM_ROW_HEIGHT;
        base += Theme.FIELD_HEIGHT + Theme.TYPE_CAPTION + Theme.SPACE_2;
        int height = base + (error.isVisible() ? Theme.TYPE_CAPTION + Theme.SPACE_1 : 0);
        Dimension size = new Dimension(0, height);
        setMinimumSize(size);
        setPreferredSize(size);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }
}
