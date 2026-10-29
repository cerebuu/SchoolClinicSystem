package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JLabel;

public final class CampusBadge extends JLabel {
    public CampusBadge(String campus) {
        super(campus);
        setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.BOLD));
        setForeground(Theme.BRAND_DARK);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
        setOpaque(false);
        getAccessibleContext().setAccessibleName(campus);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(Theme.BRAND_TINT.getRed(), Theme.BRAND_TINT.getGreen(),
                Theme.BRAND_TINT.getBlue(), 255));
        g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
        g.setColor(Theme.BRAND_BORDER);
        g.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1,
                getHeight(), getHeight()));
        g.dispose();
        super.paintComponent(graphics);
    }
}
