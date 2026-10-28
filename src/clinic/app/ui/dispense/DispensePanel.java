package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.DataTopic;
import clinic.app.ui.common.BasePanel;
import java.awt.BorderLayout;

public final class DispensePanel extends BasePanel {
    private final TransactionHistoryPanel history;

    public DispensePanel(ApplicationContext app) {
        super("Medication history",app);
        history=new TransactionHistoryPanel(app);
        add(history,BorderLayout.CENTER);
        onRefresh(history::refresh);
        refreshOn(app.changes,DataTopic.TRANSACTIONS,DataTopic.INVENTORY);
    }
}
