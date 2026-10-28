package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.dispense.DispenseItemsPanel;
import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

/** Exercises the consultation's shared medicine rows, validation, and compact sizing. */
public final class ConsultationMedicineUiChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Theme.install();
        try (ApplicationContext app = new ApplicationContext(true)) {
            app.login.login("N001", "nurse123").orElseThrow();
            MedicineProduct product = new MedicineProduct("Paracetamol","N02BE01","");
            app.clinic().addMedicine(product, 118, LocalDate.now().plusDays(90), "");
            JPanel root = new JPanel() { @Override public boolean isShowing() { return true; } };
            DispenseItemsPanel[] holder = new DispenseItemsPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new DispenseItemsPanel(app, true);
                root.add(holder[0]);
                root.addNotify();
            });
            DispenseItemsPanel panel = holder[0];
            JComboBox<?> choices = find(panel, JComboBox.class);
            waitFor(() -> containsMedicine(choices, product));
            Medicine medicine = findMedicine(choices, product);

            SwingUtilities.invokeAndWait(() -> {
                check(panel.validateForm() && panel.items().isEmpty(),
                    "untouched empty row is ignored and does not block Save");
                System.out.println("FORM untouched row | selected=None | quantity=1 | rows="
                    + panel.itemCount() + " | medicines=" + panel.items());

                @SuppressWarnings("unchecked")
                javax.swing.ListCellRenderer<Object> renderer =
                    (javax.swing.ListCellRenderer<Object>) choices.getRenderer();
                Component rendered = renderer.getListCellRendererComponent(
                    new javax.swing.JList<>(), medicine, 0, false, false);
                String choiceText = ((JLabel) rendered).getText();
                check(choiceText.contains(medicine.getName()) && choiceText.contains("118 in stock"),
                    "dropdown shows full medicine name and current stock");

                sizeAndCheck(panel, 468, 220, "1000x700 window");
                sizeAndCheck(panel, 312, 240, "small window");
                System.out.println("INFO medicine selection and quantity entry are exercised by native DesktopChecks.");

                DispenseItemsPanel quantityOnly = new DispenseItemsPanel(app, true);
                root.add(quantityOnly);
                find(quantityOnly, JSpinner.class).setValue(2);
                check(!quantityOnly.validateForm()
                    && quantityOnly.validationErrors().contains("Choose a medicine"),
                    "quantity without medicine shows inline error under its row");
                System.out.println("FORM quantity without medicine | quantity=2 | inline="
                    + quantityOnly.validationErrors() + " | Save permitted=" + quantityOnly.validateForm());
                root.remove(quantityOnly);
                root.removeNotify();
                panel.removeNotify();
            });
        }
        System.out.println("PASS: " + checks + " consultation medicine UI checks.");
    }

    private static void sizeAndCheck(DispenseItemsPanel panel, int width, int height, String label) {
        panel.setSize(new Dimension(width, height));
        layoutTree(panel);
        JScrollPane scroll = find(panel, JScrollPane.class);
        boolean dropdown = visibleIn(scroll, find(panel, JComboBox.class), panel);
        boolean quantity = visibleIn(scroll, find(panel, JSpinner.class), panel);
        JButton remove = descendants(panel).stream().filter(JButton.class::isInstance)
            .map(JButton.class::cast).filter(button -> "Remove".equals(button.getText()))
            .findFirst().orElseThrow();
        boolean removeVisible = visibleIn(scroll, remove, panel);
        System.out.println("LAYOUT " + label + " | panel=" + width + "x" + height
            + " | dropdown=" + find(panel, JComboBox.class).getBounds()
            + " quantity=" + find(panel, JSpinner.class).getBounds()
            + " Remove=" + remove.getBounds() + " | all visible="
            + (dropdown && quantity && removeVisible));
        check(dropdown && quantity && removeVisible, label + " keeps dropdown, quantity, and Remove visible");
    }

    private static boolean visibleIn(JScrollPane scroll, Component component, Container coordinateParent) {
        Rectangle view = SwingUtilities.convertRectangle(scroll.getViewport(),
            scroll.getViewport().getViewRect(), coordinateParent);
        Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), coordinateParent);
        return view.contains(bounds);
    }

    private static void layoutTree(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layoutTree(container);
    }

    private static boolean containsMedicine(JComboBox<?> choices, MedicineProduct product) {
        for (int index = 0; index < choices.getItemCount(); index++) {
            Object value = choices.getItemAt(index);
            if (value instanceof Medicine medicine && medicine.getProduct().key().equals(product.key())) return true;
        }
        return false;
    }

    private static Medicine findMedicine(JComboBox<?> choices, MedicineProduct product) {
        for (int index = 0; index < choices.getItemCount(); index++) {
            Object value = choices.getItemAt(index);
            if (value instanceof Medicine medicine && medicine.getProduct().key().equals(product.key())) return medicine;
        }
        throw new AssertionError("Test medicine is not in the picker.");
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 4000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) Thread.sleep(20);
        if (!condition.getAsBoolean()) throw new AssertionError("Timed out waiting for medicine choices.");
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

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
        System.out.println("PASS: " + description);
    }
}
