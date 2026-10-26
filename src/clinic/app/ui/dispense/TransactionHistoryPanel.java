package clinic.app.ui.dispense;
import clinic.app.*;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.model.MedicineTransaction;
import java.awt.*;
import java.time.*;
import javax.swing.*;
public final class TransactionHistoryPanel extends JPanel {
    private final PagedTablePanel<MedicineTransaction> table;
    private volatile String period="All";
    public TransactionHistoryPanel(ApplicationContext app) {
        super(new BorderLayout(Theme.GAP,Theme.GAP));setOpaque(false);
        JComboBox<String> dates=new JComboBox<>(new String[]{"All","Today","This week"});
        dates.getAccessibleContext().setAccessibleName("Transaction date filter");Theme.field(dates);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));controls.setOpaque(false);
        controls.add(new JLabel("Transaction history"));controls.add(dates);
        JButton details=Theme.button("View transaction",false);controls.add(details);add(controls,BorderLayout.NORTH);
        table=new PagedTablePanel<>(new TransactionsTableModel(),(page,size,query)->{
            String selected=period;LocalDate today=LocalDate.now();
            LocalDateTime from=selected.equals("Today")?today.atStartOfDay():selected.equals("This week")?
                today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay():null;
            return app.transactions(page,size,query,from,selected.equals("All")?null:today.plusDays(1).atStartOfDay());
        },"Patient ID / medicine");
        table.refreshOn(app.changes, clinic.app.DataTopic.TRANSACTIONS);
        table.setEmptyText("No transactions yet");add(table,BorderLayout.CENTER);
        dates.addActionListener(e->{period=(String)dates.getSelectedItem();table.setPage(0);});
        details.addActionListener(e->{var t=table.selected();if(t==null)MessagePopup.warning(this,"Select a transaction.");else MessagePopup.info(this,t.toReport());});
    }
    public void refresh(){table.reload();}
}
