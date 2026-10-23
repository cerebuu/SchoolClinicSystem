package clinic.app;

import java.awt.Dimension;
import clinic.model.Administrator;
import clinic.model.SchoolNurse;
import clinic.model.User;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Application composition root; normal startup never seeds sample credentials. */
public class GUI extends JFrame {
    private final ApplicationContext app;
    public GUI() {
        this(false);
    }

    public GUI(boolean demo) {
        this(demo, null);
    }

    public GUI(boolean demo, Class<? extends User> requiredRole) {
        this(demo?new ApplicationContext(true):ApplicationContext.production(), demo, requiredRole);
    }
    public GUI(ApplicationContext app, boolean demo, Class<? extends User> requiredRole) {
        super(windowTitle(demo, requiredRole));
        this.app=app;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) {
                if (clinic.app.ui.common.popup.MessagePopup.confirm(GUI.this,
                        app.isPersistent()?"Close the clinic application? Saved records will remain available next time."
                            :"Close the demo/test application? All session data will be lost.")) dispose();
            }
        });
        setMinimumSize(new Dimension(1100, 700));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        new Router(this, app, requiredRole).login();
    }
    @Override public void dispose() {
        try { app.close(); super.dispose(); }
        catch (RuntimeException failure) { clinic.app.ui.common.popup.MessagePopup.error(this,failure); }
    }

    public static void main(String[] args) {
        launch(args, null);
    }

    public static void launch(String[] args, Class<? extends User> requiredRole) {
        boolean demo = java.util.Arrays.asList(args).contains("--demo");
        final ApplicationContext context;
        try { context=demo?new ApplicationContext(true):ApplicationContext.production(); }
        catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> clinic.app.ui.common.popup.MessagePopup.error(null,failure));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new GUI(context, demo, requiredRole).setVisible(true);
        });
    }

    private static String windowTitle(boolean demo, Class<? extends User> requiredRole) {
        String title = "UIC School Clinic Management System";
        if (requiredRole == Administrator.class) title += " - Superadmin";
        else if (requiredRole == SchoolNurse.class) title += " - Nurse";
        if (demo) title += " - DEMO";
        return title;
    }
}
