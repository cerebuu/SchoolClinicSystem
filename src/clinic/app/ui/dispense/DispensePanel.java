package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.Card;
import clinic.app.ui.common.AsyncLoader;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.app.ui.common.popup.AllergyConfirmPopup;
import clinic.service.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;

public final class DispensePanel extends clinic.app.ui.common.BasePanel {
    private final PatientLookupField patient;
    private final DispenseItemsPanel items;
    private final TransactionHistoryPanel history;
    private final JButton submit;
    private final JLabel state=new JLabel(" ");
    private final ApplicationContext app;
    public DispensePanel(ApplicationContext app) {
        super("Dispense medicine","Clinic records");this.app=app;
        patient=new PatientLookupField(app);items=new DispenseItemsPanel(app);history=new TransactionHistoryPanel(app);
        Card form=new Card();form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));
        form.add(label("Patient ID"));form.add(patient);form.add(Box.createVerticalStrut(Theme.SPACE_3));
        form.add(label("Dispense items"));form.add(items);form.add(Box.createVerticalStrut(Theme.SPACE_2));
        state.setForeground(Theme.DANGER);form.add(state);
        JPanel content=new JPanel(new BorderLayout(Theme.GAP,Theme.GAP));content.setOpaque(false);
        content.add(form,BorderLayout.NORTH);content.add(history,BorderLayout.CENTER);add(content,BorderLayout.CENTER);
        submit=action("Dispense",true,()->dispense());
        onRefresh(()->{items.refresh();history.refresh();});
    }
    private JLabel label(String text){JLabel l=new JLabel(text);l.setFont(Theme.font(Theme.TYPE_BODY,Font.BOLD));return l;}
    private void dispense() {
        final String patientId;final List<DispenseItem> requested;final DispenseWorkflow workflow;
        try {patientId=patient.patientId();requested=items.items();workflow=app.dispenseSession();}
        catch(RuntimeException error){MessagePopup.error(this,error);return;}
        submit.setEnabled(false);state.setForeground(Theme.MUTED);state.setText("Checking patient and stock...");
        AsyncLoader.load(()->{workflow.check(patientId,requested);return workflow.patient(patientId);},profile->{
            boolean confirmed=false;
            if(!profile.getAllergies().isEmpty()) {
                confirmed=AllergyConfirmPopup.show(this,profile,requested);
                if(!confirmed){finish("Dispensing cancelled.",Theme.WARNING);return;}
            }
            boolean allergyConfirmed=confirmed;state.setText("Dispensing...");
            AsyncLoader.load(()->workflow.dispense(patientId,requested,allergyConfirmed),result->{
                submit.setEnabled(true);
                if(result.successful()) {
                    state.setForeground(Theme.SUCCESS);state.setText("Medicine dispensed successfully.");
                    items.clear();patient.clear();MessagePopup.info(this,result.report());
                } else {
                    state.setForeground(Theme.DANGER);state.setText("Some items were not dispensed.");
                    MessagePopup.error(this,new IllegalStateException(result.report()));
                }
            },error->{finish(MessagePopup.errorText(error),Theme.DANGER);MessagePopup.error(this,error);});
        },error->{finish(MessagePopup.errorText(error),Theme.DANGER);MessagePopup.error(this,error);});
    }
    private void finish(String text,Color color){submit.setEnabled(true);state.setForeground(color);state.setText(text);}
}
