package clinic.app;

import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Application composition root; normal startup never seeds sample credentials. */
public class GUI extends JFrame {
    public GUI() {
        this(false);
    }

    public GUI(boolean demo) {
        super("UIC School Clinic Management System" + (demo ? " - DEMO" : ""));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        new Router(this, new ApplicationContext(demo)).login();
    }

    public static void main(String[] args) {
        boolean demo = java.util.Arrays.asList(args).contains("--demo");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new GUI(demo).setVisible(true);
        });
    }
}
