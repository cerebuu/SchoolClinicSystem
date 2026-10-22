package clinic.app.ui.common;
import clinic.app.Theme;
import java.awt.BorderLayout;
import javax.swing.*;
public class BasePanel extends JPanel {
    protected final JPanel actions=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
    public BasePanel(String title) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        setBackground(Theme.SUBTLE);
        setBorder(Theme.padding());
        JPanel top=new JPanel(new BorderLayout());
        JLabel heading=new JLabel(title);
        heading.setFont(Theme.font(22, java.awt.Font.BOLD));
        top.add(heading, BorderLayout.NORTH);
        top.add(actions, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
    }
    protected JButton action(String label, Runnable run) {
        JButton b=Theme.button(label, false);
        b.addActionListener(e->run.run());
        actions.add(b);
        return b;
    }
}
