package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.*;

/** Reusable local search input. The callback schedules work; it never scans data here. */
public final class SearchBar extends JPanel {
    private final JTextField input;
    private final Timer debounce;

    public SearchBar(String placeholder, Consumer<String> changed) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        input = new JTextField() {
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getText().isEmpty() && !hasFocus()) {
                    graphics.setColor(Theme.MUTED);
                    Insets insets = getInsets();
                    int baseline = (getHeight() - graphics.getFontMetrics().getHeight()) / 2
                        + graphics.getFontMetrics().getAscent();
                    graphics.drawString(placeholder, insets.left, baseline);
                }
            }
        };
        Theme.field(input);
        input.getAccessibleContext().setAccessibleName(placeholder);
        input.setToolTipText(placeholder);
        JButton clear = Theme.button("x", false);
        clear.setPreferredSize(new Dimension(Theme.FIELD_HEIGHT, Theme.FIELD_HEIGHT));
        clear.setToolTipText("Clear search");
        clear.getAccessibleContext().setAccessibleName("Clear search");
        clear.addActionListener(event -> { input.setText(""); input.requestFocusInWindow(); });
        debounce = new Timer(300, event -> changed.accept(text()));
        debounce.setRepeats(false);
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });
        add(input, BorderLayout.CENTER);
        add(clear, BorderLayout.EAST);
    }

    public String text() { return input.getText().trim(); }
    public void setText(String text) { input.setText(text); }
    public void removeNotify() { debounce.stop(); super.removeNotify(); }
}
