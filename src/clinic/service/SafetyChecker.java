package clinic.service;
import clinic.model.*;
import java.util.*;
public class SafetyChecker {
    public static final String ALLERGY_CONFIRMATION = "Patient has allergies on record. Staff confirmation required.";
    public List<String> check(Patient patient,String medicine,Inventory inventory) {
        List<String> warnings=new ArrayList<>();
        if(patient==null)warnings.add("Patient not found.");
        else if(!patient.getAllergies().isEmpty())warnings.add(ALLERGY_CONFIRMATION);
        List<Medicine> batches=inventory.batches().stream().filter(m->m.getName().equalsIgnoreCase(medicine)).toList();
        if(batches.isEmpty())warnings.add("Medicine not found: "+medicine);
        else if(inventory.getStock(medicine)==0) {
            if(batches.stream().anyMatch(m->m.isExpired()&&m.getQuantity()>0))warnings.add(medicine+": expired; no usable stock");
            warnings.add(medicine+": out of stock");
        }
        return List.copyOf(warnings);
    }
}
