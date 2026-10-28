import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.dispense.MedicinePicker;
import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Prevents an edited medicine search from submitting a previously selected drug. */
public final class MedicineSelectionUiChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (ApplicationContext app = new ApplicationContext(true)) {
            app.login.login("N001", "nurse123").orElseThrow();
            SwingUtilities.invokeAndWait(() -> {
                Theme.install();
                AtomicInteger changes = new AtomicInteger();
                MedicinePicker picker = new MedicinePicker(app, changes::incrementAndGet);
                JComboBox<Object> choices = choices(picker);
                JTextField search = (JTextField) choices.getEditor().getEditorComponent();
                Medicine first = medicine("Paracetamol", "500 mg");
                Medicine second = medicine("Ibuprofen", "200 mg");
                choices.setSelectedItem(first);
                check(picker.selected() == first, "choosing a result selects its medicine");
                check(search.getText().equals(first.getDisplayName()), "selected medicine populates the search field");
                int selectedChanges = changes.get();
                search.setText("Ibuprofen");
                check(picker.selected() == null, "typing a different search invalidates the previous medicine");
                check(changes.get() > selectedChanges, "selection invalidation updates the stock display");
                search.postActionEvent();
                check(picker.selected() == null, "unconfirmed search text cannot restore the old medicine");
                choices.setSelectedItem(second);
                check(picker.selected() == second, "a replacement result can be selected");
                search.setText("");
                check(picker.selected() == null, "clearing search also clears the selected medicine");
                Medicine stale=medicine("Unavailable regression medicine", "999 mg");
                choices.setSelectedItem(stale);
                check(picker.selected()==stale,"test selects a medicine that will be absent from refreshed inventory");
                picker.refresh();
                try {
                    var showPage=MedicinePicker.class.getDeclaredMethod("showPage",
                        clinic.service.PageResult.class,String.class,int.class);
                    showPage.setAccessible(true);
                    showPage.invoke(picker,new clinic.service.PageResult<>(java.util.List.of(),0),
                        stale.getDisplayName(),0);
                } catch(ReflectiveOperationException failure) {
                    throw new AssertionError("Unable to apply refreshed inventory result",failure);
                }
                check(picker.selected()==null,
                    "refresh clears a selected medicine that is no longer available");
                choices.setSelectedItem(stale);
                picker.refresh();
                Medicine depleted=new Medicine(stale.getProduct(),0,stale.getExpirationDate(),
                    stale.getBatchDate(),stale.getRemarks());
                applyRefresh(picker,java.util.List.of(depleted),1,stale.getDisplayName());
                check(picker.selected()==null,
                    "refresh clears a selected medicine that remains listed with zero stock");
                picker.removeNotify();
            });
        }
        System.out.println("PASS: " + checks + " medicine selection UI checks.");
    }

    private static void applyRefresh(MedicinePicker picker,java.util.List<Medicine> rows,int total,String query) {
        try {
            var showPage=MedicinePicker.class.getDeclaredMethod("showPage",
                clinic.service.PageResult.class,String.class,int.class);
            showPage.setAccessible(true);
            showPage.invoke(picker,new clinic.service.PageResult<>(rows,total),query,0);
        } catch(ReflectiveOperationException failure) {
            throw new AssertionError("Unable to apply refreshed inventory result",failure);
        }
    }

    private static Medicine medicine(String name, String strength) {
        return new Medicine(new MedicineProduct(name, strength, "tablet"), 10,
            LocalDate.now().plusDays(30), LocalDate.now(), "");
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<Object> choices(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComboBox<?> combo) return (JComboBox<Object>) combo;
            if (child instanceof Container container) {
                JComboBox<Object> found = choices(container);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
    }

}
