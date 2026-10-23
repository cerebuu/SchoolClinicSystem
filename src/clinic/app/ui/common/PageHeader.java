package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class PageHeader extends JPanel {
    public PageHeader(String title, String subtitle, Component accessory) {
        super(new BorderLayout(Theme.SPACE_4, Theme.SPACE_2));
        setOpaque(false);
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.font(Theme.TYPE_PAGE, Font.BOLD));
        heading.setForeground(Theme.INK);
        JPanel text = new JPanel(new BorderLayout(0, Theme.SPACE_2));
        text.setOpaque(false);
        text.add(heading, BorderLayout.NORTH);
        add(text, BorderLayout.CENTER);
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel description = new JLabel(subtitle);
            description.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
            description.setForeground(Theme.MUTED);
            text.add(description, BorderLayout.CENTER);
        }
        if (accessory != null) add(accessory, BorderLayout.EAST);
    }
}
