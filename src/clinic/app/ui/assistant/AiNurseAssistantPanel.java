package clinic.app.ui.assistant;

import clinic.ai.AiServiceException;
import clinic.ai.GeminiAiService;
import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.AsyncLoader;
import clinic.app.ui.common.BasePanel;
import clinic.app.ui.common.Card;
import clinic.model.SchoolNurse;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

/** Nurse-only, session-local chat UI. It never reads clinic data or stores chat history. */
public final class AiNurseAssistantPanel extends BasePanel {
    private final JPanel messages = new JPanel();
    private final JScrollPane conversation;
    private final JTextArea input = new JTextArea(3, 1);
    private final JCheckBox privacy = new JCheckBox("I confirm this contains no student identifiers, records, or credentials.");
    private final JLabel status = new JLabel("Use general, non-identifying questions only.");
    private JButton send;
    private boolean pending;

    public AiNurseAssistantPanel(ApplicationContext app) {
        super("AI Nurse Assistant", "Clinic records");
        if (!(app.current() instanceof SchoolNurse)) throw new SecurityException("AI Nurse Assistant access denied.");
        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        messages.setBackground(Theme.SUBTLE);
        messages.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_3, Theme.SPACE_3, Theme.SPACE_3, Theme.SPACE_3));
        conversation = new JScrollPane(messages, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        conversation.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        conversation.getVerticalScrollBar().setUnitIncrement(16);
        add(conversation, BorderLayout.CENTER);
        addComposer();
        action("Clear conversation", () -> clear());
        addAssistant("I can help with SCMS navigation, general clinic administration, or a non-identifying draft. I cannot access records, diagnose, prescribe, or take actions in SCMS.\n\nTry: “How do I record a consultation?”");
    }

    private void addComposer() {
        JPanel composer = new JPanel(new BorderLayout(Theme.SPACE_2, Theme.SPACE_2));
        composer.setOpaque(false);
        composer.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_3, 0, 0, 0));
        input.setLineWrap(true); input.setWrapStyleWord(true); input.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
        input.setToolTipText("Ask a non-identifying question. Enter sends; Shift+Enter adds a new line.");
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "send-ai-message");
        input.getActionMap().put("send-ai-message", new javax.swing.AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { submit(); } });
        composer.add(new JScrollPane(input), BorderLayout.CENTER);
        send = Theme.button("Send", true);
        send.addActionListener(e -> submit());
        composer.add(send, BorderLayout.EAST);
        JPanel privacyLine = new JPanel();
        privacyLine.setLayout(new BoxLayout(privacyLine, BoxLayout.Y_AXIS));
        privacyLine.setOpaque(false);
        privacy.setOpaque(false); privacy.setFont(Theme.font(Theme.TYPE_CAPTION, Font.PLAIN));
        privacy.setAlignmentX(Component.LEFT_ALIGNMENT);
        privacyLine.add(privacy);
        status.setForeground(Theme.MUTED); status.setFont(Theme.font(Theme.TYPE_CAPTION, Font.PLAIN));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        status.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_1, 2, 0, 2));
        privacyLine.add(status);
        JPanel south = new JPanel(new BorderLayout()); south.setOpaque(false);
        south.add(privacyLine, BorderLayout.NORTH); south.add(composer, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void clear() {
        if (pending) return;
        messages.removeAll();
        addAssistant("Conversation cleared. Do not include student names, IDs, contact details, records, or credentials.");
        input.setText("");
        privacy.setSelected(false);
        status.setForeground(Theme.MUTED);
        status.setText("Use general, non-identifying questions only.");
    }
    private void submit() {
        if (pending) return;
        String question = input.getText().trim();
        if (question.isEmpty()) { showError("Enter a question for the assistant."); return; }
        if (!privacy.isSelected()) { showError("Confirm that the message contains no identifying or sensitive record information."); return; }
        final GeminiAiService service;
        try { service = GeminiAiService.fromEnvironment(); }
        catch (AiServiceException error) { showError(error.getMessage()); return; }
        pending = true; send.setEnabled(false); input.setEnabled(false); privacy.setEnabled(false);
        status.setForeground(Theme.MUTED); status.setText("AI is thinking…");
        addNurse(question); input.setText("");
        AsyncLoader.load(() -> service.ask(question), answer -> {
            pending = false; send.setEnabled(true); input.setEnabled(true); privacy.setEnabled(true);
            status.setForeground(Theme.SUCCESS); status.setText("Review AI drafts before use."); addAssistant(answer);
        }, error -> {
            pending = false; send.setEnabled(true); input.setEnabled(true); privacy.setEnabled(true);
            showError(error.getMessage() == null ? "The AI request failed. Please try again." : error.getMessage());
        });
    }
    private void showError(String message) { status.setForeground(Theme.DANGER); status.setText(message); }
    private void addAssistant(String text) { addMessage("AI Nurse Assistant", text, Theme.BRAND_TINT, Theme.BRAND_DARK); }
    private void addNurse(String text) { addMessage("You", text, Theme.WHITE, Theme.INK); }
    private void addMessage(String author, String text, java.awt.Color background, java.awt.Color heading) {
        Card bubble = new Card();
        bubble.setLayout(new BorderLayout(0, Theme.SPACE_1));
        bubble.setBackground(background);
        JLabel who = new JLabel(author);
        who.setFont(Theme.font(Theme.TYPE_CAPTION, Font.BOLD));
        who.setForeground(heading);
        JTextArea body = new JTextArea(text);
        body.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
        body.setForeground(Theme.INK);
        body.setLineWrap(true); body.setWrapStyleWord(true); body.setEditable(false); body.setOpaque(false);
        body.setFocusable(false);
        bubble.add(who, BorderLayout.NORTH); bubble.add(body, BorderLayout.CENTER);
        bubble.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        messages.add(bubble); messages.add(Box.createVerticalStrut(Theme.SPACE_2));
        messages.revalidate(); messages.repaint();
        javax.swing.SwingUtilities.invokeLater(() -> conversation.getVerticalScrollBar().setValue(conversation.getVerticalScrollBar().getMaximum()));
    }
}
