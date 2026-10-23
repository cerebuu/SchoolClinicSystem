package clinic.app.nav;

import clinic.app.Theme;
import clinic.app.ui.common.IconPainter;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.Timer;

public final class SidebarItem extends JButton {
    private boolean active;
    private boolean hovered;
    private float hoverAmount;

    public SidebarItem(String label) {
        super(label, new IconPainter(label, Theme.MUTED));
        setHorizontalAlignment(LEADING);
        setIconTextGap(12);
        setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.BOLD));
        setForeground(Theme.INK);
        setFocusPainted(true);
        setContentAreaFilled(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 14, 0, 12));
        setPreferredSize(new java.awt.Dimension(204, 44));
        setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 44));
        getAccessibleContext().setAccessibleName(label);
        Timer fade = new Timer(Theme.MOTION_FRAME_MS, event -> {
            float step = (float) Theme.MOTION_FRAME_MS / Theme.MOTION_HOVER_MS;
            hoverAmount += hovered ? step : -step;
            hoverAmount = Math.max(0f, Math.min(1f, hoverAmount));
            repaint();
            if ((hovered && hoverAmount >= 1f) || (!hovered && hoverAmount <= 0f)) {
                ((Timer) event.getSource()).stop();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { hovered = true; fade.restart(); }
            @Override public void mouseExited(MouseEvent event) { hovered = false; fade.restart(); }
        });
    }

    public void setActive(boolean selected) {
        active = selected;
        setForeground(selected ? Theme.BRAND_DARK : Theme.INK);
        setIcon(new IconPainter(getText(), selected ? Theme.BRAND : Theme.MUTED));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (active) {
            g.setColor(Theme.BRAND_GLOW);
            g.fill(new RoundRectangle2D.Float(3, 2, getWidth() - 6, getHeight() - 4, 20, 20));
            g.setColor(Theme.BRAND);
            g.fillRoundRect(2, 10, 3, getHeight() - 20, 3, 3);
        } else if (hoverAmount > 0f) {
            g.setColor(new Color(Theme.BRAND_TINT.getRed(), Theme.BRAND_TINT.getGreen(),
                    Theme.BRAND_TINT.getBlue(), Math.round(150 * hoverAmount)));
            g.fill(new RoundRectangle2D.Float(3, 2, getWidth() - 6, getHeight() - 4, 20, 20));
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
