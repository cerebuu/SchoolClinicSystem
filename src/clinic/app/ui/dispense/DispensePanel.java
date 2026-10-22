package clinic.app.ui.dispense;
import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import java.awt.*;
import javax.swing.*;
public final class DispensePanel extends BasePanel {
    private final JTextField patient=new JTextField(24), medicine=new JTextField(24), quantity=new JTextField("1", 24);
    private final JButton submit;
    public DispensePanel(ApplicationContext app) {
        super("Dispense medicine");
        JPanel form=new JPanel(new GridLayout(0, 2, Theme.GAP, Theme.GAP));
        field(form, "Patient ID", patient);
        field(form, "Medicine", medicine);
        field(form, "Quantity", quantity);
        JPanel body=new JPanel(new BorderLayout());
        body.add(form, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        submit=action("Check safety and dispense", ()->dispense(app));
    }
    private void field(JPanel p, String name, JTextField f) {
        f.setName(name);
        JLabel l=new JLabel(name);
        l.setLabelFor(f);
        p.add(l);
        Theme.field(f);
        p.add(f);
    }
    private void dispense(ApplicationContext app) {
        String p=patient.getText().trim(), m=medicine.getText().trim();
        int qty;
        try {
            clinic.util.Validation.required(p, "Patient ID");
            clinic.util.Validation.required(m, "Medicine");
            qty=clinic.util.Validation.positive(Integer.parseInt(quantity.getText().trim()));
        }
        catch(RuntimeException ex) {
            MessagePopup.error(this, new IllegalArgumentException("Enter patient ID, medicine, and a positive whole quantity."));
            return;
        }
        final clinic.core.ClinicSystem clinic;
        final String staffId;
        try {
            clinic = app.clinic();
            staffId = app.current().getUserId();
        } catch (RuntimeException error) {
            MessagePopup.error(this, error);
            return;
        }
        submit.setEnabled(false);
        AsyncLoader.load(() -> clinic.checkSafety(p, m), warnings -> {
            boolean overrideConfirmed = false;
            if (!warnings.isEmpty()) {
                WarningOverridePopup.Result decision = WarningOverridePopup.show(this, p, m, warnings);
                if (decision.outcome() == WarningOverridePopup.Outcome.CANCELLED) {
                    submit.setEnabled(true);
                    return;
                }
                overrideConfirmed = true;
            }
            final boolean confirmed = overrideConfirmed;
            AsyncLoader.load(() -> clinic.dispenseMedicine(p, m, qty, staffId, confirmed), transaction -> {
                submit.setEnabled(true);
                MessagePopup.info(this, "Dispensed. Transaction ID: " + transaction.getTransactionId());
            }, error -> {
                submit.setEnabled(true);
                MessagePopup.error(this, error);
            });
        }, error -> {
            submit.setEnabled(true);
            MessagePopup.error(this, error);
        });
    }
}
