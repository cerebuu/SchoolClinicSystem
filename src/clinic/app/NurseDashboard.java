package clinic.app;

import clinic.model.User;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Nurse landing page. It only presents workflows that have a backing
 * ClinicSystem contract; data panels remain explicit until an implementation
 * is supplied by the application composition root.
 */
public class NurseDashboard extends JPanel {
    private static final long serialVersionUID = 1L;

    public NurseDashboard(User user, Consumer<String> navigate) {
        super(new BorderLayout(0, 20));
        setBackground(Theme.SUBTLE);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 28, 28));

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);
        String firstName = user.getName().trim().split("\\s+")[0];
        JLabel title = new JLabel("<html><b>Good morning, " + firstName
                + "</b><br><font color='#6B7280'>Your clinic workspace for patient care and safety.</font></html>");
        title.setFont(Theme.font(22, Font.BOLD));
        header.add(title, BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
        actions.setOpaque(false);
        actions.add(actionButton("Find patient", "patients", false, navigate));
        actions.add(actionButton("New consultation", "consultations", true, navigate));
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);
        content.add(createMetrics(), BorderLayout.NORTH);

        JPanel lower = new JPanel(new GridLayout(1, 2, 16, 0));
        lower.setOpaque(false);
        lower.add(workflowCard("Patient care", "Search patients, review visit history, and record consultations.",
                new String[] {"Patients", "Consultations"}, new String[] {"patients", "consultations"}, navigate));
        lower.add(workflowCard("Medicine safety", "Check allergies, dispense medicine, and monitor inventory.",
                new String[] {"Dispense medicine", "Medicine inventory"}, new String[] {"dispense", "inventory"}, navigate));
        content.add(lower, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    private JPanel createMetrics() {
        JPanel metrics = new JPanel(new GridLayout(1, 4, 14, 0));
        metrics.setOpaque(false);
        metrics.add(statusCard("Patient records", "ClinicSystem", "Lookup and visit history"));
        metrics.add(statusCard("Consultations", "ClinicSystem", "Record visits and remarks"));
        metrics.add(statusCard("Safety checks", "ClinicSystem", "Allergy and medicine review"));
        metrics.add(statusCard("Inventory", "ClinicSystem", "Stock and expiry monitoring"));
        return metrics;
    }

    private JPanel statusCard(String label, String value, String detail) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        Theme.surface(card);
        JLabel name = new JLabel(label);
        name.setForeground(Theme.MUTED);
        name.setFont(Theme.font(13, Font.PLAIN));
        JLabel state = new JLabel(value);
        state.setForeground(Theme.BRAND);
        state.setFont(Theme.font(19, Font.BOLD));
        JLabel note = new JLabel(detail);
        note.setForeground(Theme.MUTED);
        note.setFont(Theme.font(12, Font.PLAIN));
        card.add(name);
        card.add(Box.createVerticalStrut(10));
        card.add(state);
        card.add(Box.createVerticalStrut(5));
        card.add(note);
        return card;
    }

    private JPanel workflowCard(String title, String description, String[] labels,
                                String[] pages, Consumer<String> navigate) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        Theme.surface(card);
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.font(17, Font.BOLD));
        JLabel body = new JLabel("<html>" + description + "</html>");
        body.setForeground(Theme.MUTED);
        body.setFont(Theme.font(13, Font.PLAIN));
        card.add(heading);
        card.add(Box.createVerticalStrut(5));
        card.add(body);
        card.add(Box.createVerticalStrut(18));
        for (int i = 0; i < labels.length; i++) {
            card.add(actionButton(labels[i], pages[i], false, navigate));
            if (i < labels.length - 1) {
                card.add(Box.createVerticalStrut(9));
            }
        }
        return card;
    }

    private JButton actionButton(String text, String page, boolean primary,
                                 Consumer<String> navigate) {
        JButton button = Theme.button(text, primary);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        button.addActionListener(e -> navigate.accept(page));
        return button;
    }
}
