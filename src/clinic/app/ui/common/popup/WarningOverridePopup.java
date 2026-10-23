package clinic.app.ui.common.popup;

import clinic.app.Theme;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Reusable safety confirmation. Free-text reasons stay out of audit details. */
public final class WarningOverridePopup {
    public enum Outcome { CANCELLED, OVERRIDDEN }

    public record Result(Outcome outcome, String reason) {
        public Result {
            if (outcome == null) throw new IllegalArgumentException("Outcome is required.");
            reason = reason == null ? "" : reason.trim();
            if (outcome == Outcome.OVERRIDDEN && reason.length() < 10)
                throw new IllegalArgumentException("Enter a reason of at least 10 characters.");
            if (outcome == Outcome.CANCELLED) reason = "";
        }
    }

    private WarningOverridePopup() {}

    public static Result show(Component owner, String patientId, String medicine,
                              List<String> warnings) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Open safety warnings on the event thread.");
        Window parent = owner instanceof Window window ? window : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(parent, "Safety warning", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        content.setBorder(Theme.padding());
        content.setBackground(Theme.WHITE);

        JLabel header = new JLabel("Safety warning", UIManager.getIcon("OptionPane.warningIcon"), JLabel.LEFT);
        header.setOpaque(true);
        header.setBackground(Theme.DANGER);
        header.setForeground(Theme.WHITE);
        header.setBorder(Theme.padding());
        header.setFont(Theme.font(18, Font.BOLD));
        content.add(header, BorderLayout.NORTH);

        JPanel details = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        // Text components display identifiers literally, including any HTML-like characters.
        JTextArea context = new JTextArea("Patient ID: " + patientId + "\nMedicine: " + medicine);
        context.setEditable(false);
        context.setFont(Theme.font(14, Font.PLAIN));
        details.add(context, BorderLayout.NORTH);
        JTextArea warningText = new JTextArea(String.join("\n", List.copyOf(warnings)), 5, 48);
        warningText.setEditable(false);
        warningText.setLineWrap(true);
        warningText.setWrapStyleWord(true);
        warningText.setForeground(Theme.DANGER);
        warningText.setFont(Theme.font(14, Font.PLAIN));
        details.add(new JScrollPane(warningText), BorderLayout.CENTER);
        JTextField reason = new JTextField(36);
        reason.setName("Override reason");
        Theme.field(reason);
        JLabel reasonLabel = new JLabel("Override reason (at least 10 characters)");
        reasonLabel.setLabelFor(reason);
        JPanel reasonPanel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        reasonPanel.add(reasonLabel, BorderLayout.NORTH);
        reasonPanel.add(reason, BorderLayout.CENTER);
        reasonPanel.add(new JLabel("This override will be recorded in the audit log."), BorderLayout.SOUTH);
        details.add(reasonPanel, BorderLayout.SOUTH);
        content.add(details, BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        JButton override = new JButton("Override with reason");
        for (JButton button : new JButton[]{cancel, override}) {
            button.setFont(Theme.font(14, Font.BOLD));
            button.setForeground(Theme.DANGER);
            button.setBackground(Theme.WHITE);
        }
        override.setEnabled(false);
        reason.getDocument().addDocumentListener(new DocumentListener() {
            private void validateReason() { override.setEnabled(reason.getText().trim().length() >= 10); }
            public void insertUpdate(DocumentEvent e) { validateReason(); }
            public void removeUpdate(DocumentEvent e) { validateReason(); }
            public void changedUpdate(DocumentEvent e) { validateReason(); }
        });
        Result[] result = {new Result(Outcome.CANCELLED, "")};
        cancel.addActionListener(e -> dialog.dispose());
        override.addActionListener(e -> {
            if (reason.getText().trim().length() < 10) return;
            result[0] = new Result(Outcome.OVERRIDDEN, reason.getText());
            dialog.dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(override);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(cancel);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) { cancel.requestFocusInWindow(); }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    /** Compatibility entry point retained for existing callers. */
    public static String reason(Component owner, List<String> warnings) {
        Result result = show(owner, "Not supplied", "Not supplied", warnings);
        return result.outcome() == Outcome.OVERRIDDEN ? result.reason() : null;
    }
}
