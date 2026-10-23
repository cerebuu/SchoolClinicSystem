package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.GridBagLayout;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class EmptyState extends JPanel {
    public EmptyState(String title, String hint) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title, JLabel.CENTER);
        heading.setFont(Theme.font(Theme.TYPE_TITLE, Font.BOLD));
        heading.setForeground(Theme.INK);
        heading.setAlignmentX(CENTER_ALIGNMENT);
        JLabel detail = new JLabel(hint, JLabel.CENTER);
        detail.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
        detail.setForeground(Theme.MUTED);
        detail.setAlignmentX(CENTER_ALIGNMENT);
        content.add(heading);
        content.add(javax.swing.Box.createVerticalStrut(Theme.SPACE_2));
        content.add(detail);
        add(content);
    }
}
