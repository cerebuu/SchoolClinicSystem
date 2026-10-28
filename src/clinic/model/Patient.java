package clinic.model;
import java.util.*;
import clinic.util.Validation;
public class Patient extends Person {
    public static final String STUDENT="STUDENT", EMPLOYEE="EMPLOYEE";
    private final String patientType;
    private String guardianContact;
    private final List<String> allergies=new ArrayList<>(), medicalHistory=new ArrayList<>();
    public Patient(String id, String name, String contact, String type) {
        super(id, name, contact);
        patientType=Validation.required(type, "Patient type");
    }
    /** Detached profile for read operations; callers cannot mutate stored data. */
    public Patient copy() {
        Patient result;
        if (this instanceof Student student) {
            result = new Student(getId(), getName(), getContactInfo(), student.getCourse(), student.getYearLevel(), student.getSection());
        } else if (this instanceof Employee employee) {
            result = new Employee(getId(), getName(), getContactInfo(), employee.getDepartment(), employee.getPosition());
        } else {
            result = new Patient(getId(), getName(), getContactInfo(), patientType);
        }
        getAllergies().forEach(result::addAllergy);
        result.setGuardianContact(getGuardianContact());
        getMedicalHistory().forEach(result::addHistory);
        return result;
    }
    /**
     * Return the editable profile fields from {@code profile} while retaining this
     * patient's immutable identity and clinical history.
     */
    public Patient withUpdatedProfile(Patient profile) {
        Objects.requireNonNull(profile, "Patient profile is required.");
        if (!getId().equals(profile.getId()))
            throw new IllegalArgumentException("Patient ID cannot be changed.");
        if (!patientType.equals(profile.patientType) || getClass() != profile.getClass())
            throw new IllegalArgumentException("Patient type cannot be changed.");

        Patient updated;
        if (this instanceof Student current && profile instanceof Student replacement) {
            updated = new Student(getId(), replacement.getName(), replacement.getContactInfo(),
                replacement.getCourse(), replacement.getYearLevel(), replacement.getSection());
        } else if (this instanceof Employee && profile instanceof Employee replacement) {
            updated = new Employee(getId(), replacement.getName(), replacement.getContactInfo(),
                replacement.getDepartment(), replacement.getPosition());
        } else if (getClass() == Patient.class) {
            updated = new Patient(getId(), profile.getName(), profile.getContactInfo(), patientType);
        } else {
            throw new IllegalArgumentException("Unsupported patient type.");
        }
        getAllergies().forEach(updated::addAllergy);
        getMedicalHistory().forEach(updated::addHistory);
        updated.setGuardianContact(profile.getGuardianContact());
        return updated;
    }
    public String getPatientType() {
        return patientType;
    }
    public synchronized String getGuardianContact() { return guardianContact; }
    public synchronized void setGuardianContact(String value) { guardianContact = value; }
    public synchronized void addAllergy(String value) {
        String a=Validation.required(value, "Allergy");
        if(!hasAllergy(a))allergies.add(a);
    }
    public synchronized boolean hasAllergy(String value) {
        return allergies.stream().anyMatch(a->a.equalsIgnoreCase(value));
    }
    public synchronized List<String> getAllergies() {
        return List.copyOf(allergies);
    }
    public synchronized void addHistory(String value) {
        medicalHistory.add(Validation.required(value, "History"));
    }
    public synchronized List<String> getMedicalHistory() {
        return List.copyOf(medicalHistory);
    }
}
