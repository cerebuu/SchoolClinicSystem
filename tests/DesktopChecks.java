import clinic.app.*;
import clinic.model.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;

/** Native-window integration checks. Uses isolated in-memory test data only. */
public final class DesktopChecks {
    private static int checks;
    private static JFrame frame;
    private static ApplicationContext app;
    private static void edt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < end) {
            AtomicBoolean done = new AtomicBoolean();
            edt(() -> done.set(condition.getAsBoolean()));
            if (done.get()) return;
            Thread.sleep(30);
        }
        throw new AssertionError("Timed out waiting for UI");
    }
    private static List<Component> components(Container parent) {
        List<Component> all = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            all.add(child);
            if (child instanceof Container container) all.addAll(components(container));
        }
        return all;
    }
    private static JButton button(Container parent, String text) {
        return components(parent).stream().filter(c -> c instanceof JButton b
            && text.equals(b.getText()) && b.isShowing()).map(c -> (JButton)c).findFirst().orElseThrow();
    }
    private static void click(Container parent, String text) {
        SwingUtilities.invokeLater(() -> button(parent, text).doClick());
    }
    private static JDialog dialog(String title) {
        return Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog d
            && d.isShowing() && title.equals(d.getTitle())).map(w -> (JDialog)w).findFirst().orElse(null);
    }
    private static JDialog awaitDialog(String title) throws Exception {
        waitFor(() -> dialog(title) != null);
        AtomicReference<JDialog> result = new AtomicReference<>();
        edt(() -> result.set(dialog(title)));
        return result.get();
    }
    private static void fill(Container parent, String name, String value) {
        Component input = components(parent).stream().filter(c -> (c instanceof JTextField || c instanceof JComboBox<?>) && c instanceof JComponent j
            && (name.equals(j.getName()) || name.equals(j.getAccessibleContext().getAccessibleName())))
            .findFirst().orElseThrow(() -> new AssertionError("Missing input: " + name));
        if (input instanceof JTextField field) field.setText(value);
        else if (input instanceof JComboBox<?> combo) combo.setSelectedItem(value);
    }
    private static void form(String title, Map<String, String> values) throws Exception {
        JDialog dialog = awaitDialog(title);
        edt(() -> values.forEach((name, value) -> fill(dialog, name, value)));
        click(dialog, "Save");
        waitFor(() -> !dialog.isShowing());
    }
    private static void dismissInfo() throws Exception {
        JDialog info = awaitDialog("School clinic");
        click(info, "OK");
        waitFor(() -> !info.isShowing());
    }
    private static JTable table() {
        return components(frame).stream().filter(c -> c instanceof JTable t && t.isShowing()
            && t.isEnabled()).map(c -> (JTable)c).findFirst().orElse(null);
    }
    private static void row() throws Exception {
        waitFor(() -> table() != null && table().getRowCount() > 0);
        edt(() -> table().setRowSelectionInterval(0, 0));
    }
    private static void screenshot(String name) throws Exception {
        edt(() -> {
            try {
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                frame.paintAll(g);
                g.dispose();
                javax.imageio.ImageIO.write(image, "png", Path.of("verification", name + ".png").toFile());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Desktop required");
        try {
            app = Arrays.asList(args).contains("--persistent")
                ? new ApplicationContext(Files.createTempDirectory(Path.of("verification"),"desktop-db-").resolve("clinic"))
                : new ApplicationContext(false);
            edt(() -> {
                Theme.install();
                frame = new JFrame("Clinic integration checks");
                frame.setSize(1100, 700);
                new Router(frame, app).login();
                frame.setVisible(true);
            });
            click(frame, "Create first administrator");
            form("First-run administrator setup", Map.of("ID", "root", "Name", "Test Admin", "Password", "test-password"));
            dismissInfo();
            check(!app.login.needsSetup(), "first-run setup via dialog");
            edt(() -> { fill(frame, "User ID", "root"); fill(frame, "Password", "wrong"); });
            click(frame, "Sign in");
            waitFor(() -> button(frame, "Sign in").isEnabled());
            check(!app.login.isLoggedIn(), "UI rejects incorrect password");
            edt(() -> fill(frame, "Password", "test-password"));
            click(frame, "Sign in");
            waitFor(() -> app.login.isLoggedIn() && components(frame).stream().anyMatch(c -> c instanceof JButton b && "Users".equals(b.getText())));
            click(frame, "Users");
            waitFor(() -> table() != null);
            click(frame, "Add user");
            form("Add user", Map.of("ID", "nurse", "Name", "Test Nurse", "Role", User.NURSE, "Password", "test-password"));
            waitFor(() -> table() != null && table().getRowCount() == 2);
            check(app.admin.userCount() == 2, "user added via dialog and table refreshed");
            click(frame, "Campuses");
            click(frame, "Add campus");
            form("Add campus", Map.of("Campus ID", "main", "Name", "Main campus"));
            row();
            click(frame, "Edit campus");
            form("Edit campus", Map.of("Name", "Updated campus"));
            waitFor(() -> table() != null && "Updated campus".equals(table().getValueAt(0, 1)));
            check(true, "campus create/edit refresh");
            click(frame, "Sign out");
            waitFor(() -> !app.login.isLoggedIn());
            edt(() -> { fill(frame, "User ID", "nurse"); fill(frame, "Password", "test-password"); });
            click(frame, "Sign in");
            waitFor(() -> app.login.isLoggedIn() && components(frame).stream().anyMatch(c -> c instanceof JButton b && "Patients".equals(b.getText())));
            click(frame, "Patients");
            click(frame, "Register patient");
            JDialog registration = awaitDialog("Register patient");
            click(registration, "Save");
            waitFor(() -> components(registration).stream().anyMatch(c -> c instanceof JLabel label && "ID is required.".equals(label.getText())));
            check(app.patientCount() == 0, "required fields prevent registration");
            form("Register patient", Map.of("ID", "p1", "Name", "Test Patient", "Contact", "Test contact", "Type", Patient.STUDENT,
                "Course / Department", "BSCS", "Section / Position", "A", "Year level", "1"));
            row();
            check(app.patientCount() == 1, "patient registration and refresh");
            click(frame, "Add allergy");
            JDialog allergy = awaitDialog("Add allergy");
            click(allergy, "Cancel");
            waitFor(() -> !allergy.isShowing());
            check(app.clinic().findPatient("p1").orElseThrow().getAllergies().isEmpty(), "dialog cancellation leaves data unchanged");
            click(frame, "Consultations");
            click(frame, "Record visit");
            form("New Consultation", Map.of("Patient ID", "p1", "Symptoms", "Headache", "Reason", "Test visit"));
            dismissInfo();
            row();
            check(app.clinic().getVisitHistory("p1").size() == 1, "consultation saved via form");
            click(frame, "Inventory");
            click(frame, "Add medicine");
            form("Add medicine batch", Map.of("Medicine", "Test medicine", "Quantity", "10", "Expiry (YYYY-MM-DD)", LocalDate.now().plusDays(90).toString()));
            row();
            click(frame, "Dispense medicine");
            edt(() -> { fill(frame, "Patient ID", "p1"); fill(frame, "Medicine", "Test medicine"); fill(frame, "Quantity", "2"); });
            click(frame, "Check safety and dispense");
            dismissInfo();
            check(app.clinic().getStock("Test medicine") == 8, "dispensing changes stock");
            var transaction = app.clinic().dispenseMedicine("p1", "Test medicine", 1, "nurse", false);
            click(frame, "Notifications");
            click(frame, "Create notification");
            form("Create guardian notification", Map.of("Patient ID", "p1", "Transaction ID", transaction.getTransactionId(), "Reason", "Test notification"));
            row();
            click(frame, "View message");
            dismissInfo();
            click(frame, "Mark sent");
            click(awaitDialog("Confirm"), "OK");
            waitFor(() -> app.clinic().getNotifications("p1").get(0).getStatus() == NotificationStatus.SENT);
            check(true, "notification creation, preview, and confirmed status update");
            app.clinic().addAllergy("p1", "Test medicine");
            click(frame, "Dispense medicine");
            click(frame, "Check safety and dispense");
            JDialog warning = awaitDialog("Safety warning");
            edt(() -> check(!button(warning, "Override with reason").isEnabled(), "override requires a reason"));
            click(warning, "Cancel");
            waitFor(() -> !warning.isShowing());
            check(app.clinic().getStock("Test medicine") == 7, "cancel safety warning preserves stock");
            click(frame, "Check safety and dispense");
            final JDialog override = awaitDialog("Safety warning");
            edt(() -> fill(override, "Override reason", "Test documented override"));
            click(override, "Override with reason");
            dismissInfo();
            check(app.clinic().getStock("Test medicine") == 5, "documented override dispenses medicine");
            click(frame, "Inventory");
            waitFor(() -> table() != null && Integer.valueOf(5).equals(table().getValueAt(0, 1)));
            check(true, "cached inventory reloads after dispensing");
            row();
            click(frame, "Restock");
            form("Restock Test medicine", Map.of("Quantity", "12", "Expiry (YYYY-MM-DD)", LocalDate.now().plusDays(100).toString()));
            waitFor(() -> table() != null && table().getRowCount() == 2);
            check(app.clinic().getStock("Test medicine") == 17, "restock creates batch and updates total");
            screenshot("desktop-inventory-1100");
            click(frame, "Reports");
            row();
            click(frame, "Generate selected report");
            dismissInfo();
            check(true, "report dialog opens and closes");
            click(frame, "Nurse dashboard");
            waitFor(() -> table() != null && table().getRowCount() == 1);
            edt(() -> frame.setSize(1280, 800));
            screenshot("desktop-nurse-1280");
            check(true, "dashboard refresh and window resize");
            edt(() -> frame.dispose());
            AtomicReference<GUI> actual = new AtomicReference<>();
            edt(() -> { actual.set(new GUI(new ApplicationContext(false),false,null)); actual.get().setVisible(true); });
            SwingUtilities.invokeLater(() -> actual.get().dispatchEvent(new WindowEvent(actual.get(), WindowEvent.WINDOW_CLOSING)));
            JDialog close = awaitDialog("Confirm");
            click(close, "Cancel");
            waitFor(() -> !close.isShowing());
            check(actual.get().isDisplayable(), "cancel close retains window");
            SwingUtilities.invokeLater(() -> actual.get().dispatchEvent(new WindowEvent(actual.get(), WindowEvent.WINDOW_CLOSING)));
            click(awaitDialog("Confirm"), "OK");
            waitFor(() -> !actual.get().isDisplayable());
            check(true, "application startup and confirmed shutdown");
            System.out.println("PASS: " + checks + " native Swing workflow checks.");
        } finally {
            edt(() -> { for (Window window : Window.getWindows()) window.dispose(); });
            if(app!=null)app.close();
        }
    }
}
