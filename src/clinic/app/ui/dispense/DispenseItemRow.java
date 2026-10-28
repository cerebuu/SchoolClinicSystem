package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.service.DispenseItem;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class DispenseItemRow extends JPanel {
    final MedicinePicker medicine;
    final PositiveQuantitySpinner quantity = new PositiveQuantitySpinner();
    private final JLabel stock = new JLabel("Available: —");

    DispenseItemRow(ApplicationContext app, Runnable changed, Runnable remove) {
        super(new BorderLayout(Theme.GAP, 0));
        setOpaque(false);
        medicine = new MedicinePicker(app, () -> {
            updateStock();
            changed.run();
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_2, 0));
        controls.setOpaque(false);
        quantity.getAccessibleContext().setAccessibleName("Quantity");
        quantity.setPreferredSize(new Dimension(90, Theme.CONTROL_HEIGHT));
        JButton delete = Theme.button("Remove", false);
        delete.addActionListener(event -> remove.run());
        controls.add(new JLabel("Quantity"));
        controls.add(quantity);
        controls.add(stock);
        controls.add(delete);
        add(medicine, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(0, Theme.SPACE_2, 0, Theme.SPACE_2)));
        Dimension rowSize = new Dimension(0, DispenseLayout.ITEM_ROW_HEIGHT);
        setMinimumSize(rowSize);
        setPreferredSize(rowSize);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, DispenseLayout.ITEM_ROW_HEIGHT));
    }

    private void updateStock() {
        stock.setText("Available: "
            + (medicine.selected() == null ? "—" : medicine.selected().getQuantity()));
    }

    DispenseItem item() {
        if (medicine.selected() == null) throw new IllegalArgumentException("Choose a medicine for every row.");
        return new DispenseItem(medicine.selected(), quantity.positiveQuantity());
    }
}
