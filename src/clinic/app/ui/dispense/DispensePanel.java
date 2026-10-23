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
    private final JLabel validation = new JLabel(" ");
    public DispensePanel(ApplicationContext app) {
        super("Dispense medicine", Theme.SAMPLE_CAMPUS);
        JPanel form=new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        field(form, "Patient ID", patient);
        field(form, "Medicine", medicine);
        field(form, "Quantity", quantity);
        validation.setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.BOLD));
        validation.setForeground(Theme.DANGER);
        form.add(validation);
        JPanel body=new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        Card card = new Card();
        card.setLayout(new BorderLayout());
        card.add(form, BorderLayout.NORTH);
        body.add(card, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        submit=action("Check safety and dispense", true, ()->dispense(app));
    }
    private void field(JPanel p, String name, JTextField f) {
        f.setName(name);
        JLabel l=new JLabel(name);
        l.setLabelFor(f);
        l.setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.BOLD));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        Theme.field(f);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.FIELD_HEIGHT));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(Theme.SPACE_1));
        p.add(f);
        p.add(Box.createVerticalStrut(Theme.SPACE_3));
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
            validation.setText("Enter patient ID, medicine, and a positive whole quantity.");
            return;
        }
        validation.setText(" ");
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
        validation.setForeground(Theme.MUTED);
        validation.setText("Checking medicine safety…");
        AsyncLoader.load(() -> clinic.checkSafety(p, m), warnings -> {
            boolean overrideConfirmed = false;
            if (!warnings.isEmpty()) {
                validation.setText("Review the safety warning before continuing.");
                WarningOverridePopup.Result decision = WarningOverridePopup.show(this, p, m, warnings);
                if (decision.outcome() == WarningOverridePopup.Outcome.CANCELLED) {
                    submit.setEnabled(true);
                    validation.setText("Dispensing cancelled.");
                    return;
                }
                overrideConfirmed = true;
            }
            final boolean confirmed = overrideConfirmed;
            validation.setText("Dispensing…");
            AsyncLoader.load(() -> clinic.dispenseMedicine(p, m, qty, staffId, confirmed), transaction -> {
                submit.setEnabled(true);
                validation.setForeground(Theme.SUCCESS);
                validation.setText("Medicine dispensed successfully.");
                MessagePopup.info(this, "Dispensed. Transaction ID: " + transaction.getTransactionId());
            }, error -> {
                submit.setEnabled(true);
                validation.setForeground(Theme.DANGER);
                validation.setText("Unable to dispense. Review the error and retry.");
                MessagePopup.error(this, error);
            });
        }, error -> {
            submit.setEnabled(true);
            validation.setForeground(Theme.DANGER);
            validation.setText("Unable to check medicine safety.");
            MessagePopup.error(this, error);
        });
    }
}
