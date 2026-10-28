package clinic.service;

import clinic.model.MedicalRecord;
import java.util.List;

/** Internal override-aware consultation operation; ClinicSystem stays unchanged. */
public interface ConsultationWorkflow {
    MedicalRecord recordVisitAndAdministerWithReason(String patientId,List<String> symptoms,String reason,
        String remarks,String staffId,List<DispenseItem> medicines,String overrideReason);
}
