package clinic.app.ui.dashboard;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;

public final class WheelForwarder {
    private WheelForwarder() {}

    public static void install(JScrollPane page) {
        installOnScrollPanes(page.getViewport().getView(), page);
    }

    private static void installOnScrollPanes(Component component, JScrollPane page) {
        if (component instanceof JScrollPane inner) {
            Component view = inner.getViewport().getView();
            if (view instanceof JTable table) {
                MouseWheelListener forwarder = event -> forward(event, inner, page);
                table.addMouseWheelListener(forwarder);
                table.getTableHeader().addMouseWheelListener(forwarder);
            } else {
                installOnView(view, inner, page);
            }
            return;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) installOnScrollPanes(child, page);
        }
    }

    private static void installOnView(Component component, JScrollPane inner, JScrollPane page) {
        MouseWheelListener forwarder = event -> forward(event, inner, page);
        component.addMouseWheelListener(forwarder);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) installOnView(child, inner, page);
        }
    }

    private static void forward(MouseWheelEvent event, JScrollPane tablePane, JScrollPane page) {
        if (event.isShiftDown()) return;
        int units = event.getUnitsToScroll();
        if (units == 0) return;
        JScrollBar tableBar = tablePane.getVerticalScrollBar();
        if (canScroll(tableBar, units)) {
            scroll(tableBar, units);
        } else {
            JScrollBar pageBar = page.getVerticalScrollBar();
            if (canScroll(pageBar, units)) scroll(pageBar, units);
        }
        event.consume();
    }

    private static boolean canScroll(JScrollBar bar, int units) {
        int minimum = bar.getMinimum();
        int maximum = bar.getMaximum() - bar.getVisibleAmount();
        return units < 0 ? bar.getValue() > minimum : bar.getValue() < maximum;
    }

    private static void scroll(JScrollBar bar, int units) {
        int value = bar.getValue() + units * DashboardLayout.PAGE_SCROLL_UNIT;
        int maximum = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
        bar.setValue(Math.max(bar.getMinimum(), Math.min(maximum, value)));
    }
}
