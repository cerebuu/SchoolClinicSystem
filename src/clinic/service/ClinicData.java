package clinic.service;

import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.LocalDateTime;
import java.util.List;

/** Internal query contract shared by the memory fixture and durable repository adapter. */
public interface ClinicData extends ClinicSystem {
    MedicalRecord recordVisitWithCampus(String patientId,List<String> symptoms,String reason,String remarks,
        String staffId,String campusId);
    MedicalRecord recordVisitAndAdministerWithCampus(String patientId,List<String> symptoms,String reason,
        String remarks,String staffId,List<DispenseItem> medicines,boolean allergyConfirmed,String campusId);
    MedicalRecord recordVisitAndAdministerWithReason(String patientId,List<String> symptoms,String reason,
        String remarks,String staffId,List<DispenseItem> medicines,String overrideReason,String campusId);
    MedicineTransaction dispenseMedicineWithCampus(String patientId,MedicineProduct medicine,int quantity,
        String staffId,String overrideReason,String campusId);
    void restockMedicine(String name, int quantity, java.time.LocalDate expiry, String remarks);
    void restockMedicine(MedicineProduct product,int quantity,java.time.LocalDate expiry,String remarks);
    List<Medicine> matchingMedicineBatches(String name,java.time.LocalDate expiry);
    List<Medicine> matchingMedicineBatches(MedicineProduct product,java.time.LocalDate expiry);
    void mergeMedicineBatch(String name,int quantity,java.time.LocalDate expiry,String remarks);
    void mergeMedicineBatch(MedicineProduct product,int quantity,java.time.LocalDate expiry,String remarks);
    void mergeDuplicateMedicineBatches(String name,java.time.LocalDate expiry);
    void mergeDuplicateMedicineBatches(MedicineProduct product,java.time.LocalDate expiry);
    void refillBatch(String batchId,int quantity,java.time.LocalDate expiry,String remarks);
    void saveInventoryBatch(MedicineProduct product,int quantity,java.time.LocalDate expiry,String remarks,
        boolean restock,boolean merge,String actorId,String campusId);
    void refillInventoryBatch(String batchId,int quantity,java.time.LocalDate expiry,String remarks,
        String actorId,String campusId);
    NotificationLog createGuardianNotificationWithCampus(String patientId,String transactionId,
        String reason,String campusId);
    NotificationLog createEmergencyNotificationWithCampus(String patientId,String campusId);
    void updateNotificationStatusForCampus(String notificationId,NotificationStatus status,String campusId);
    List<NotificationLog> getNotificationsForCampus(String patientId,String campusId);
    NotificationLog beginNotificationAttemptForCampus(String notificationId,clinic.notify.Channel channel,
        LocalDateTime attemptedAt,String campusId);
    void completeNotificationAttemptForCampus(String notificationId,NotificationStatus status,String campusId);
    PageResult<NotificationLog> notificationPageForCampus(int page,int size,String filter,String campusId);
    PageResult<MedicationHistoryEntry> medicationHistoryPage(int page,int size,String query,
        LocalDateTime from,LocalDateTime to,String campusId);
    List<Alert> detectOutbreaks(String campusId);
    java.util.Optional<Alert> detectRecurringVisit(String patientId,String campusId);
    List<Alert> getAllAlerts(String campusId);
    List<MedicalRecord> searchRecords(String patientId,String symptom,String medicineName,
        java.time.LocalDate date,String section,String department,String campusId);
    PageResult<Medicine> dispensableMedicinePage(int page,int size,String query);
    PageResult<MedicineTransaction> transactionPage(int page,int size,String query,
        LocalDateTime fromInclusive,LocalDateTime toExclusive);
    PageResult<MedicineTransaction> transactionPage(int page,int size,String query,
        LocalDateTime fromInclusive,LocalDateTime toExclusive,String campusId);
    PageResult<Patient> patientPage(int page, int size, String filter);
    PageResult<Medicine> medicinePage(int page, int size, String filter);
    PageResult<MedicalRecord> recordPage(int page, int size, String filter);
    PageResult<MedicalRecord> recordPage(int page, int size, String filter,String campusId);
    PageResult<MedicalRecord> recentRecordPage(int page, int size, String query, String staffId);
    PageResult<MedicalRecord> recentRecordPage(int page,int size,String query,String staffId,String campusId);
    PageResult<MedicalRecord> recentRecordPage(int page, int size, String query, String staffId,
        LocalDateTime from, LocalDateTime to);
    PageResult<MedicalRecord> recentRecordPage(int page,int size,String query,String staffId,
        LocalDateTime from,LocalDateTime to,String campusId);
    PageResult<NotificationLog> notificationPage(int page, int size, String filter);
    List<MedicalRecord> getVisitHistory(String patientId,String campusId);
    PageResult<Alert> alertPage(int page, int size, String query);
    PageResult<Alert> alertPage(int page,int size,String query,String campusId);
    int patientCount();
    int recordCount();
    int recordCount(String campusId);
}
