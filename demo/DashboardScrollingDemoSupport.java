import clinic.app.ui.common.PagedTablePanel;
import clinic.app.ui.common.SearchBar;
import clinic.app.ui.dashboard.RecentVisitsPanel;
import clinic.app.ui.dashboard.VisitPeriod;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.util.function.Predicate;

final class DashboardScrollingDemoSupport {
    private DashboardScrollingDemoSupport() {}

    static PagedTablePanel<?> findTable(Container root, String modelType) {
        return matching(root, PagedTablePanel.class,
            table -> ((PagedTablePanel<?>) table).modelType().equals(modelType), "Missing table " + modelType);
    }

    static int viewportHeight(PagedTablePanel<?> panel) {
        JTable table = findTableComponent(panel, panel.modelType());
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        return scroll.getViewport().getExtentSize().height;
    }

    static int visibleRows(PagedTablePanel<?> panel) {
        JTable table = findTableComponent(panel, panel.modelType());
        int height = viewportHeight(panel);
        return height <= 0 ? 0 : Math.max(0, table.rowAtPoint(new java.awt.Point(0, height - 1)) + 1);
    }

    static void verifyPageWheel(JScrollPane page) {
        page.getVerticalScrollBar().setValue(0);
        int before = page.getVerticalScrollBar().getValue();
        page.dispatchEvent(wheel(page));
        require(page.getVerticalScrollBar().getValue() > before, "Wheel scrolls the page");
        System.out.println("PASS page wheel: wheel input moves the dashboard page.");
    }

    static void verifyTableBoundaryWheel(Container dashboard, JScrollPane page) {
        JTable table = findTableComponent(dashboard, "RecentVisitsTableModel");
        JScrollPane tableScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        var tableBar = tableScroll.getVerticalScrollBar();
        tableBar.setValue(tableBar.getMaximum() - tableBar.getVisibleAmount());
        page.getVerticalScrollBar().setValue(0);
        int tableBottom = tableBar.getValue();
        table.dispatchEvent(wheel(table));
        require(tableBar.getValue() == tableBottom, "Table remains at its bottom boundary");
        require(page.getVerticalScrollBar().getValue() > 0, "Boundary wheel scrolls the page");
        System.out.println("PASS nested wheel: at a table's bottom, wheel input scrolls the page.");
    }

    static void verifyMinimums(Container dashboard) {
        RecentVisitsPanel recent = find(dashboard, RecentVisitsPanel.class);
        var predictions = find(dashboard, clinic.app.ui.dashboard.MedicinePredictionsPanel.class);
        require(recent.getHeight() >= recent.getMinimumSize().height, "Recent visits card minimum holds");
        require(predictions.getHeight() >= predictions.getMinimumSize().height,
            "Medicine predictions card minimum holds");
    }

    static void verifyNoInnerScrollbar(Container dashboard) {
        layoutTree(dashboard);
        JTable table = findTableComponent(dashboard, "RecentVisitsTableModel");
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        require(!scroll.getVerticalScrollBar().isVisible(),
            "A one-row table does not show an inner vertical scrollbar stub");
        System.out.println("PASS inner scrollbar: a fitting one-row result has no vertical scrollbar stub.");
    }

    static void verifySearchAndPeriod(Container dashboard) {
        JComboBox<?> period = find(dashboard, JComboBox.class);
        PagedTablePanel<?> visits = findTable(dashboard, "RecentVisitsTableModel");
        int before = visits.refreshCount();
        period.setSelectedItem(VisitPeriod.ALL);
        period.setSelectedItem(VisitPeriod.THIS_WEEK);
        require(period.getSelectedItem() == VisitPeriod.THIS_WEEK, "This week filter is selected");
        require(visits.refreshCount() >= before + 2, "Period changes reload the visit table");
        System.out.println("PASS This week filter: changing scope reloads the visit table.");
        require(button(dashboard, "+ New Consultation").isEnabled(), "New Consultation is enabled");
        searchField(dashboard).setText("SCROLL-P");
    }

    static JTextField searchField(Container dashboard) {
        SearchBar search = find(dashboard, SearchBar.class);
        return find(search, JTextField.class);
    }

    static void verifyFooterReachable(Container dashboard, JScrollPane page) {
        page.getVerticalScrollBar().setValue(
            page.getVerticalScrollBar().getMaximum() - page.getVerticalScrollBar().getVisibleAmount());
        JLabel footer = findLabel(dashboard, "Clinic operations for ");
        int footerBottom = SwingUtilities.convertPoint(footer, 0, footer.getHeight(),
            page.getViewport().getView()).y;
        int viewportBottom = page.getVerticalScrollBar().getValue()
            + page.getViewport().getExtentSize().height;
        require(footerBottom <= viewportBottom, "Footer is reachable at page bottom");
        System.out.println("PASS footer: clinic operations / signed-in status is reachable at the bottom.");
    }

    static JTable findTableComponent(Container root, String modelType) {
        return matching(root, JTable.class, table -> table.getModel().getClass()
            .getSimpleName().equals(modelType), "Missing table component " + modelType);
    }

    static JScrollPane findPage(Container root) {
        return matching(root, JScrollPane.class, scroll ->
            scroll.getVerticalScrollBarPolicy() == JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                && scroll.getHorizontalScrollBarPolicy() == JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
            "Missing dashboard page scroll pane");
    }

    static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton candidate && text.equals(candidate.getText())) return candidate;
            if (child instanceof Container nested) {
                JButton found = button(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    static JDialog visibleDialog() {
        for (java.awt.Window window : java.awt.Window.getWindows()) {
            if (window instanceof JDialog dialog && dialog.isShowing()) return dialog;
        }
        return null;
    }

    static JLabel findLabel(Container root, String prefix) {
        return matching(root, JLabel.class, label -> label.getText().startsWith(prefix),
            "Missing footer label");
    }

    static <T> T find(Container root, Class<T> type) {
        return matching(root, type, value -> true, "Missing component " + type.getSimpleName());
    }

    private static <T> T matching(Container root, Class<T> type, Predicate<T> filter, String message) {
        T found = search(root, type, filter);
        if (found == null) throw new AssertionError(message);
        return found;
    }

    private static <T> T search(Container root, Class<T> type, Predicate<T> filter) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && filter.test(type.cast(child))) return type.cast(child);
            if (child instanceof Container nested) {
                T found = search(nested, type, filter);
                if (found != null) return found;
            }
        }
        return null;
    }

    static MouseWheelEvent wheel(Component target) {
        return new MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
            0, Math.max(1, target.getWidth() / 2), Math.max(1, target.getHeight() / 2),
            0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1);
    }

    static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    static void require(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
    }

}
