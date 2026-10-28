package clinic.app.ui.common;
import clinic.app.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.*;
public class BasePanel extends JPanel {
    private Runnable refreshAction = () -> {};
    private LiveRefresh live;
    protected final void refreshOn(clinic.app.DataChangeBus bus,clinic.app.DataTopic... topics){live=new LiveRefresh(this,bus,()->refreshAction.run(),topics);}
    protected final boolean refreshActive(){return live==null||live.active();}
    protected final JPanel actions=new JPanel(new BorderLayout(Theme.SPACE_4,0));
    private final JPanel primaryActions=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,Theme.SPACE_2,0));
    private final JPanel secondaryActions=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,Theme.SPACE_2,0));
    public BasePanel(String title) {
        this(title, (String)null);
    }
    public BasePanel(String title,clinic.app.ApplicationContext app) {
        this(title,java.util.Objects.requireNonNull(app).campusLabel());
    }
    public BasePanel(String title, String campusLabel) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        setBackground(Theme.SUBTLE);
        setBorder(Theme.padding());
        JPanel top=new JPanel(new BorderLayout(0,Theme.SPACE_3));
        top.setOpaque(false);
        actions.setBackground(Theme.WHITE);
        actions.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.LINE),
            BorderFactory.createEmptyBorder(Theme.SPACE_2,Theme.SPACE_2,Theme.SPACE_2,Theme.SPACE_2)));
        primaryActions.setOpaque(false);
        secondaryActions.setOpaque(false);
        actions.add(primaryActions,BorderLayout.WEST);
        actions.add(secondaryActions,BorderLayout.EAST);
        actions.setVisible(false);
        Component badge = campusLabel == null ? null : new CampusBadge(campusLabel);
        top.add(new PageHeader(title, null, badge), BorderLayout.NORTH);
        top.add(actions, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
    }
    protected JButton action(String label, Runnable run) {
        return action(label, false, run);
    }
    protected JButton action(String label, boolean primary, Runnable run) {
        JButton b=Theme.button(label, primary);
        String icon=actionIcon(label);
        if(icon!=null) {
            b.setIcon(new IconPainter(icon,primary?Color.WHITE:Theme.BRAND_DARK));
            b.setIconTextGap(Theme.SPACE_2);
            b.setPreferredSize(null);
            b.setPreferredSize(new java.awt.Dimension(
                Math.max(100,b.getPreferredSize().width),Theme.CONTROL_HEIGHT));
        }
        b.addActionListener(e->run.run());
        (primary?primaryActions:secondaryActions).add(b);
        actions.setVisible(true);
        return b;
    }

    private static String actionIcon(String label) {
        String lower=label.toLowerCase(java.util.Locale.ROOT);
        if(lower.startsWith("add ")||lower.startsWith("register ")||lower.startsWith("create "))return "add";
        if(lower.startsWith("edit "))return "edit";
        if(lower.equals("refresh")||lower.startsWith("retry"))return "refresh";
        if(lower.startsWith("deactivate")||lower.startsWith("cancel request"))return "cancel";
        if(lower.startsWith("view "))return "view";
        if(lower.startsWith("clear "))return "clear";
        return null;
    }
    protected final void onRefresh(Runnable refresh) { refreshAction = refresh; }
    public final void refreshPage() {
        refreshAction.run();
        refreshTables(this);
    }
    private static void refreshTables(java.awt.Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof PagedTablePanel<?> table) table.reload();
            else if (child instanceof java.awt.Container container) refreshTables(container);
        }
    }
}
