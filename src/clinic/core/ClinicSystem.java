package clinic.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import clinic.notify.Channel;
import clinic.model.MedicineProduct;
import clinic.service.DispenseItem;

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

    /** Update editable profile details. The patient ID and type must match the existing profile. */
    void updatePatient(String patientId, Patient profile);

    /** Allergies live on the Patient profile. */
    void addAllergy(String patientId, String allergy);

    // ---------- 2. Visits and records ----------
    MedicalRecord recordVisit(String patientId, List<String> symptoms,
                              String reason, String remarks, String staffId);

    MedicalRecord recordVisitAndAdminister(String patientId, List<String> symptoms,
                                           String reason, String remarks, String staffId,
                                           List<DispenseItem> medicines, boolean allergyConfirmed);

    List<MedicalRecord> getVisitHistory(String patientId);

    // ---------- 3. Safety and dispensing ----------
    /** Returns stock/patient errors and a generic staff-confirmation requirement for recorded allergies.
     * Does not assess medicine ingredients or allergy compatibility. */
    List<String> checkSafety(String patientId, String medicineName);

    /** Throws IllegalStateException if there are warnings and overrideConfirmed is false. */
    MedicineTransaction dispenseMedicine(String patientId, String medicineName,
                                         int quantity, String staffId,
                                         boolean overrideConfirmed);

    /** Retains a documented override reason with the transaction, outside the general audit log. */
    MedicineTransaction dispenseMedicine(String patientId, String medicineName,
                                         int quantity, String staffId, String overrideReason);

    MedicineTransaction dispenseMedicine(String patientId, MedicineProduct medicine,
                                         int quantity, String staffId, boolean overrideConfirmed);

    // ---------- 4. Inventory ----------
    void addMedicine(String name, int quantity, LocalDate expiry, String remarks);
    void addMedicine(MedicineProduct medicine, int quantity, LocalDate expiry, String remarks);

    void restockMedicine(String name, int quantity, LocalDate expiry);
    void restockMedicine(MedicineProduct medicine, int quantity, LocalDate expiry, String remarks);

    int getStock(String medicineName);
    int getStock(MedicineProduct medicine);

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

    NotificationLog createEmergencyNotification(String patientId);

    NotificationLog beginNotificationAttempt(String notificationId, Channel channel,
                                               LocalDateTime attemptedAt);

    void completeNotificationAttempt(String notificationId, NotificationStatus status);
}
