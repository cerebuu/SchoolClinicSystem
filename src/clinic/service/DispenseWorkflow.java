package clinic.service;
import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.LocalDateTime;
import java.util.*;

/** Sequential commits, with full preflight and an explicit partial-result ledger. */
public final class DispenseWorkflow {
    private final ClinicSystem clinic;
    private final User staff;
    private final AuditLog audit;
    public DispenseWorkflow(ClinicSystem securedClinic,User staff,AuditLog audit) {
        this.clinic=securedClinic;this.staff=staff;this.audit=audit;
    }
    public static List<DispenseItem> merge(List<DispenseItem> items) {
        Map<String,DispenseItem> merged=new LinkedHashMap<>();
        for(DispenseItem item:items) {
            String key=item.medicine().toLowerCase(Locale.ROOT);
            DispenseItem old=merged.get(key);
            merged.put(key,old==null?item:new DispenseItem(old.medicine(),Math.addExact(old.quantity(),item.quantity())));
        }
        if(merged.isEmpty())throw new IllegalArgumentException("Add at least one medicine.");
        return List.copyOf(merged.values());
    }
    public List<String> check(String patient,List<DispenseItem> requested) {
        List<DispenseItem> items=merge(requested);
        clinic.findPatient(patient).orElseThrow(()->new IllegalArgumentException("Patient not found."));
        List<String> warnings=new ArrayList<>();
        List<String> stockErrors=new ArrayList<>();
        for(DispenseItem item:items) {
            warnings.addAll(clinic.checkSafety(patient,item.medicine()).stream().map(w->item.medicine()+": "+w).toList());
            // getStock excludes expired batches, even when an allergy override is supplied.
            if(item.quantity()>clinic.getStock(item.medicine()))stockErrors.add(item.medicine()+": requested "+item.quantity()
                +", available "+clinic.getStock(item.medicine()));
        }
        if(!stockErrors.isEmpty())throw new IllegalStateException("Insufficient unexpired stock. Nothing dispensed:\n"+String.join("\n",stockErrors));
        return List.copyOf(warnings);
    }
    public DispenseResult dispense(String patient,List<DispenseItem> requested,String reason) {
        List<DispenseItem> items=merge(requested);
        List<MedicineTransaction> done=new ArrayList<>();
        List<String> warnings;
        try {
            warnings=check(patient,items);
            if(!warnings.isEmpty()&&(reason==null||reason.trim().length()<10))
                throw new IllegalStateException("Safety override requires a reason of at least 10 characters. Nothing dispensed.");
        } catch(RuntimeException ex) {return new DispenseResult(done,items,ex.getMessage());}
        for(int i=0;i<items.size();i++) {
            DispenseItem item=items.get(i);
            try {
                boolean override=warnings.stream().anyMatch(w->w.startsWith(item.medicine()+":"));
                done.add(clinic.dispenseMedicine(patient,item.medicine(),item.quantity(),staff.getUserId(),override?(reason==null?"":reason):""));
            } catch(RuntimeException ex) {
                String message=item.medicine()+": dispensing failed. Remaining items were not attempted.";
                if(!done.isEmpty()) {
                    try {audit.record(LocalDateTime.now(),staff.getUserId(),staff.getName(),staff.getRole(),
                        "dispense partially completed","completed="+done.size()+", notDispensed="+(items.size()-done.size()),true);}
                    catch(RuntimeException auditFailure){message+=" Audit entry could not be saved; contact the administrator.";}
                }
                return new DispenseResult(done,items.subList(i,items.size()),message);
            }
        }
        return new DispenseResult(done,List.of(),"");
    }
}
