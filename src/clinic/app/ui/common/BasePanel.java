package clinic.app.ui.common;
import clinic.app.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.*;
public class BasePanel extends JPanel {
    private Runnable refreshAction = () -> {};
    private LiveRefresh live;
    protected final void refreshOn(clinic.app.DataChangeBus bus,clinic.app.DataTopic... topics){live=new LiveRefresh(this,bus,()->refreshAction.run(),topics);}
    protected final boolean refreshActive(){return live==null||live.active();}
    protected final JPanel actions=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
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
        JPanel top=new JPanel(new BorderLayout());
        top.setOpaque(false);
        actions.setOpaque(false);
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
        b.addActionListener(e->run.run());
        actions.add(b);
        return b;
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
