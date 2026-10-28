package clinic.app.ui.dashboard;

import clinic.app.Theme;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.JPanel;

/** Dashboard metric grid that keeps four columns only when cards have room. */
public final class ResponsiveKpiGrid extends JPanel {
    public static final int FOUR_COLUMN_MIN_WIDTH = 720;

    private int columns = 4;

    public ResponsiveKpiGrid(Component... metrics) {
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setLayout(layout(columns));
        for (Component metric : metrics) add(metric);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                updateColumns();
            }
        });
    }

    private GridLayout layout(int columnCount) {
        return new GridLayout(0, columnCount, Theme.SPACE_3, Theme.SPACE_3);
    }

    private void updateColumns() {
        int desired = getWidth() < FOUR_COLUMN_MIN_WIDTH ? 2 : 4;
        if (desired == columns) return;
        columns = desired;
        setLayout(layout(columns));
        revalidate();
    }

    @Override public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
