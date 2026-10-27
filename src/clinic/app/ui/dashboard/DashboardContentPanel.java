package clinic.app.ui.dashboard;

import clinic.app.Theme;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

public final class DashboardContentPanel extends JPanel implements Scrollable {
    public DashboardContentPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
    }

    public void addSection(JComponent section) {
        if (getComponentCount() > 0) add(Box.createVerticalStrut(Theme.SPACE_4));
        section.setAlignmentX(LEFT_ALIGNMENT);
        add(section);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return DashboardLayout.PAGE_SCROLL_UNIT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL
            ? Math.max(DashboardLayout.PAGE_SCROLL_UNIT, visibleRect.height)
            : Math.max(DashboardLayout.PAGE_SCROLL_UNIT, visibleRect.width);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
