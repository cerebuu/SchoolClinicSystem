package clinic.service;
import clinic.model.*;
import clinic.util.Validation;
import java.util.*;
import java.time.LocalDate;

final class DispensingService {
    private DispensingService() {}
    static MedicineTransaction dispense(InMemoryClinicSystem clinic,Inventory inventory,
            Map<String,MedicineTransaction> transactions,String patient,MedicineProduct medicine,
            int quantity,String staff,String overrideReason,String visitId,String campusId) {
        List<String> warnings=clinic.checkSafety(patient,medicine.name());
        List<String> hardErrors=warnings.stream().filter(w -> !w.equals(SafetyChecker.ALLERGY_CONFIRMATION)).toList();
        if(!hardErrors.isEmpty())throw new IllegalStateException(String.join("; ",hardErrors));
        String reason=overrideReason==null?"":overrideReason.trim();
        boolean override=!reason.isEmpty();
        if(!warnings.isEmpty()&&!override)throw new IllegalStateException(SafetyChecker.ALLERGY_CONFIRMATION);
        if(override&&reason.length()<10)throw new IllegalArgumentException("Enter an override reason of at least 10 characters.");
        clinic.findPatient(patient).orElseThrow(()->new IllegalArgumentException("Patient not found."));
        Validation.required(staff,"Staff");
        String campus=campusId==null||campusId.isBlank()?null:Validation.required(campusId,"Campus ID");
        // Inventory enforces usable quantity and expiry independently of allergy overrides.
        List<BatchAllocation> allocations=inventory.dispenseAllocations(medicine,quantity);
        LocalDate expiry=allocations.get(0).expiry();
        MedicineTransaction t=new MedicineTransaction(UUID.randomUUID().toString(),medicine,patient,staff,quantity,expiry,
            override?"Staff confirmation: "+reason:"",java.time.LocalDateTime.now(),visitId,allocations,campus);
        transactions.put(t.getTransactionId(),t);
        return t;
    }
}
