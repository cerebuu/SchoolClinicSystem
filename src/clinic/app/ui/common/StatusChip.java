package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JLabel;

public final class StatusChip extends JLabel {
    public enum Kind { NEUTRAL, SUCCESS, WARNING, DANGER, INFO }
    private final Color tint;
    private boolean selected;

    public StatusChip(String text, Kind kind) {
        super(text);
        setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.BOLD));
        setForeground(foreground(kind));
        tint = tint(kind);
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 10, 4, 10));
        getAccessibleContext().setAccessibleName(text);
    }

    private Color foreground(Kind kind) {
        return switch (kind) {
            case SUCCESS -> Theme.SUCCESS;
            case WARNING -> new Color(0x8A5A00);
            case DANGER -> Theme.DANGER;
            case INFO -> Theme.INFO;
            case NEUTRAL -> Theme.MUTED;
        };
    }

    private Color tint(Kind kind) {
        return switch (kind) {
            case SUCCESS -> Theme.SUCCESS_TINT;
            case WARNING -> Theme.WARNING_TINT;
            case DANGER -> Theme.DANGER_TINT;
            case INFO -> Theme.INFO_TINT;
            case NEUTRAL -> Theme.SUBTLE;
        };
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(selected ? Theme.BRAND_TINT : tint);
        g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
        g.dispose();
        super.paintComponent(graphics);
    }
}
