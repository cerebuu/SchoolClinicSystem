package clinic.app.nav;
import clinic.app.ui.common.CampusBadge;
import clinic.app.Theme;
import clinic.app.ui.common.IconPainter;
import clinic.app.ui.common.UicLogo;
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
        this(items,logout,user,superadmin,superadmin?"Administration":
            user.getCampusId()==null?"Campus unassigned":"Campus "+user.getCampusId());
    }
    public Navigator(List<NavItem> items,Runnable logout,User user,boolean superadmin,String campusLabel) {
        super(new BorderLayout());
        JPanel sidebar=new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.NAV);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.NAV_EDGE),
            BorderFactory.createEmptyBorder(24, 16, 16, 16)));
        sidebar.setPreferredSize(new Dimension(248, 0));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_2, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel logoBadge = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D copy = (Graphics2D) graphics.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setColor(Theme.BRAND_TINT);
                copy.fillOval(0, 0, getWidth(), getHeight());
                copy.dispose();
            }
        };
        logoBadge.setOpaque(false);
        logoBadge.setPreferredSize(new Dimension(64, 64));
        logoBadge.setMinimumSize(new Dimension(64, 64));
        logoBadge.setMaximumSize(new Dimension(64, 64));
        logoBadge.add(UicLogo.label(56));
        JLabel brandName = new JLabel("UIC SCMS");
        brandName.setFont(Theme.font(Theme.TYPE_TITLE, java.awt.Font.BOLD));
        brandName.setForeground(Theme.NAV_TEXT);
        brand.add(logoBadge);
        brand.add(brandName);
        sidebar.add(brand);
        JLabel workspace = new JLabel(superadmin ? "Superadmin" :
            User.NURSE.equals(user.getRole()) ? "Nursing operations" : "Clinic workspace");
        workspace.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.BOLD));
        workspace.setForeground(Theme.NAV_MUTED);
        workspace.setBorder(BorderFactory.createEmptyBorder(0, 4, 16, 0));
        workspace.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(workspace);
        if (!superadmin) {
            CampusBadge campus = new CampusBadge(campusLabel);
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
        name.setForeground(Theme.NAV_TEXT);
        JLabel role = new JLabel(user.getRole());
        role.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.PLAIN));
        role.setForeground(Theme.NAV_MUTED);
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
        JScrollPane sidebarScroll = new JScrollPane(sidebar,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.setBorder(BorderFactory.createEmptyBorder());
        sidebarScroll.setBackground(Theme.NAV);
        sidebarScroll.getViewport().setBackground(Theme.NAV);
        sidebarScroll.setPreferredSize(new Dimension(248, 0));
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(sidebarScroll, BorderLayout.WEST);
        pages.setBackground(Theme.SUBTLE);
        add(pages, BorderLayout.CENTER);
        if(!allowed.isEmpty())open(allowed.keySet().iterator().next());
    }
    public void open(String label) {
        NavItem item=allowed.get(label);
        if(item==null)throw new SecurityException("Navigation is not permitted.");
        boolean cached = loaded.containsKey(label);
        if(!cached) {
            JPanel panel=item.factory().get();
            loaded.put(label, panel);
            pages.add(panel, label);
        }
        navigation.forEach((name, button) -> button.setActive(name.equals(label)));
        layout.show(pages, label);
    }
}
