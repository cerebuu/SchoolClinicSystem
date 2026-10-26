package clinic.app.ui.common.popup;

import clinic.app.Theme;
import clinic.util.AllergyParser;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;

/** Comma-separated input with a bounded, debounced neutral chip preview. */
public final class AllergyField extends JPanel {
    private final JTextField input = new JTextField(24) {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                g.setColor(Theme.MUTED);
                g.setFont(getFont());
                g.drawString("e.g. Penicillin, Aspirin, Peanuts", getInsets().left,
                    (getHeight()+g.getFontMetrics().getAscent()-g.getFontMetrics().getDescent())/2);
            }
        }
    };
    private final JPanel chips = new JPanel();
    private final JLabel error = new JLabel(" ");
    private final Timer debounce = new Timer(300, e -> refresh());
    public AllergyField(String initial) {
        super(new BorderLayout(0, Theme.SPACE_1));
        setOpaque(false);
        Theme.field(input);
        input.getDocument().putProperty("filterNewlines", Boolean.FALSE);
        input.getAccessibleContext().setAccessibleName("Allergies");
        input.getAccessibleContext().setAccessibleDescription("e.g. Penicillin, Aspirin, Peanuts. Separate with commas");
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
        top.add(input, BorderLayout.NORTH);
        JLabel hint = new JLabel("Separate with commas"); hint.setForeground(Theme.MUTED);
        hint.setFont(Theme.font(Theme.TYPE_CAPTION, Font.PLAIN)); top.add(hint, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        chips.setLayout(new BoxLayout(chips, BoxLayout.Y_AXIS)); chips.setBackground(Theme.WHITE);
        JScrollPane scroll = new JScrollPane(chips);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        error.setForeground(Theme.DANGER); error.setFont(Theme.font(Theme.TYPE_CAPTION, Font.PLAIN));
        add(error, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(300,175)); setMaximumSize(new Dimension(Integer.MAX_VALUE,175));
        debounce.setRepeats(false);
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });
        input.setText(initial); refresh();
    }
    public String getText() { return input.getText(); }
    private void refresh() {
        chips.removeAll(); error.setText(" ");
        try {
            for (String allergy : AllergyParser.parse(getText())) {
                JButton chip = new JButton(allergy + "  ×");
                chip.setBackground(Theme.SUBTLE); chip.setForeground(Theme.INK);
                chip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE),
                    BorderFactory.createEmptyBorder(3,8,3,8)));
                chip.setOpaque(true);
                chip.setContentAreaFilled(false);
                chip.getAccessibleContext().setAccessibleName("Remove allergy " + allergy);
                chip.addActionListener(e -> {
                    try {
                        input.setText(String.join(", ", AllergyParser.parse(getText()).stream()
                            .filter(a -> !a.equalsIgnoreCase(allergy)).toList()));
                        debounce.stop(); refresh();
                    } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
                });
                chips.add(chip);
            }
        } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
        chips.revalidate(); chips.repaint();
    }
    @Override public void removeNotify() { debounce.stop(); super.removeNotify(); }
}
