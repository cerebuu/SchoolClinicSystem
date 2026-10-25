package clinic.app.ui.dispense;
import clinic.app.ui.common.PagedTableModel;
import clinic.model.MedicineTransaction;
import clinic.util.DateUtil;
public final class TransactionsTableModel extends PagedTableModel<MedicineTransaction> {
    public TransactionsTableModel(){super("Date and time","Transaction ID","Patient ID","Medicine","Quantity","Staff ID");}
    protected Object value(MedicineTransaction t,int column){return switch(column){
        case 0->DateUtil.format(t.getDateTime());case 1->t.getTransactionId();case 2->t.getPatientId();
        case 3->t.getMedicineName();case 4->t.getQuantity();case 5->t.getStaffId();default->"";
    };}
}
