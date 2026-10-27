package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.service.DispenseItem;
import clinic.service.DispenseWorkflow;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public final class DispenseItemsPanel extends JPanel {
    private final List<DispenseItemRow> rows = new ArrayList<>();
    private final JPanel list = new JPanel();
    private final JScrollPane scroll;
    private final ApplicationContext app;

    public DispenseItemsPanel(ApplicationContext app) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.app = app;
        setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Theme.WHITE);
        scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        scroll.getViewport().setBackground(Theme.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(Theme.SPACE_4);
        scroll.setWheelScrollingEnabled(true);
        add(scroll, BorderLayout.CENTER);
        JButton add = Theme.button("+ Add medicine", false);
        add.addActionListener(event -> addRow());
        add(add, BorderLayout.SOUTH);
        addRow();
    }

    private void addRow() {
        DispenseItemRow[] holder = new DispenseItemRow[1];
        holder[0] = new DispenseItemRow(app, () -> merge(holder[0]), () -> remove(holder[0]));
        rows.add(holder[0]);
        list.add(holder[0]);
        updateListHeight();
    }

    private void remove(DispenseItemRow row) {
        rows.remove(row);
        list.remove(row);
        updateListHeight();
    }

    private void updateListHeight() {
        int contentHeight = rows.size() * DispenseLayout.ITEM_ROW_HEIGHT;
        list.setPreferredSize(new Dimension(0, contentHeight));
        list.setMinimumSize(new Dimension(0, contentHeight));
        scroll.setPreferredSize(new Dimension(0,
            Math.min(rows.size(), DispenseLayout.MAX_VISIBLE_ITEMS) * DispenseLayout.ITEM_ROW_HEIGHT - 1));
        DispenseLayout.sizeItems(this, scroll, rows.size());
        list.revalidate();
        revalidate();
        repaint();
    }

    private void merge(DispenseItemRow changed) {
        if (changed == null || changed.medicine.selected() == null) return;
        for (DispenseItemRow row : List.copyOf(rows)) {
            if (row != changed && row.medicine.selected() != null
                && row.medicine.selected().getProduct().key()
                    .equals(changed.medicine.selected().getProduct().key())) {
                try {
                    row.quantity.setValue(Math.addExact(row.item().quantity(), changed.item().quantity()));
                    remove(changed);
                } catch (RuntimeException error) {
                    MessagePopup.error(this, error);
                }
                return;
            }
        }
    }

    public List<DispenseItem> items() {
        return DispenseWorkflow.merge(rows.stream().map(DispenseItemRow::item).toList());
    }

    public void clear() {
        for (DispenseItemRow row : List.copyOf(rows)) remove(row);
        addRow();
    }

    public void refresh() {
        rows.forEach(row -> row.medicine.refresh());
    }

    public int itemCount() {
        return rows.size();
    }

    public int itemViewportHeight() {
        return scroll.getViewport().getExtentSize().height;
    }

    public int visibleItemRows() {
        scroll.getViewport().doLayout();
        list.doLayout();
        java.awt.Rectangle visible = scroll.getViewport().getViewRect();
        int count = 0;
        for (DispenseItemRow row : rows) {
            if (row.getBounds().intersects(visible)) count++;
        }
        return count;
    }

    public String itemBounds() {
        return rows.stream().map(row -> row.getBounds().toString()).toList().toString()
            + " view=" + scroll.getViewport().getViewRect();
    }
}
