package clinic.service;
import clinic.model.*;
import clinic.util.Validation;
import java.util.*;
import java.time.LocalDate;

final class DispensingService {
    private DispensingService() {}
    static MedicineTransaction dispense(InMemoryClinicSystem clinic,Inventory inventory,
            Map<String,MedicineTransaction> transactions,String patient,String medicine,
            int quantity,String staff,String overrideReason) {
        List<String> warnings=clinic.checkSafety(patient,medicine);
        String reason=overrideReason==null?"":overrideReason.trim();
        boolean override=!reason.isEmpty();
        if(!warnings.isEmpty()&&!override)throw new IllegalStateException("Safety confirmation is required: "+String.join("; ",warnings));
        if(override&&reason.length()<10)throw new IllegalArgumentException("Enter an override reason of at least 10 characters.");
        clinic.findPatient(patient).orElseThrow(()->new IllegalArgumentException("Patient not found."));
        Validation.required(staff,"Staff");
        // Inventory enforces usable quantity and expiry independently of allergy overrides.
        LocalDate expiry=inventory.dispense(medicine,quantity);
        MedicineTransaction t=new MedicineTransaction(UUID.randomUUID().toString(),medicine,patient,staff,quantity,expiry,
            override?"Safety override: "+reason:"");
        transactions.put(t.getTransactionId(),t);
        return t;
    }
}
