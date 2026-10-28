package clinic.app.ui.inventory;

import clinic.app.Theme;
import clinic.app.ui.common.TableRowRenderer;
import clinic.app.ui.common.CopyableIdCell;
import clinic.app.ui.common.popup.PopupDialog;
import clinic.model.Medicine;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;

/** Read-only batch inspection with an optional path to restock one exact batch. */
final class InventoryBatchDialog {
    private InventoryBatchDialog() {}

    static Medicine show(Component owner, InventoryProductRow medicine, boolean canRestock) {
        List<Medicine> batches = medicine.batches();
        JDialog dialog = PopupDialog.create(owner, "Medicine batches — " + medicine.product().displayName());
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4, Theme.SPACE_4,
            Theme.SPACE_4, Theme.SPACE_4));
        JLabel summary = new JLabel(batches.size() + " batch records · " + medicine.eligibleQuantity()
            + " eligible units", SwingConstants.LEADING);
        summary.setForeground(Theme.MUTED);
        content.add(summary, BorderLayout.NORTH);

        BatchTableModel model = new BatchTableModel(batches);
        JTable table = new JTable(model);
        Theme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new TableRowRenderer());
        table.setDefaultRenderer(Number.class, new TableRowRenderer());
        CopyableIdCell.install(table);
        table.getAccessibleContext().setAccessibleName("Batches for " + medicine.product().displayName());
        table.getAccessibleContext().setAccessibleDescription(
            "Individual batch quantity, expiry, received date, status, and remarks.");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(850, 340));
        content.add(scroll, BorderLayout.CENTER);

        Medicine[] chosen = {null};
        JButton close = Theme.button("Close", false);
        close.getAccessibleContext().setAccessibleName("Close batch details");
        JButton restock = Theme.button("Restock selected batch", true);
        restock.setEnabled(canRestock && !batches.isEmpty());
        restock.getAccessibleContext().setAccessibleName("Restock the selected batch");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) restock.setEnabled(canRestock && table.getSelectedRow() >= 0);
        });
        close.addActionListener(event -> dialog.dispose());
        restock.addActionListener(event -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                chosen[0] = batches.get(table.convertRowIndexToModel(selected));
                dialog.dispose();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_2, 0));
        buttons.add(close);
        if (canRestock) buttons.add(restock);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        PopupDialog.show(owner, dialog);
        return chosen[0];
    }

    private static final class BatchTableModel extends AbstractTableModel {
        private final List<Medicine> rows;
        private BatchTableModel(List<Medicine> rows) { this.rows = rows; }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 6; }
        @Override public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Batch ID";
                case 1 -> "Quantity";
                case 2 -> "Expiry";
                case 3 -> "Received";
                case 4 -> "Status";
                case 5 -> "Remarks";
                default -> "";
            };
        }
        @Override public Class<?> getColumnClass(int column) { return column == 1 ? Integer.class : String.class; }
        @Override public Object getValueAt(int row, int column) {
            Medicine batch = rows.get(row);
            return switch (column) {
                case 0 -> batch.getBatchId();
                case 1 -> batch.getQuantity();
                case 2 -> batch.getExpirationDate();
                case 3 -> batch.getBatchDate();
                case 4 -> status(batch);
                case 5 -> batch.getRemarks().isBlank() ? "—" : batch.getRemarks();
                default -> "";
            };
        }
        private static String status(Medicine batch) {
            if (batch.getExpirationDate().isBefore(LocalDate.now())) return "Expired";
            return batch.getQuantity() == 0 ? "Depleted" : "Eligible";
        }
    }
}
