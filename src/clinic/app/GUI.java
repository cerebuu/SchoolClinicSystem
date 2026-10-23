package clinic.app;

import java.awt.Dimension;
import clinic.model.Administrator;
import clinic.model.SchoolNurse;
import clinic.model.User;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Application composition root; normal startup never seeds sample credentials. */
public class GUI extends JFrame {
    public GUI() {
        this(false);
    }

    public GUI(boolean demo) {
        this(demo, null);
    }

    public GUI(boolean demo, Class<? extends User> requiredRole) {
        super(windowTitle(demo, requiredRole));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        new Router(this, new ApplicationContext(demo), requiredRole).login();
    }

    public static void main(String[] args) {
        launch(args, null);
    }

    public static void launch(String[] args, Class<? extends User> requiredRole) {
        boolean demo = java.util.Arrays.asList(args).contains("--demo");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new GUI(demo, requiredRole).setVisible(true);
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
