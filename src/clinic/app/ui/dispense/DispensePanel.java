package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.DataTopic;
import clinic.app.ui.common.BasePanel;
import clinic.app.ui.consultations.NewConsultationForm;
import clinic.model.User;
import java.awt.BorderLayout;

public final class DispensePanel extends BasePanel {
    private final TransactionHistoryPanel history;

    public DispensePanel(ApplicationContext app) {
        super("Medication history",app);
        history=new TransactionHistoryPanel(app);
        add(history,BorderLayout.CENTER);
        if(app.current().canAccess(User.RECORD_TRANSACTION))
            action("+ New consultation",true,()->NewConsultationForm.open(this,app,history::refresh));
        onRefresh(history::refresh);
        refreshOn(app.changes,DataTopic.TRANSACTIONS,DataTopic.INVENTORY);
    }
}
