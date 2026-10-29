package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

/** Adds shared row and cell copy actions to paged tables. */
public final class TableClipboardSupport {
    private static final String INSTALLED = TableClipboardSupport.class.getName() + ".installed";
    private static final String COPY = TableClipboardSupport.class.getName() + ".copy";
    private TableClipboardSupport() {}

    public static void install(JTable table) {
        if (GraphicsEnvironment.isHeadless()) return;
        install(table, Toolkit.getDefaultToolkit().getSystemClipboard());
    }

    static void install(JTable table, Clipboard clipboard) {
        if (Boolean.TRUE.equals(table.getClientProperty(INSTALLED))) return;
        table.putClientProperty(INSTALLED, Boolean.TRUE);
        int menuMask;
        try {
            menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException unavailable) {
            menuMask = InputEvent.CTRL_DOWN_MASK;
        }
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), COPY);
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), COPY);
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK), COPY);
        table.getActionMap().put(COPY, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                copySelection(table, clipboard);
            }
        });
        table.addMouseListener(new MouseAdapter() {
            private long lastPopup;
            @Override public void mousePressed(MouseEvent event) { popup(event); }
            @Override public void mouseReleased(MouseEvent event) { popup(event); }
            private void popup(MouseEvent event) {
                if (!event.isPopupTrigger() || System.currentTimeMillis() - lastPopup < 250) return;
                lastPopup = System.currentTimeMillis();
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row < 0 || column < 0) return;
                table.changeSelection(row, column, false, false);
                JPopupMenu menu = createMenu(table, row, column, clipboard);
                menu.show(table, event.getX(), event.getY());
            }
        });
        table.getAccessibleContext().setAccessibleDescription(
            "Select a row and press Cmd+C or Ctrl+C to copy it. Right-click for cell or row copy.");
        ToolTipManager.sharedInstance().registerComponent(table);
    }

    static JPopupMenu createMenu(JTable table, int row, int column, Clipboard clipboard) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.WHITE);
        JMenuItem cell = item("Copy cell");
        JMenuItem wholeRow = item("Copy row");
        cell.addActionListener(event -> copyCell(table, row, column, clipboard));
        wholeRow.addActionListener(event -> copyRow(table, row, clipboard));
        menu.add(cell);
        menu.add(wholeRow);
        return menu;
    }

    static void copyCell(JTable table, int row, int column, Clipboard clipboard) {
        if (valid(table, row, column)) copy(table, text(table.getValueAt(row, column)), clipboard, "Copied cell");
    }

    static void copyRow(JTable table, int row, Clipboard clipboard) {
        if (row < 0 || row >= table.getRowCount()) return;
        StringBuilder value = new StringBuilder();
        for (int column = 0; column < table.getColumnCount(); column++) {
            if ("Actions".equals(table.getColumnName(column))) continue;
            if (!value.isEmpty()) value.append('\t');
            value.append(text(table.getValueAt(row, column)));
        }
        copy(table, value.toString(), clipboard, "Copied row");
    }

    private static void copySelection(JTable table, Clipboard clipboard) {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int column = table.getSelectedColumn();
        if (table.getColumnSelectionAllowed() && column >= 0)
            copyCell(table, row, column, clipboard);
        else copyRow(table, row, clipboard);
    }

    private static boolean valid(JTable table, int row, int column) {
        return row >= 0 && row < table.getRowCount()
            && column >= 0 && column < table.getColumnCount();
    }

    private static String text(Object value) {
        if (value instanceof CopyableIdCell.Value id)
            return id.id() == null ? id.label() : id.id();
        if (value instanceof clinic.model.MedicineProduct product) return product.atcLabel();
        return value == null ? "" : value.toString();
    }

    private static void copy(JTable table, String value, Clipboard clipboard, String message) {
        try {
            clipboard.setContents(new StringSelection(value), null);
            showCopied(table, message);
        } catch (IllegalStateException | SecurityException | HeadlessException failure) {
            showCopied(table, "Unable to access clipboard");
        }
    }

    private static void showCopied(JTable table, String message) {
        table.setToolTipText(message);
        if (GraphicsEnvironment.isHeadless() || !table.isShowing()) return;
        javax.swing.JToolTip tip = table.createToolTip();
        tip.setTipText(message);
        Point location = table.getMousePosition();
        if (location == null) location = new Point(table.getWidth() / 2, 0);
        Rectangle bounds = table.getCellRect(table.getSelectedRow(), Math.max(0, table.getSelectedColumn()), true);
        Point screen = new Point(location.x, bounds.y);
        SwingUtilities.convertPointToScreen(screen, table);
        Popup popup = PopupFactory.getSharedInstance().getPopup(table, tip, screen.x, screen.y);
        popup.show();
        Timer hide = new Timer(1200, event -> popup.hide());
        hide.setRepeats(false);
        hide.start();
    }

    private static JMenuItem item(String label) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.PLAIN));
        item.setForeground(Theme.INK);
        return item;
    }
}
