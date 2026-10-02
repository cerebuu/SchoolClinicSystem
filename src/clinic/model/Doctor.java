package clinic.model;

// This class is responsible for authorized doctors who receive medical reports.
//
// Role is DOCTOR. Attribute: specialty (String).
// Constructor: Doctor(String userId, String name, String specialty) and call super(...).
// Override canAccess() to allow only viewing reports sent to this doctor.
public class Doctor extends User {
}
