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
    private final JTextArea status = new JTextArea("Use general, non-identifying questions only.", 3, 1);
    private final ApplicationContext app;
    private final Runnable sessionGuard;
    private javax.swing.SwingWorker<String, Void> request;
    private int generation;
    private JButton send;
    private boolean pending;

    public AiNurseAssistantPanel(ApplicationContext app) {
        super("AI Nurse Assistant", "Clinic records");
        this.app = app;
        this.sessionGuard = app.nurseSessionGuard();
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
        action("Cancel request", () -> cancelRequest());
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) cancelRequest();
        });
        addAssistant("I can help with SCMS navigation, general clinic administration, or a non-identifying draft. I cannot access records, diagnose, prescribe, or take actions in SCMS.\n\nTry: “How do I record a consultation?”");
    }

    private void addComposer() {
        JPanel composer = new JPanel(new BorderLayout(Theme.SPACE_2, Theme.SPACE_2));
        composer.setOpaque(false);
        composer.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_3, 0, 0, 0));
        input.setLineWrap(true); input.setWrapStyleWord(true); input.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
        input.setToolTipText("Ask a non-identifying question. Enter sends; Shift+Enter adds a new line.");
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "send-ai-message");
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        input.getAccessibleContext().setAccessibleName("Assistant question");
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
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setOpaque(false);
        status.getAccessibleContext().setAccessibleName("Assistant status");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        status.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_1, 2, 0, 2));
        privacyLine.add(status);
        JPanel south = new JPanel(new BorderLayout()); south.setOpaque(false);
        south.add(privacyLine, BorderLayout.NORTH); south.add(composer, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void clear() {
        cancelRequest();
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
        try { sessionGuard.run(); service = GeminiAiService.fromEnvironment(app); }
        catch (AiServiceException | SecurityException error) { showError(error.getMessage()); return; }
        pending = true; send.setEnabled(false); input.setEnabled(false); privacy.setEnabled(false);
        status.setForeground(Theme.MUTED); status.setText("AI is thinking…");
        final int current = ++generation;
        request = AsyncLoader.load(() -> service.ask(question), answer -> {
            if (current != generation) return;
            pending = false; send.setEnabled(true); input.setEnabled(true); privacy.setEnabled(true);
            try { sessionGuard.run(); } catch (SecurityException expired) { showError(expired.getMessage()); return; }
            addNurse(question); input.setText(""); privacy.setSelected(false);
            status.setForeground(Theme.SUCCESS); status.setText("Review AI drafts before use."); addAssistant("Draft for nurse review\n\n" + answer);
        }, error -> {
            if (current != generation) return;
            pending = false; send.setEnabled(true); input.setEnabled(true); privacy.setEnabled(true);
            showError(error instanceof AiServiceException || error instanceof SecurityException
                ? error.getMessage() : "The AI request failed. Your question is retained; use Send to retry.");
        });
    }
    private void cancelRequest() {
        generation++;
        if (request != null) { request.cancel(true); request = null; }
        if (pending) status.setText("Request cancelled. Your question is retained.");
        pending = false;
        if (send != null) send.setEnabled(true);
        input.setEnabled(true); privacy.setEnabled(true);
    }
    @Override public void removeNotify() {
        cancelRequest(); messages.removeAll(); input.setText(""); privacy.setSelected(false);
        super.removeNotify();
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
        while (messages.getComponentCount() > 40) { messages.remove(0); messages.remove(0); }
        messages.revalidate(); messages.repaint();
        javax.swing.SwingUtilities.invokeLater(() -> conversation.getVerticalScrollBar().setValue(conversation.getVerticalScrollBar().getMaximum()));
    }
}
