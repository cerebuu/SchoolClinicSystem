package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class KpiCard extends Card {
    private final JLabel value = new JLabel("—");
    private final JLabel caption = new JLabel(" ");

    public KpiCard(String label, String initialValue, String initialCaption) {
        super();
        setLayout(new BorderLayout(Theme.SPACE_2, Theme.SPACE_1));
        JLabel heading = new JLabel(label);
        heading.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
        heading.setForeground(Theme.MUTED);
        value.setFont(Theme.font(Theme.TYPE_METRIC, Font.BOLD));
        value.setForeground(Theme.INK);
        caption.setFont(Theme.font(Theme.TYPE_CAPTION, Font.PLAIN));
        caption.setForeground(Theme.MUTED);
        JPanel text = new JPanel(new BorderLayout(0, Theme.SPACE_1));
        text.setOpaque(false);
        text.add(heading, BorderLayout.NORTH);
        text.add(value, BorderLayout.CENTER);
        text.add(caption, BorderLayout.SOUTH);
        add(text, BorderLayout.CENTER);
        add(new SparkAccent(), BorderLayout.EAST);
        setValue(initialValue);
        setCaption(initialCaption);
    }

    public void setValue(String text) {
        value.setText(text == null ? "—" : text);
    }

    public void setCaption(String text) {
        caption.setText(text == null ? " " : text);
    }

    public void setSampleCaption(String text) {
        caption.setForeground(Theme.MUTED);
        setCaption(text);
    }

    private static final class SparkAccent extends JPanel {
        private SparkAccent() {
            setOpaque(false);
            setPreferredSize(new java.awt.Dimension(28, 38));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(Theme.BRAND.getRed(), Theme.BRAND.getGreen(), Theme.BRAND.getBlue(), 55));
            g.fillRoundRect(5, 4, 5, 20, 4, 4);
            g.setColor(Theme.BRAND);
            g.fillRoundRect(14, 0, 5, 28, 4, 4);
            g.dispose();
        }
    }
}
