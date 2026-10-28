package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.dispense.DispenseItemsPanel;
import clinic.app.ui.common.MedicinePicker;
import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
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
            JPanel root = new JPanel();
            DispenseItemsPanel[] holder = new DispenseItemsPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new DispenseItemsPanel(app, true);
                root.add(holder[0]);
            });
            DispenseItemsPanel panel = holder[0];
            MedicinePicker picker=find(panel,MedicinePicker.class);
            @SuppressWarnings("unchecked") JComboBox<Object> choices=(JComboBox<Object>)find(panel,JComboBox.class);
            Medicine medicine = new Medicine(product,118,LocalDate.now().plusDays(90),LocalDate.now(),"");
            SwingUtilities.invokeAndWait(()->{
                DefaultComboBoxModel<Object> model=new DefaultComboBoxModel<>();
                model.addElement(medicine);model.setSelectedItem(null);choices.setModel(model);
            });

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

                sizeAndCheck(panel, 468, 320, "1000x700 window");
                sizeAndCheck(panel, 312, 300, "small window");
                choices.setSelectedItem(medicine);
                JTextField selectedName=(JTextField)choices.getEditor().getEditorComponent();
                check(medicine.getDisplayName().equals(selectedName.getText()),
                    "selected picker field displays the full medicine name");
                check(picker.selected()!=null&&picker.selected().getName().equals(medicine.getName()),
                    "choosing a catalog result creates a medicine selection");
                sizeAndCheck(panel, 468, 320, "selected medicine at normal width");
                writeSnapshot(panel,"consultation-medicine-row.png");
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
                DispenseItemsPanel explicitEmpty=new DispenseItemsPanel(app,true);
                root.add(explicitEmpty);
                descendants(explicitEmpty).stream().filter(c->c instanceof JButton button
                    &&"+ Add medicine".equals(button.getText())).map(c->(JButton)c).findFirst().orElseThrow().doClick();
                List<JComboBox<?>> selectors=new java.util.ArrayList<>();
                for(Component component:descendants(explicitEmpty))
                    if(component instanceof JComboBox<?> combo)selectors.add(combo);
                JTextField typed=(JTextField)selectors.get(1).getEditor().getEditorComponent();
                typed.setText("Amox");
                explicitEmpty.setSize(new Dimension(468,320));layoutTree(explicitEmpty);
                check(!explicitEmpty.validateForm()
                    &&explicitEmpty.validationErrors().contains("Choose a medicine or remove this row.")
                    &&"Amox".equals(typed.getText()),
                    "an added empty row shows inline validation and keeps its search text");
                layoutTree(explicitEmpty);
                JScrollPane emptyScroll=find(explicitEmpty,JScrollPane.class);
                JLabel inlineError=descendants(explicitEmpty).stream().filter(c->c instanceof JLabel label
                    &&"Choose a medicine or remove this row.".equals(label.getText()))
                    .map(JLabel.class::cast).findFirst().orElseThrow();
                JPanel medicineList=(JPanel)emptyScroll.getViewport().getView();
                Rectangle errorInList=SwingUtilities.convertRectangle(inlineError.getParent(),inlineError.getBounds(),medicineList);
                check(emptyScroll.getViewport().getViewRect().contains(errorInList),
                    "empty-row message is visible after the form scrolls to it");
                writeSnapshot(explicitEmpty,"consultation-empty-row-error.png");
                root.remove(explicitEmpty);
                DispenseItemsPanel tooMany=new DispenseItemsPanel(app,true);
                root.add(tooMany);
                find(tooMany,JComboBox.class).setSelectedItem(medicine);
                find(tooMany,JSpinner.class).setValue(119);
                check(!tooMany.validateForm()&&tooMany.validationErrors().contains("Only 118 left in stock"),
                    "quantity above usable stock is rejected inline with the available count");
                root.remove(tooMany);
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
        boolean removeTextFits=remove.getFontMetrics(remove.getFont()).stringWidth(remove.getText())
            +remove.getInsets().left+remove.getInsets().right<=remove.getWidth();
        MedicinePicker picker=find(panel,MedicinePicker.class);
        JPanel rowLayout=(JPanel)picker.getParent();
        boolean rowChildrenDoNotOverlap=!picker.getBounds().intersects(rowLayout.getComponent(1).getBounds());
        System.out.println("LAYOUT " + label + " | panel=" + width + "x" + height
            + " | dropdown=" + find(panel, JComboBox.class).getBounds()
            + " quantity=" + find(panel, JSpinner.class).getBounds()
            + " Remove=" + remove.getBounds() + " | all visible="
            + (dropdown && quantity && removeVisible) + " | picker and row controls overlap="
            + !rowChildrenDoNotOverlap + " | Remove text fits="+removeTextFits);
        check(dropdown && quantity && removeVisible, label + " keeps dropdown, quantity, and Remove visible");
        check(rowChildrenDoNotOverlap,label+" keeps picker and row controls in separate space");
        check(removeTextFits,label+" displays the complete Remove label");
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

    private static void writeSnapshot(JPanel panel,String name) {
        try {
            Files.createDirectories(Path.of("verification"));
            BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();
            javax.imageio.ImageIO.write(image,"png",Path.of("verification",name).toFile());
            System.out.println("SCREENSHOT verification/"+name+" | size="+panel.getWidth()+"x"+panel.getHeight());
        } catch(Exception failure) { throw new IllegalStateException("Unable to write screenshot.",failure); }
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
