package clinic.service;
import clinic.model.MedicineTransaction;
import java.util.List;
public record DispenseResult(List<MedicineTransaction> completed,List<DispenseItem> remaining,String failure) {
    public DispenseResult {completed=List.copyOf(completed);remaining=List.copyOf(remaining);}
    public boolean successful(){return remaining.isEmpty();}
    public String report() {
        String done=completed.isEmpty()?"None":String.join("\n",completed.stream()
            .map(t->t.getMedicineName()+" x "+t.getQuantity()+" — Transaction ID: "+t.getTransactionId()).toList());
        String pending=remaining.isEmpty()?"None":String.join("\n",remaining.stream().map(DispenseItem::toString).toList());
        return "Dispensed:\n"+done+"\n\nNot dispensed:\n"+pending+(failure.isEmpty()?"":"\n\n"+failure);
    }
}
