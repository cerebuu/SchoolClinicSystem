package clinic.app.nav;
import clinic.app.Theme;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
public final class Navigator extends JPanel {
    private final CardLayout layout=new CardLayout();
    private final JPanel pages=new JPanel(layout);
    private final Map<String, JPanel> loaded=new HashMap<>();
    private final Map<String, NavItem> allowed=new LinkedHashMap<>();
    public Navigator(List<NavItem> items, Runnable logout) {
        super(new BorderLayout());
        JPanel sidebar=new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(Theme.padding());
        sidebar.add(new JLabel("UIC SCMS"));
        for(NavItem item:items)if(item.allowed()) {
            allowed.put(item.label(), item);
            JButton button=Theme.button(item.label(), false);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
            button.addActionListener(e->open(item.label()));
            sidebar.add(button);
        }
        sidebar.add(Box.createVerticalGlue());
        JButton signOut=Theme.button("Sign out", true);
        signOut.addActionListener(e->logout.run());
        sidebar.add(signOut);
        add(sidebar, BorderLayout.WEST);
        add(pages, BorderLayout.CENTER);
        if(!allowed.isEmpty())open(allowed.keySet().iterator().next());
    }
    public void open(String label) {
        NavItem item=allowed.get(label);
        if(item==null)throw new SecurityException("Navigation is not permitted.");
        if(!loaded.containsKey(label)) {
            JPanel panel=item.factory().get();
            loaded.put(label, panel);
            pages.add(panel, label);
        }
        layout.show(pages, label);
    }
}
