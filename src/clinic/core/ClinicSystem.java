package clinic.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import clinic.model.*;

/**
 * ClinicSystem: the single entry point the screens and Main use.
 * Do not change a method signature without telling the group chat first.
 */
public interface ClinicSystem {

    // ---------- 1. Patients ----------
    /** Look up by student or employee ID. Empty if not found. */
    Optional<Patient> findPatient(String patientId);

    /** Register a new Student or Employee. Throws if the ID already exists. */
    void registerPatient(Patient patient);

    /** Allergies live on the Patient profile. */
    void addAllergy(String patientId, String allergy);

    // ---------- 2. Visits and records ----------
    MedicalRecord recordVisit(String patientId, List<String> symptoms,
                              String reason, String remarks, String staffId);

    List<MedicalRecord> getVisitHistory(String patientId);

    // ---------- 3. Safety and dispensing ----------
    /** Run after the medicine is selected and before dispensing. Empty list means no conflict. */
    List<String> checkSafety(String patientId, String medicineName);

    /** Throws IllegalStateException if there are warnings and overrideConfirmed is false. */
    MedicineTransaction dispenseMedicine(String patientId, String medicineName,
                                         int quantity, String staffId,
                                         boolean overrideConfirmed);

    // ---------- 4. Inventory ----------
    void addMedicine(String name, int quantity, LocalDate expiry, String remarks);

    void restockMedicine(String name, int quantity, LocalDate expiry);

    int getStock(String medicineName);

    List<Medicine> getLowStock();

    List<Medicine> getExpiringWithin(int days);

    // ---------- 5. Analytics and alerts ----------
    double estimateDaysRemaining(String medicineName);

    List<Alert> detectOutbreaks();

    Optional<Alert> detectRecurringVisit(String patientId);

    List<Alert> getAllAlerts();

    // ---------- 6. Reports and search ----------
    String generateReport(Formattable item);

    List<MedicalRecord> searchRecords(String patientId, String symptom,
                                      String medicineName, LocalDate date,
                                      String section, String department);

    // ---------- 7. Guardian notifications ----------
    NotificationLog createGuardianNotification(String patientId,
                                               String transactionId,
                                               String reason);

    void updateNotificationStatus(String notificationId, NotificationStatus status);

    List<NotificationLog> getNotifications(String patientId);
}
