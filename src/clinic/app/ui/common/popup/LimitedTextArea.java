package clinic.app.ui.common.popup;

import clinic.app.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

/** Reusable multiline form input. Excess pasted characters are truncated at the limit. */
public final class LimitedTextArea extends JPanel {
    private final JTextArea text;
    public LimitedTextArea(FormField field) {
        super(new BorderLayout(0, Theme.SPACE_1));
        setOpaque(false);
        text = new JTextArea(field.rows(), 24);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.PLAIN));
        text.setForeground(Theme.INK);
        text.setBackground(Theme.WHITE);
        text.setSelectionColor(Theme.BRAND_TINT);
        text.getAccessibleContext().setAccessibleName(field.label());
        JLabel counter = new JLabel("0 / " + field.maxLength(), SwingConstants.RIGHT);
        counter.setForeground(Theme.MUTED);
        counter.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.PLAIN));
        ((AbstractDocument) text.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass fb, int offset, String value, AttributeSet attrs)
                    throws BadLocationException { replace(fb, offset, 0, value, attrs); }
            @Override public void replace(FilterBypass fb, int offset, int length, String value, AttributeSet attrs)
                    throws BadLocationException {
                String input = value == null ? "" : value;
                int room = Math.max(0, field.maxLength() - fb.getDocument().getLength() + length);
                super.replace(fb, offset, length, input.substring(0, Math.min(room, input.length())), attrs);
            }
        });
        text.getDocument().addDocumentListener(new DocumentListener() {
            private void update() { counter.setText(text.getDocument().getLength() + " / " + field.maxLength()); }
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });
        text.setText(field.initial());
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        add(counter, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(300, 95));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
    }
    public String getText() { return text.getText().trim(); }
}
