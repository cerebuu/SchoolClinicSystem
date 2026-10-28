package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

/** Exercises optional medicines, incomplete selections, and exact quantity validation. */
public final class ConsultationMedicineUiChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (ApplicationContext app = new ApplicationContext(true)) {
            app.login.login("N001", "nurse123").orElseThrow();
            SwingUtilities.invokeAndWait(() -> {
                ConsultationMedicinePanel panel = new ConsultationMedicinePanel(app);
                check(panel.items().isEmpty(), "consultations support no medicines");
                descendants(panel).stream().filter(child -> child instanceof JButton button
                    && "+ Add medicine".equals(button.getText())).map(child -> (JButton) child)
                    .findFirst().orElseThrow().doClick();
                JComboBox<Object> choices = choices(panel);
                choices.setSelectedItem(new Medicine(new MedicineProduct("Vitamin C", "500 mg", "tablet"),
                    10, LocalDate.now().plusDays(30), LocalDate.now(), ""));
                JSpinner quantity = find(panel, JSpinner.class);
                for (String invalid : new String[]{"1.5", "2abc", "0", "-2", "2147483648", ""}) {
                    ((JSpinner.DefaultEditor) quantity.getEditor()).getTextField().setText(invalid);
                    rejects(panel::items, "invalid quantity is rejected without truncation: " + invalid);
                }
                ((JSpinner.DefaultEditor) quantity.getEditor()).getTextField().setText("3");
                check(panel.items().get(0).quantity() == 3, "valid whole quantity is preserved");
                ((javax.swing.JTextField) choices.getEditor().getEditorComponent()).setText("Another medicine");
                rejects(panel::items, "an unconfirmed medicine search cannot silently save without medicine");
                panel.removeNotify();
            });
        }
        System.out.println("PASS: " + checks + " consultation medicine UI checks.");
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<Object> choices(Container panel) {
        return (JComboBox<Object>) find(panel, JComboBox.class);
    }

    private static java.util.List<Component> descendants(Container parent) {
        java.util.List<Component> result = new java.util.ArrayList<>();
        for (Component child : parent.getComponents()) {
            result.add(child);
            if (child instanceof Container container) result.addAll(descendants(container));
        }
        return result;
    }

    private static <T> T find(Container parent, Class<T> type) {
        return descendants(parent).stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static void rejects(Runnable work, String description) {
        try { work.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(description);
    }

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
    }
}
