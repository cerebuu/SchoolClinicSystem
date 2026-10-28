package clinic.app.ui.dispense;
import clinic.app.ui.common.PagedTableModel;
import clinic.model.MedicationHistoryEntry;
import clinic.util.DateUtil;
public final class TransactionsTableModel extends PagedTableModel<MedicationHistoryEntry> {
    public TransactionsTableModel(){super("Date and time","Event","Medicine","Quantity","Patient ID","Actor ID","Campus","Batch / expiry");}
    protected Object value(MedicationHistoryEntry entry,int column){return switch(column){
        case 0->DateUtil.format(entry.dateTime());case 1->entry.type();
        case 2->entry.medicine().displayName();case 3->entry.quantityLabel();
        case 4->entry.patientId()==null?"—":entry.patientId();case 5->entry.actorId();
        case 6->entry.campusId()==null?"Unassigned":entry.campusId();
        case 7->entry.type()==MedicationHistoryEntry.Type.DISPENSE
            ?entry.batchAllocations().stream().map(a->a.batchId()+" × "+a.quantity()+" ("+a.expiry()+")")
                .collect(java.util.stream.Collectors.joining(", "))
            :(entry.batchId()==null?"Expiry "+entry.expiry():entry.batchId()+" · "+entry.expiry());
        default->"";
    };}
}
