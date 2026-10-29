package clinic.app.ui.dispense;
import clinic.app.*;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.model.MedicationHistoryEntry;
import java.awt.*;
import java.time.*;
import javax.swing.*;
public final class TransactionHistoryPanel extends JPanel {
    private final PagedTablePanel<MedicationHistoryEntry> table;
    private volatile String period="All";
    public TransactionHistoryPanel(ApplicationContext app) {
        super(new BorderLayout(Theme.GAP,Theme.GAP));setOpaque(false);
        JComboBox<String> dates=new JComboBox<>(new String[]{"All","Today","This week"});
        dates.getAccessibleContext().setAccessibleName("Transaction date filter");Theme.field(dates);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));controls.setOpaque(false);
        controls.add(new JLabel("Medication and stock history"));controls.add(dates);
        add(controls,BorderLayout.NORTH);
        table=new PagedTablePanel<>(new TransactionsTableModel(),(page,size,query)->{
            String selected=period;LocalDate today=LocalDate.now();
            LocalDateTime from=selected.equals("Today")?today.atStartOfDay():selected.equals("This week")?
                today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay():null;
            return app.medicationHistory(page,size,query,from,selected.equals("All")?null:today.plusDays(1).atStartOfDay());
        },"Medicine / patient / actor / batch");
        table.setRowActions(entry->java.util.List.of(new RowAction("View entry",
            ()->MessagePopup.infoWithCopies(this,"Medication history entry",entry.toReport(),
                copyIds(entry),"Copy",false))));
        table.refreshOn(app.changes, clinic.app.DataTopic.TRANSACTIONS,clinic.app.DataTopic.INVENTORY);
        table.setEmptyText("No medication or stock events yet");
        table.setDashboardViewport(DispenseLayout.HISTORY_VISIBLE_ROWS, DispenseLayout.ROW_HEIGHT);
        clinic.app.ui.dashboard.DashboardLayout.sizeCard(table);
        add(table,BorderLayout.CENTER);
        dates.addActionListener(e->{period=(String)dates.getSelectedItem();table.setPage(0);});
        DispenseLayout.sizeSection(this);
    }
    public void refresh(){table.reload();}

    private static java.util.Map<String,String> copyIds(MedicationHistoryEntry entry) {
        java.util.Map<String,String> ids=new java.util.LinkedHashMap<>();
        ids.put(entry.type()==MedicationHistoryEntry.Type.DISPENSE?"Transaction ID":"Event ID",entry.id());
        if(entry.patientId()!=null)ids.put("Patient ID",entry.patientId());
        if(entry.visitId()!=null)ids.put("Visit ID",entry.visitId());
        ids.put("Actor ID",entry.actorId());
        if(entry.campusId()!=null)ids.put("Campus ID",entry.campusId());
        if(entry.batchId()!=null)ids.put("Batch ID",entry.batchId());
        for(int i=0;i<entry.batchAllocations().size();i++)
            ids.put("Batch ID "+(i+1),entry.batchAllocations().get(i).batchId());
        return ids;
    }
}
