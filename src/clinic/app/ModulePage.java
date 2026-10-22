package clinic.app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Honest empty/service-state page used when the domain implementation is absent. */
public class ModulePage extends JPanel {
    private static final long serialVersionUID = 1L;

    public ModulePage(String title, String description, String contract) {
        super(new BorderLayout());
        setBackground(Theme.SUBTLE);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 28, 28));

        JPanel card = new JPanel(new BorderLayout(0, 14));
        Theme.surface(card);
        JLabel heading = new JLabel("<html><b>" + title + "</b><br><font color='#6B7280'>"
                + description + "</font></html>");
        heading.setFont(Theme.font(21, Font.BOLD));
        card.add(heading, BorderLayout.NORTH);

        JLabel state = new JLabel("<html><b>This module is ready for the clinic service.</b><br>"
                + "The current project exposes the " + contract
                + " contract, but no concrete data provider is configured yet. "
                + "No records were fabricated and no write action was simulated.</html>");
        state.setForeground(Theme.INK);
        state.setFont(Theme.font(14, Font.PLAIN));
        state.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BRAND_BORDER),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        card.add(state, BorderLayout.CENTER);

        JLabel note = new JLabel("Service state · waiting for application composition");
        note.setForeground(Theme.MUTED);
        note.setFont(Theme.font(12, Font.PLAIN));
        card.add(note, BorderLayout.SOUTH);
        add(card, BorderLayout.NORTH);
    }
}
