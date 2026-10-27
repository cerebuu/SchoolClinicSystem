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
            String key=item.product().key();
            DispenseItem old=merged.get(key);
            merged.put(key,old==null?item:new DispenseItem(old.product(),Math.addExact(old.quantity(),item.quantity())));
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
            for(String warning:clinic.checkSafety(patient,item.product().name())) {
                if(warning.equals(SafetyChecker.ALLERGY_CONFIRMATION)) {
                    if(!warnings.contains(warning))warnings.add(warning);
                } else stockErrors.add(warning);
            }
            // getStock excludes expired batches, even when an allergy override is supplied.
            if(item.quantity()>clinic.getStock(item.product()))stockErrors.add(item.product().displayName()+": requested "+item.quantity()
                +", available "+clinic.getStock(item.product()));
        }
        if(!stockErrors.isEmpty())throw new IllegalStateException("Insufficient unexpired stock. Nothing dispensed:\n"+String.join("\n",stockErrors));
        return List.copyOf(warnings);
    }
    public Patient patient(String id) {
        return clinic.findPatient(id).orElseThrow(()->new IllegalArgumentException("Patient not found."));
    }
    public DispenseResult dispense(String patient,List<DispenseItem> requested,boolean confirmed) {
        List<DispenseItem> items=merge(requested);
        List<MedicineTransaction> done=new ArrayList<>();
        List<String> warnings;
        try {
            warnings=check(patient,items);
            if(!warnings.isEmpty()&&!confirmed)
                throw new IllegalStateException(SafetyChecker.ALLERGY_CONFIRMATION+" Nothing dispensed.");
        } catch(RuntimeException ex) {return new DispenseResult(done,items,ex.getMessage());}
        for(int i=0;i<items.size();i++) {
            DispenseItem item=items.get(i);
            try {
                done.add(clinic.dispenseMedicine(patient,item.product(),item.quantity(),staff.getUserId(),confirmed));
            } catch(RuntimeException ex) {
                String message=item.medicine()+": dispensing failed. Remaining items were not attempted.";
                if(!done.isEmpty()) {
                    try {audit.record(LocalDateTime.now(),staff.getUserId(),staff.getName(),staff.getRole(),
                        "dispense partially completed","completed="+done.size()+", notDispensed="+(items.size()-done.size()),
                        true,staff.getCampusId());}
                    catch(RuntimeException auditFailure){message+=" Audit entry could not be saved; contact the administrator.";}
                }
                return new DispenseResult(done,items.subList(i,items.size()),message);
            }
        }
        return new DispenseResult(done,List.of(),"");
    }
}
