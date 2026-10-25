package clinic.service;
import clinic.model.*;
import java.util.*;
public class SafetyChecker {
    // Limited project rules, not a clinical interaction database.
    private static final List<Set<String>> CLASSES=List.of(
        Set.of("penicillin","amoxicillin","ampicillin"),
        Set.of("nsaid","nsaids","ibuprofen","mefenamic acid","aspirin"),
        Set.of("sulfa","sulfamethoxazole"));
    public boolean checkAllergy(Patient patient, String medicine) {
        if(patient==null||medicine==null)return false;
        String name=medicine.trim().toLowerCase(Locale.ROOT);
        return patient.hasAllergy(name)||CLASSES.stream().anyMatch(g->g.contains(name)&&g.stream().anyMatch(patient::hasAllergy));
    }
    public String generateWarning(Patient patient, String medicine) {
        if(patient==null)return "Patient not found";
        return checkAllergy(patient,medicine)?medicine+(patient.hasAllergy(medicine)?": direct allergy conflict":": drug-class allergy conflict"):"";
    }
    public List<String> check(Patient patient,String medicine,Inventory inventory) {
        List<String> warnings=new ArrayList<>();
        String allergy=generateWarning(patient,medicine);
        if(!allergy.isEmpty())warnings.add(allergy);
        List<Medicine> batches=inventory.batches().stream().filter(m->m.getName().equalsIgnoreCase(medicine)).toList();
        if(batches.isEmpty())warnings.add("Medicine not found: "+medicine);
        else if(inventory.getStock(medicine)==0) {
            if(batches.stream().anyMatch(m->m.isExpired()&&m.getQuantity()>0))warnings.add(medicine+": expired; no usable stock");
            warnings.add(medicine+": out of stock");
        }
        return List.copyOf(warnings);
    }
}
