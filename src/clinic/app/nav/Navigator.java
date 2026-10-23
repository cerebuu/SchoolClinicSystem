package clinic.app.nav;
import clinic.app.ui.common.CampusBadge;
import clinic.app.Theme;
import clinic.app.ui.common.IconPainter;
import clinic.model.User;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
public final class Navigator extends JPanel {
    private final CardLayout layout=new CardLayout();
    private final JPanel pages=new JPanel(layout);
    private final Map<String, JPanel> loaded=new HashMap<>();
    private final Map<String, NavItem> allowed=new LinkedHashMap<>();
    private final Map<String, SidebarItem> navigation = new LinkedHashMap<>();
    public Navigator(List<NavItem> items, Runnable logout, User user, boolean superadmin) {
        super(new BorderLayout());
        JPanel sidebar=new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.WHITE);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE),
            BorderFactory.createEmptyBorder(24, 16, 16, 16)));
        sidebar.setPreferredSize(new Dimension(248, 0));
        JLabel brand = new JLabel("  UIC SCMS", new IconPainter("dashboard", Theme.BRAND), JLabel.LEFT);
        brand.setFont(Theme.font(Theme.TYPE_TITLE, java.awt.Font.BOLD));
        brand.setForeground(Theme.INK);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 4, 10, 0));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(brand);
        JLabel workspace = new JLabel(superadmin ? "Superadmin" : "Clinic workspace");
        workspace.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.BOLD));
        workspace.setForeground(Theme.MUTED);
        workspace.setBorder(BorderFactory.createEmptyBorder(0, 4, 16, 0));
        workspace.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(workspace);
        if (!superadmin) {
            CampusBadge campus = CampusBadge.sample();
            campus.setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebar.add(campus);
            sidebar.add(Box.createVerticalStrut(Theme.SPACE_4));
        }
        for(NavItem item:items)if(item.allowed()) {
            allowed.put(item.label(), item);
            SidebarItem button=new SidebarItem(item.label());
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            navigation.put(item.label(), button);
            button.addActionListener(e->open(item.label()));
            sidebar.add(button);
            sidebar.add(Box.createVerticalStrut(Theme.SPACE_1));
        }
        JPanel userBlock = new JPanel();
        userBlock.setLayout(new BoxLayout(userBlock, BoxLayout.Y_AXIS));
        userBlock.setOpaque(false);
        JLabel name = new JLabel(user.getName());
        name.setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.BOLD));
        name.setForeground(Theme.INK);
        JLabel role = new JLabel(user.getRole());
        role.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.PLAIN));
        role.setForeground(Theme.MUTED);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        role.setAlignmentX(Component.LEFT_ALIGNMENT);
        userBlock.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        userBlock.add(name);
        userBlock.add(Box.createVerticalStrut(3));
        userBlock.add(role);
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(userBlock);
        JButton signOut=Theme.button("Sign out", false);
        signOut.setIcon(new IconPainter("sign out", Theme.BRAND));
        signOut.setHorizontalAlignment(SwingConstants.LEFT);
        signOut.setAlignmentX(Component.LEFT_ALIGNMENT);
        signOut.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        signOut.addActionListener(e->logout.run());
        sidebar.add(signOut);
        add(sidebar, BorderLayout.WEST);
        pages.setBackground(Theme.SUBTLE);
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
        navigation.forEach((name, button) -> button.setActive(name.equals(label)));
        layout.show(pages, label);
    }
}
