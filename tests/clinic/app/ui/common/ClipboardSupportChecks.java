package clinic.app.ui.common;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.popup.FormField;
import clinic.app.ui.common.popup.LimitedTextArea;
import java.awt.datatransfer.Clipboard;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/** Headless clipboard checks use an isolated in-memory clipboard and fixture app. */
public final class ClipboardSupportChecks {
    public static void main(String[] args) throws Exception {
        Clipboard local = new Clipboard("isolated-test-clipboard");
        ClipboardCheckSupport.clipboard = local;
        try (ApplicationContext app = new ApplicationContext(true)) {
            app.login.login("N001", "nurse123").orElseThrow();
            AtomicReference<PatientLookupField> patient = new AtomicReference<>();
            AtomicReference<MedicinePicker> medicine = new AtomicReference<>();
            AtomicReference<SearchBar> search = new AtomicReference<>();
            AtomicReference<LimitedTextArea> limited = new AtomicReference<>();
            AtomicReference<JTextField> popupField = new AtomicReference<>();
            AtomicInteger searches = new AtomicInteger();
            AtomicReference<String> patientQuery = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                Theme.install();
                ClipboardSupport.install(local);
                JPanel root = new JPanel();
                search.set(new SearchBar("Main search", ignored -> searches.incrementAndGet()));
                patient.set(new PatientLookupField(app, PatientLookupField.Mode.NEW_ID,
                    "Patient search", id -> { patientQuery.set(id); return java.util.Optional.empty(); }));
                medicine.set(new MedicinePicker(app, () -> {}, true));
                limited.set(new LimitedTextArea(FormField.remarks()));
                JPanel popup = new JPanel();
                popup.setName("Form popup contents");
                JTextField formInput = new JTextField();
                formInput.getAccessibleContext().setAccessibleName("Popup form field");
                popup.add(formInput);
                popupField.set(formInput);
                root.add(search.get()); root.add(patient.get()); root.add(medicine.get());
                root.add(popup); root.add(limited.get());
            });

            JTextField mainSearch = ClipboardCheckSupport.field(search.get(), "input");
            int searchStart = searches.get();
            ClipboardCheckSupport.exercise(mainSearch, "Main search", "main search");
            ClipboardCheckSupport.waitFor(() -> searches.get() > searchStart);
            Thread.sleep(360);
            ClipboardCheckSupport.check(searches.get() - searchStart == 1,
                "search debounces pasted replacement once");

            JTextField patientInput = patient.get().textField();
            ClipboardCheckSupport.copyClearPaste(patientInput, "patient search");
            ClipboardCheckSupport.waitFor(() -> "patient search".equals(patientQuery.get()));
            ClipboardCheckSupport.check(patient.get().lookupCount() == 1,
                "patient lookup filters once on pasted text");
            ClipboardCheckSupport.cut(patientInput, "patient search", "patient search");
            patient.get().close();

            JTextField medicineInput = ClipboardCheckSupport.field(medicine.get(), "editor");
            ClipboardCheckSupport.copyClearPaste(medicineInput, "medicine query");
            ClipboardCheckSupport.check("medicine query".equals(
                ClipboardCheckSupport.field(medicine.get(), "query")),
                "medicine picker filters from the pasted editor text");
            ClipboardCheckSupport.cut(medicineInput, "medicine query", "medicine query");
            medicine.get().removeNotify();

            ClipboardCheckSupport.exercise(popupField.get(), "Popup form field", "popup form field");
            JTextComponent remarks = ClipboardCheckSupport.field(limited.get(), "text");
            ClipboardCheckSupport.exercise(remarks, "Remarks", "L".repeat(240), 200);
            ClipboardCheckSupport.readOnlyMenu(local);
            ClipboardCheckSupport.editableMenu(local);
            ClipboardCheckSupport.passwordRules(local);
            ClipboardCheckSupport.singleLinePaste(local);
            ClipboardCheckSupport.shortcutBindings(mainSearch);
            ClipboardCheckSupport.tableCopy(local);
            ClipboardCheckSupport.alertDetectedFormat();
            System.out.println("PASS: " + ClipboardCheckSupport.checks
                + " clipboard checks; isolated in-memory clipboard; fixture app only.");
            System.out.println("RIGHT-CLICK menu attached to new popup form field: PASS");
            System.out.println("VISUAL popup display: NOT RUN (headless environment; no DISPLAY)");
        }
    }
}
