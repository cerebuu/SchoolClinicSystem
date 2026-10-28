import clinic.app.Theme;
import clinic.app.ui.common.popup.FormField;
import clinic.app.ui.common.popup.FormPopup;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Native-window checks for exactly one interactive form submission at a time. */
public final class PopupSubmissionChecks {
    private static final String TITLE = "Submission regression fixture";
    private static int checks;

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<String> mode = new AtomicReference<>("sync failure");
        AtomicInteger preparations = new AtomicInteger(), writes = new AtomicInteger(), saved = new AtomicInteger();
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            FormPopup.showInteractive(new JPanel(), TITLE, List.of(FormField.value("Value", "original")),
                Map.of(), (values, selections, controller) -> {
                    if (mode.get().equals("sync failure")) throw new IllegalArgumentException("Synchronous fixture failure.");
                    controller.prepareAsync(() -> {
                        preparations.incrementAndGet();
                        if (mode.get().equals("prepare failure")) throw new IllegalArgumentException("Preparation fixture failure.");
                        if (mode.get().equals("success")) {
                            started.countDown();
                            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out.");
                        }
                        return values.get("Value");
                    }, prepared -> {
                        if (mode.get().equals("cancel")) return;
                        controller.saveAsync(() -> {
                            if (!"original".equals(prepared)) throw new AssertionError("Submission values changed.");
                            writes.incrementAndGet();
                            return true;
                        });
                    });
                }, saved::incrementAndGet);
        });
        try {
            waitFor(() -> dialog() != null);
            edt(() -> button("Save").doClick());
            waitFor(() -> messages(dialog()).contains("Synchronous fixture failure."));
            edt(() -> check(button("Save").isEnabled() && field().isEnabled(),
                "a synchronous submit error leaves the form usable"));

            mode.set("prepare failure");
            edt(() -> button("Save").doClick());
            waitFor(() -> messages(dialog()).contains("Preparation fixture failure."));
            edt(() -> check(button("Save").isEnabled() && button("Cancel").isEnabled()
                && field().isEnabled() && field().getText().equals("original"),
                "failed preparation restores inputs and preserves values for retry"));

            mode.set("cancel");
            edt(() -> button("Save").doClick());
            waitFor(() -> button("Save").isEnabled());
            edt(() -> check(field().isEnabled() && writes.get() == 0,
                "cancelling preparation restores the form without writing"));

            mode.set("success");
            edt(() -> button("Save").doClick());
            check(started.await(5, TimeUnit.SECONDS), "the controlled preparation starts");
            int preparingCount = preparations.get();
            edt(() -> {
                check(!button("Save").isEnabled() && !button("Cancel").isEnabled() && !field().isEnabled(),
                    "preparation freezes the form and its submit controls");
                button("Save").doClick();
                button("Save").doClick();
            });
            check(preparations.get() == preparingCount, "repeated Save does not start another preparation");
            release.countDown();
            waitFor(() -> dialog() == null);
            check(writes.get() == 1 && saved.get() == 1, "one successful attempt writes and completes exactly once");
        } finally {
            release.countDown();
            edt(() -> { for (Window window : Window.getWindows()) if (window instanceof JDialog
                && TITLE.equals(((JDialog) window).getTitle())) window.dispose(); });
        }
        System.out.println("PASS: " + checks + " native popup submission checks.");
    }

    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            edt(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(15);
        }
        throw new AssertionError("Timed out waiting for popup state.");
    }

    private static JDialog dialog() {
        for (Window window : Window.getWindows()) if (window instanceof JDialog popup
                && popup.isShowing() && TITLE.equals(popup.getTitle())) return popup;
        return null;
    }

    private static JButton button(String title) {
        return descendants(dialog()).stream().filter(child -> child instanceof JButton button
            && title.equals(button.getText())).map(child -> (JButton) child).findFirst().orElseThrow();
    }

    private static JTextField field() {
        return descendants(dialog()).stream().filter(child -> child instanceof JTextField)
            .map(child -> (JTextField) child).findFirst().orElseThrow();
    }

    private static String messages(Container parent) {
        return descendants(parent).stream().filter(child -> child instanceof JLabel)
            .map(child -> ((JLabel) child).getText()).collect(java.util.stream.Collectors.joining(" "));
    }

    private static List<Component> descendants(Container parent) {
        java.util.ArrayList<Component> result = new java.util.ArrayList<>();
        if (parent != null) for (Component child : parent.getComponents()) {
            result.add(child);
            if (child instanceof Container container) result.addAll(descendants(container));
        }
        return result;
    }

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
    }
}
