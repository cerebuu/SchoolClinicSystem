package clinic.model;

// This class is responsible for authorized doctors who receive medical reports.
public class Doctor extends User {

    private String specialty;

    public Doctor(String userId, String name, String specialty) {
        super(userId, name, DOCTOR);
        this.specialty = specialty;
    }

    public String getSpecialty() {
        return specialty;
    }

    // Doctors can only view reports sent to them.
    @Override
    public boolean canAccess(String feature) {
        return isOneOf(feature, VIEW_REPORTS);
    }
}
