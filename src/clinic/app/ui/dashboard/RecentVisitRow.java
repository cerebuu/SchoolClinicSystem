package clinic.app.ui.dashboard;

import clinic.model.MedicalRecord;
import clinic.model.Patient;

/** One bounded recent-visit query result with its detached patient profile. */
public record RecentVisitRow(MedicalRecord visit, Patient patient) {}
