package clinic.app.ui.dashboard;

import clinic.app.Theme;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public final class DashboardLayout {
    public static final int VISIBLE_ROWS = 10;
    public static final int ROW_HEIGHT = 28;
    public static final int TABLE_AREA_HEIGHT = 320;
    public static final int PAGE_SCROLL_UNIT = 16;

    private DashboardLayout() {}

    public static void sizeCard(JComponent card) {
        Dimension preferred = card.getPreferredSize();
        int height = Math.max(preferred.height, TABLE_AREA_HEIGHT);
        card.setMinimumSize(new Dimension(0, height));
        card.setPreferredSize(new Dimension(preferred.width, height));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    public static JScrollPane scrollPage(DashboardContentPanel content) {
        JScrollPane page = new JScrollPane(content,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        page.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        page.setBackground(Theme.SUBTLE);
        page.getViewport().setBackground(Theme.SUBTLE);
        page.getVerticalScrollBar().setUnitIncrement(PAGE_SCROLL_UNIT);
        Runnable updateBlockIncrement = () -> page.getVerticalScrollBar()
            .setBlockIncrement(Math.max(PAGE_SCROLL_UNIT, page.getViewport().getExtentSize().height));
        page.getViewport().addChangeListener(event -> updateBlockIncrement.run());
        page.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                updateBlockIncrement.run();
            }
        });
        WheelForwarder.install(page);
        return page;
    }
}
