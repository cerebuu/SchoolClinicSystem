package clinic.service;

import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.LocalDateTime;
import java.util.List;

/** Internal query contract shared by the memory fixture and durable repository adapter. */
public interface ClinicData extends ClinicSystem {
    void restockMedicine(String name, int quantity, java.time.LocalDate expiry, String remarks);
    List<Medicine> matchingMedicineBatches(String name,java.time.LocalDate expiry);
    void mergeMedicineBatch(String name,int quantity,java.time.LocalDate expiry,String remarks);
    void mergeDuplicateMedicineBatches(String name,java.time.LocalDate expiry);
    void refillBatch(String batchId,int quantity,java.time.LocalDate expiry,String remarks);
    PageResult<Medicine> dispensableMedicinePage(int page,int size,String query);
    PageResult<MedicineTransaction> transactionPage(int page,int size,String query,
        LocalDateTime fromInclusive,LocalDateTime toExclusive);
    PageResult<Patient> patientPage(int page, int size, String filter);
    PageResult<Medicine> medicinePage(int page, int size, String filter);
    PageResult<MedicalRecord> recordPage(int page, int size, String filter);
    PageResult<MedicalRecord> recentRecordPage(int page, int size, String query, String staffId);
    PageResult<MedicalRecord> recentRecordPage(int page, int size, String query, String staffId,
        LocalDateTime from, LocalDateTime to);
    PageResult<NotificationLog> notificationPage(int page, int size, String filter);
    PageResult<Alert> alertPage(int page, int size, String query);
    int patientCount();
    int recordCount();
}
