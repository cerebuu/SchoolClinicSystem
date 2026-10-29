package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.JTextComponent;

/** Installs consistent clipboard actions on every current and future text component. */
public final class ClipboardSupport {
    private static final String INSTALLED = ClipboardSupport.class.getName() + ".installed";
    private static final String CUT = "clinic.clipboard.cut";
    private static final String COPY = "clinic.clipboard.copy";
    private static final String PASTE = "clinic.clipboard.paste";
    private static final String SELECT_ALL = "clinic.clipboard.selectAll";
    private static Clipboard clipboard;
    private static boolean installed;

    private static final AWTEventListener COMPONENT_WATCHER = event -> {
        if (event instanceof ContainerEvent added && added.getID() == ContainerEvent.COMPONENT_ADDED)
            installTree(added.getChild());
        else if (event instanceof WindowEvent opened && opened.getID() == WindowEvent.WINDOW_OPENED)
            installTree(opened.getWindow());
    };

    private ClipboardSupport() {}
    public static synchronized void install() {
        install(Toolkit.getDefaultToolkit().getSystemClipboard());
    }

    /** Copies an exact string through the clipboard shared by text and table controls. */
    public static synchronized void copyText(String value) {
        if(value==null)throw new IllegalArgumentException("Text to copy is required.");
        if(!installed)install();
        try {
            clipboard.setContents(new StringSelection(value),null);
        } catch(IllegalStateException|HeadlessException|SecurityException failure) {
            reportFailure(null);
            throw new IllegalStateException("Clipboard access failed.",failure);
        }
    }

    static synchronized void install(Clipboard target) {
        if (installed) return;
        clipboard = java.util.Objects.requireNonNull(target);
        Toolkit.getDefaultToolkit().addAWTEventListener(COMPONENT_WATCHER,
            AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK);
        for (Window window : Window.getWindows()) installTree(window);
        installed = true;
    }
    private static void installTree(Component component) {
        if (component instanceof JTextComponent text) installText(text);
        if (component instanceof JComboBox<?> combo) watchEditor(combo);
        if (component instanceof Container container)
            for (Component child : container.getComponents()) installTree(child);
    }
    private static void watchEditor(JComboBox<?> combo) {
        if (Boolean.TRUE.equals(combo.getClientProperty(INSTALLED))) return;
        combo.putClientProperty(INSTALLED, Boolean.TRUE);
        installEditor(combo);
        combo.addPropertyChangeListener("editor", event -> installEditor(combo));
        combo.addPropertyChangeListener("editable", event -> installEditor(combo));
    }
    private static void installEditor(JComboBox<?> combo) {
        if (combo.isEditable() && combo.getEditor() != null
                && combo.getEditor().getEditorComponent() instanceof JTextComponent text)
            installText(text);
    }
    private static void installText(JTextComponent text) {
        if (Boolean.TRUE.equals(text.getClientProperty(INSTALLED))) return;
        text.putClientProperty(INSTALLED, Boolean.TRUE);
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.WHITE);
        JMenuItem cut = item("Cut");
        JMenuItem copy = item("Copy");
        JMenuItem paste = item("Paste");
        JMenuItem selectAll = item("Select All");
        menu.add(cut);
        menu.add(copy);
        menu.add(paste);
        menu.addSeparator();
        menu.add(selectAll);
        menu.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) { update(text, cut, copy, paste, selectAll); }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}
            public void popupMenuCanceled(PopupMenuEvent event) {}
        });
        text.setComponentPopupMenu(menu);
        bind(text, KeyEvent.VK_X, CUT, () -> cut(text));
        bind(text, KeyEvent.VK_C, COPY, () -> copy(text));
        bind(text, KeyEvent.VK_V, PASTE, () -> paste(text));
        bind(text, KeyEvent.VK_A, SELECT_ALL, text::selectAll);
        cut.addActionListener(event -> cut(text));
        copy.addActionListener(event -> copy(text));
        paste.addActionListener(event -> paste(text));
        selectAll.addActionListener(event -> text.selectAll());
    }

    private static JMenuItem item(String label) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.PLAIN));
        item.setForeground(Theme.INK);
        return item;
    }

    private static void bind(JTextComponent text, int key, String name, Runnable operation) {
        int menuMask;
        try {
            menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException noDisplay) {
            menuMask = InputEvent.CTRL_DOWN_MASK;
        }
        int[] masks = {menuMask, InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK};
        for (int mask : masks) {
            KeyStroke stroke = KeyStroke.getKeyStroke(key, mask);
            text.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        }
        text.getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { operation.run(); }
        });
    }

    private static void update(JTextComponent text, JMenuItem cut, JMenuItem copy,
            JMenuItem paste, JMenuItem selectAll) {
        boolean selected = text.getSelectionStart() != text.getSelectionEnd();
        boolean password = text instanceof javax.swing.JPasswordField;
        boolean editable = text.isEditable() && text.isEnabled();
        cut.setEnabled(editable && !password && selected);
        copy.setEnabled(!password && selected);
        paste.setToolTipText(null);
        paste.setEnabled(editable && hasText(paste));
        selectAll.setEnabled(text.getDocument().getLength() > 0);
    }

    private static boolean hasText(JMenuItem paste) {
        try {
            Transferable contents = clipboard.getContents(null);
            return contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)
                && !((String) contents.getTransferData(DataFlavor.stringFlavor)).isEmpty();
        } catch (IllegalStateException | HeadlessException | SecurityException
                | java.awt.datatransfer.UnsupportedFlavorException | IOException unavailable) {
            paste.setToolTipText("Clipboard unavailable. Try again.");
            return false;
        }
    }

    private static void cut(JTextComponent text) {
        if (!text.isEditable() || !text.isEnabled() || text instanceof javax.swing.JPasswordField
                || text.getSelectionStart() == text.getSelectionEnd()) return;
        if (copySelection(text)) text.replaceSelection("");
    }

    private static void copy(JTextComponent text) {
        if (!(text instanceof javax.swing.JPasswordField)
                && text.getSelectionStart() != text.getSelectionEnd()) copySelection(text);
    }

    private static boolean copySelection(JTextComponent text) {
        String selection = text.getSelectedText();
        if (selection == null || selection.isEmpty()) return false;
        try {
            clipboard.setContents(new StringSelection(selection), null);
            return true;
        } catch (IllegalStateException | HeadlessException | SecurityException failure) {
            reportFailure(text);
            return false;
        }
    }

    private static void paste(JTextComponent text) {
        if (!text.isEditable() || !text.isEnabled()) return;
        try {
            Transferable contents = clipboard.getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return;
            String value = (String) contents.getTransferData(DataFlavor.stringFlavor);
            if (text instanceof javax.swing.JTextField)
                value = value.replaceAll("\\R", " ").trim();
            text.replaceSelection(value);
        } catch (IllegalStateException | HeadlessException | SecurityException
                | java.awt.datatransfer.UnsupportedFlavorException | IOException failure) {
            reportFailure(text);
        }
    }

    private static void reportFailure(Component owner) {
        if (java.awt.GraphicsEnvironment.isHeadless())
            throw new IllegalStateException("Clipboard access failed.");
        JOptionPane.showMessageDialog(owner, "Clipboard access failed. Try again.",
            "Clipboard", JOptionPane.ERROR_MESSAGE);
    }
}
