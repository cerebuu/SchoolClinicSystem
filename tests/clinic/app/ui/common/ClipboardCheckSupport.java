package clinic.app.ui.common;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.swing.table.DefaultTableModel;

final class ClipboardCheckSupport {
    static int checks;
    static Clipboard clipboard;

    private ClipboardCheckSupport() {}

    static void exercise(JTextComponent field, String name, String value) throws Exception {
        exercise(field, name, value, value.length());
    }

    static void exercise(JTextComponent field, String name, String value, int limit) throws Exception {
        copyClearPaste(field, value, limit);
        String expected = value.substring(0, Math.min(limit, value.length()));
        check(field.getText().equals(expected), "pasted value and length limit");
        cut(field, name, expected);
    }

    static void copyClearPaste(JTextComponent field, String value) throws Exception {
        copyClearPaste(field, value, value.length());
    }

    private static void copyClearPaste(JTextComponent field, String value, int limit) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            field.setText(value);
            field.selectAll();
            action(field, "clinic.clipboard.copy");
            field.setText("");
            action(field, "clinic.clipboard.paste");
        });
        check(field.getText().equals(value.substring(0, Math.min(limit, value.length()))),
            "copy, clear, and paste round-trip");
    }

    static void cut(JTextComponent field, String name, String expected) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            field.selectAll();
            action(field, "clinic.clipboard.cut");
        });
        check(field.getText().isEmpty(), "cut empties selected text");
        check(clipboardText().equals(expected), "cut leaves selected text on the clipboard");
        System.out.println("PASTE/CUT " + name + " | field empty=true | clipboard matches=true");
    }

    static void readOnlyMenu(Clipboard local) throws Exception {
        local.setContents(new StringSelection(""), null);
        JTextField field = addField("read only");
        SwingUtilities.invokeAndWait(() -> field.setEditable(false));
        JPopupMenu menu = field.getComponentPopupMenu();
        check(menu != null, "right-click menu is attached");
        refreshMenu(menu);
        check(!item(menu, "Cut").isEnabled() && !item(menu, "Copy").isEnabled()
            && !item(menu, "Paste").isEnabled() && item(menu, "Select All").isEnabled(),
            "read-only menu disables editing and enables select all");
        System.out.println("MENU read-only / no selection | Cut=false Copy=false Paste=false Select All=true");
        SwingUtilities.invokeAndWait(field::selectAll);
        refreshMenu(menu);
        check(item(menu, "Copy").isEnabled(), "read-only selection enables copy");
        SwingUtilities.invokeAndWait(() -> item(menu, "Copy").doClick());
        check(clipboardText().equals("read only"), "read-only selection copies");
        System.out.println("MENU read-only / selected | Cut=false Copy=true Paste=false Select All=true");
        local.setContents(new StringSelection("available"), null);
        refreshMenu(menu);
        check(!item(menu, "Paste").isEnabled(), "read-only paste stays disabled with text available");
        System.out.println("MENU read-only / clipboard text present | Cut=false Copy=true Paste=false Select All=true");
    }

    static void editableMenu(Clipboard local) throws Exception {
        local.setContents(new StringSelection(""), null);
        JTextField field = addField("editable");
        JPopupMenu menu = field.getComponentPopupMenu();
        refreshMenu(menu);
        check(!item(menu, "Cut").isEnabled() && !item(menu, "Copy").isEnabled()
            && !item(menu, "Paste").isEnabled() && item(menu, "Select All").isEnabled(),
            "editable menu disables selection actions and empty-clipboard paste");
        System.out.println("MENU editable / no selection, empty clipboard | Cut=false Copy=false Paste=false Select All=true");
        SwingUtilities.invokeAndWait(field::selectAll);
        refreshMenu(menu);
        check(item(menu, "Cut").isEnabled() && item(menu, "Copy").isEnabled(),
            "selection enables cut and copy");
        local.setContents(new StringSelection("available"), null);
        refreshMenu(menu);
        check(item(menu, "Paste").isEnabled(), "editable paste enables when clipboard has text");
        System.out.println("MENU editable / selected, clipboard text present | Cut=true Copy=true Paste=true Select All=true");
    }

    static void passwordRules(Clipboard local) throws Exception {
        var field = new javax.swing.JPasswordField("secret");
        SwingUtilities.invokeAndWait(() -> new JPanel().add(field));
        local.setContents(new StringSelection("replacement"), null);
        JPopupMenu menu = field.getComponentPopupMenu();
        refreshMenu(menu);
        check(!item(menu, "Cut").isEnabled() && !item(menu, "Copy").isEnabled()
            && item(menu, "Paste").isEnabled(), "password permits paste, not cut or copy");
        SwingUtilities.invokeAndWait(() -> {
            field.setText("");
            action(field, "clinic.clipboard.paste");
        });
        check(new String(field.getPassword()).equals("replacement"), "password paste works");
    }

    static void singleLinePaste(Clipboard local) throws Exception {
        JTextField field = addField("");
        local.setContents(new StringSelection("  first\r\nsecond\n "), null);
        SwingUtilities.invokeAndWait(() -> action(field, "clinic.clipboard.paste"));
        check(field.getText().equals("first second"), "single-line paste trims and replaces line breaks");
    }

    static void shortcutBindings(JTextField field) {
        int[] keys = {java.awt.event.KeyEvent.VK_X, java.awt.event.KeyEvent.VK_C,
            java.awt.event.KeyEvent.VK_V, java.awt.event.KeyEvent.VK_A};
        String[] actions = {"clinic.clipboard.cut", "clinic.clipboard.copy",
            "clinic.clipboard.paste", "clinic.clipboard.selectAll"};
        for (int i = 0; i < keys.length; i++) for (int mask : new int[]{
                java.awt.event.InputEvent.CTRL_DOWN_MASK, java.awt.event.InputEvent.META_DOWN_MASK}) {
            check(actions[i].equals(field.getInputMap(JComponent.WHEN_FOCUSED)
                .get(javax.swing.KeyStroke.getKeyStroke(keys[i], mask))),
                "Ctrl and Cmd shortcut is explicitly bound");
        }
    }

    static void tableCopy(Clipboard local) throws Exception {
        Object[][] rows = {{CopyableIdCell.Value.id("P-123"), "Student name", "Actions"}};
        String[] columns = {"Patient ID", "Name", "Actions"};
        JTable table = new JTable(new DefaultTableModel(rows, columns));
        TableClipboardSupport.install(table, local);
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        TableClipboardSupport.copyCell(table, 0, 0, local);
        check(clipboardText().equals("P-123"), "table cell copy unwraps full ID value");
        TableClipboardSupport.copyRow(table, 0, local);
        check(clipboardText().equals("P-123\tStudent name"),
            "table row copy is tab-separated and skips actions");
        JPopupMenu menu = TableClipboardSupport.createMenu(table, 0, 1, local);
        check(item(menu, "Copy cell") != null && item(menu, "Copy row") != null,
            "table context menu exposes cell and row copy");
        SwingUtilities.invokeAndWait(() -> item(menu, "Copy cell").doClick());
        check(clipboardText().equals("Student name"), "table menu copies selected cell");
        SwingUtilities.invokeAndWait(() -> item(menu, "Copy row").doClick());
        check(clipboardText().equals("P-123\tStudent name"), "table menu copies full row");
        System.out.println("TABLE Copy cell | P-123: PASS");
        System.out.println("TABLE Copy row | P-123\\tStudent name: PASS");
    }

    static void alertDetectedFormat() {
        clinic.model.Alert alert = new clinic.model.Alert("OUTBREAK", "sample message");
        clinic.app.ui.notifications.AlertsTableModel model =
            new clinic.app.ui.notifications.AlertsTableModel();
        model.replace(new clinic.service.PageResult<>(java.util.List.of(alert), 1));
        check(model.getValueAt(0, 2).equals(clinic.util.DateUtil.format(alert.getCreatedAt())),
            "alert Detected value uses shared date format");
        check(model.toolTipAt(0,1).equals(alert.getMessage()),"alert message tooltip retains the full message");
        System.out.println("ALERT Detected | " + model.getValueAt(0, 2));
        System.out.println("ALERT full message tooltip | " + model.toolTipAt(0,1));
    }

    static <T> T field(Object target, String name) {
        try {
            var member = target.getClass().getDeclaredField(name);
            member.setAccessible(true);
            @SuppressWarnings("unchecked") T value = (T) member.get(target);
            return value;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for debounced paste handling.");
    }

    static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }

    private static JTextField addField(String value) throws Exception {
        java.util.concurrent.atomic.AtomicReference<JTextField> result =
            new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JTextField field = new JTextField(value);
            new JPanel().add(field);
            result.set(field);
        });
        return result.get();
    }

    private static void refreshMenu(JPopupMenu menu) {
        var event = new javax.swing.event.PopupMenuEvent(menu);
        for (var listener : menu.getPopupMenuListeners()) listener.popupMenuWillBecomeVisible(event);
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (var component : menu.getComponents())
            if (component instanceof JMenuItem menuItem && menuItem.getText().equals(text)) return menuItem;
        throw new AssertionError("Menu item missing: " + text);
    }

    private static void action(JTextComponent field, String name) {
        var action = field.getActionMap().get(name);
        if (action == null) throw new AssertionError("Clipboard action not installed: " + name);
        action.actionPerformed(new java.awt.event.ActionEvent(field, 0, name));
    }

    private static String clipboardText() throws Exception {
        return (String) clipboard.getContents(null).getTransferData(DataFlavor.stringFlavor);
    }
}
