package clinic.app.ui.dispense;
import clinic.app.ui.common.CopyableIdCell;
import clinic.app.ui.common.PagedTableModel;
import clinic.model.MedicationHistoryEntry;
import clinic.util.DateUtil;
public final class TransactionsTableModel extends PagedTableModel<MedicationHistoryEntry> {
    public TransactionsTableModel(){super("Date and time","Event","Event ID","Medicine","Quantity","Patient ID","Actor ID","Campus","Batch ID","Expiry","ATC code");}
    protected Object value(MedicationHistoryEntry entry,int column){return switch(column){
        case 0->DateUtil.format(entry.dateTime());case 1->entry.type();case 2->entry.id();
        case 3->entry.medicine().displayName();case 4->entry.quantityLabel();
        case 5->entry.patientId()==null?"—":entry.patientId();case 6->entry.actorId();
        case 7->entry.campusId()==null?"Unassigned":entry.campusId();
        case 8->batchId(entry);case 9->expiry(entry);case 10->entry.medicine();
        default->"";
    };}
    @Override public Object key(int row){return row(row).id();}
    private static CopyableIdCell.Value batchId(MedicationHistoryEntry entry) {
        if(entry.type()!=MedicationHistoryEntry.Type.DISPENSE)
            return entry.batchId()==null?CopyableIdCell.Value.label("—"):CopyableIdCell.Value.id(entry.batchId());
        if(entry.batchAllocations().size()==1)return CopyableIdCell.Value.id(entry.batchAllocations().get(0).batchId());
        return CopyableIdCell.Value.label(entry.batchAllocations().isEmpty()?"—":"Multiple batches");
    }
    private static String expiry(MedicationHistoryEntry entry) {
        if(entry.type()!=MedicationHistoryEntry.Type.DISPENSE)return String.valueOf(entry.expiry());
        return entry.batchAllocations().stream().map(a->a.expiry().toString()).distinct()
            .collect(java.util.stream.Collectors.joining(", "));
    }
}
