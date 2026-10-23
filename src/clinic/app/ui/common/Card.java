package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public class Card extends JPanel {
    public Card() {
        setOpaque(false);
        setBorder(new EmptyBorder(Theme.SPACE_4, Theme.SPACE_4, Theme.SPACE_4, Theme.SPACE_4));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = Theme.RADIUS_CARD;
        g.setColor(Theme.SHADOW_COLOR);
        g.fill(new RoundRectangle2D.Float(2, 3, getWidth() - 4, getHeight() - 5, arc, arc));
        g.setColor(Theme.WHITE);
        g.fill(new RoundRectangle2D.Float(1, 0, getWidth() - 3, getHeight() - 4, arc, arc));
        g.setColor(Theme.BRAND_BORDER);
        g.draw(new RoundRectangle2D.Float(1, 0, getWidth() - 3, getHeight() - 4, arc, arc));
        g.dispose();
        super.paintComponent(graphics);
    }
}
