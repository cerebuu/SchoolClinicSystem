package clinic.service;
import clinic.model.Patient;
public class SafetyChecker {
    // This is the project's limited allergy rule, not a clinical interaction database.
    public boolean checkAllergy(Patient patient, String medicine) {
        return patient.hasAllergy(medicine)||("Amoxicillin".equalsIgnoreCase(medicine)&&patient.hasAllergy("Penicillin"));
    }
    public String generateWarning(Patient patient, String medicine) {
        return checkAllergy(patient, medicine)?"Recorded allergy conflicts with the selected medicine.":"";
    }
}
