package clinic.model;

// This class is responsible for someone receiving clinic services.
//
// Attributes (private): patientType (STUDENT or EMPLOYEE), allergies (List<String>), medicalHistory (List<String>), guardianContact.
// Constructor: Patient(String id, String name, String contactInfo, String patientType) and call super(id, name, contactInfo).
// Methods: addAllergy(String), getAllergies() returns a copy, hasAllergy(String), addHistory(String), getMedicalHistory() returns a copy.
// Allergies live here, not in MedicalRecord, because they last across visits.
// You will need to import java.util.List and java.util.ArrayList.
public class Patient extends Person {
}
