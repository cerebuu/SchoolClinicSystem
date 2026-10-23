package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.Icon;

public final class IconPainter implements Icon {
    private final String name;
    private final Color color;

    public IconPainter(String name, Color color) {
        this.name = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        this.color = color;
    }

    @Override public int getIconWidth() { return 18; }
    @Override public int getIconHeight() { return 18; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
            case "dashboard", "admin dashboard" -> {
                g.drawRoundRect(2, 2, 6, 6, 2, 2);
                g.drawRoundRect(10, 2, 6, 6, 2, 2);
                g.drawRoundRect(2, 10, 6, 6, 2, 2);
                g.drawRoundRect(10, 10, 6, 6, 2, 2);
            }
            case "patients", "users" -> {
                g.drawOval(6, 2, 6, 6);
                g.drawArc(3, 9, 12, 9, 0, 180);
            }
            case "campuses" -> {
                g.drawRoundRect(3, 4, 12, 11, 2, 2);
                g.drawLine(6, 8, 6, 8);
                g.drawLine(9, 8, 9, 8);
                g.drawLine(12, 8, 12, 8);
                g.drawLine(9, 15, 9, 11);
            }
            case "sign out" -> {
                g.drawLine(2, 9, 12, 9);
                g.drawLine(9, 5, 13, 9);
                g.drawLine(9, 13, 13, 9);
                g.drawRoundRect(5, 2, 11, 14, 2, 2);
            }
            default -> {
                Path2D mark = new Path2D.Float();
                mark.moveTo(9, 1);
                mark.curveTo(4, 2, 2, 5, 2, 9);
                mark.curveTo(2, 13, 5, 16, 9, 17);
                mark.curveTo(13, 16, 16, 13, 16, 9);
                mark.curveTo(16, 5, 13, 2, 9, 1);
                mark.closePath();
                g.draw(mark);
                g.drawLine(9, 5, 9, 10);
                g.fillOval(8, 13, 2, 2);
            }
        }
        g.dispose();
    }
}
