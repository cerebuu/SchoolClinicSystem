package clinic.app.nav;

import clinic.app.Theme;
import clinic.app.ui.common.IconPainter;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JButton;

public final class SidebarItem extends JButton {
    private boolean active;
    private boolean hovered;
    private boolean pressed;

    public SidebarItem(String label) {
        super(label, new IconPainter(label, Theme.NAV_MUTED));
        setHorizontalAlignment(LEADING);
        setIconTextGap(12);
        setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.BOLD));
        setForeground(Theme.NAV_TEXT);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 14, 0, 12));
        setPreferredSize(new java.awt.Dimension(204, 44));
        setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 44));
        getAccessibleContext().setAccessibleName(label);
        getAccessibleContext().setAccessibleDescription("Navigate to " + label + ".");
        getModel().addChangeListener(event -> {
            pressed = getModel().isPressed() && getModel().isArmed();
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent event) { hovered = false; repaint(); }
        });
    }

    public void setActive(boolean selected) {
        boolean previous = active;
        active = selected;
        setIcon(new IconPainter(getText(), selected ? Theme.BRAND_BORDER : Theme.NAV_MUTED));
        getAccessibleContext().setAccessibleDescription(selected
            ? getText() + ". Current page."
            : "Navigate to " + getText() + ".");
        if (previous != selected) {
            getAccessibleContext().firePropertyChange(AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                previous ? AccessibleState.SELECTED : null,
                selected ? AccessibleState.SELECTED : null);
        }
        repaint();
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleSidebarItem();
        return accessibleContext;
    }

    protected class AccessibleSidebarItem extends AccessibleJButton {
        @Override
        public AccessibleStateSet getAccessibleStateSet() {
            AccessibleStateSet states = super.getAccessibleStateSet();
            if (active) states.add(AccessibleState.SELECTED);
            return states;
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (active || hovered || pressed) {
            g.setColor(active || pressed ? Theme.NAV_SELECTED : Theme.NAV_HOVER);
            g.fill(new RoundRectangle2D.Float(3, 2, getWidth() - 6, getHeight() - 4, 12, 12));
        }
        if (active) {
            g.setColor(Theme.BRAND);
            g.fillRoundRect(2, 10, 3, getHeight() - 20, 3, 3);
        }
        if (isFocusOwner()) {
            g.setColor(Theme.NAV_FOCUS);
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Float(4, 3, getWidth() - 8, getHeight() - 6, 10, 10));
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
