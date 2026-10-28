package clinic.app.ui.dispense;

import clinic.app.Theme;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JScrollPane;

public final class DispenseLayout {
    public static final int ITEM_ROW_HEIGHT = 48;
    public static final int MAX_VISIBLE_ITEMS = 6;
    public static final int HISTORY_VISIBLE_ROWS = 10;
    public static final int ROW_HEIGHT = 28;
    public static final int COMBO_HEIGHT = Theme.CONTROL_HEIGHT;

    private DispenseLayout() {}

    public static void sizeSection(JComponent section) {
        Dimension preferred = section.getPreferredSize();
        section.setMinimumSize(new Dimension(0, preferred.height));
        section.setPreferredSize(new Dimension(preferred.width, preferred.height));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    }

    public static void sizeItems(JComponent items, JScrollPane scroll, int rowCount) {
        int visibleRows = Math.max(1, Math.min(rowCount, MAX_VISIBLE_ITEMS));
        int listHeight = visibleRows * ITEM_ROW_HEIGHT;
        int height = listHeight - 3 + Theme.CONTROL_HEIGHT + Theme.GAP + Theme.SPACE_1;
        scroll.setPreferredSize(new Dimension(0, listHeight - 3));
        scroll.setMinimumSize(new Dimension(0, ITEM_ROW_HEIGHT));
        items.setMinimumSize(new Dimension(0, height));
        items.setPreferredSize(new Dimension(0, height));
        items.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }
}
