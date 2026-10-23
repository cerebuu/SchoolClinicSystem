package clinic.service;

import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.LocalDateTime;

/** Internal query contract shared by the memory fixture and durable repository adapter. */
public interface ClinicData extends ClinicSystem {
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
