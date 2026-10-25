package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.popup.FormField;
import clinic.app.ui.common.popup.FormPopup;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.app.ui.common.popup.MultiSelectField;
import clinic.service.FeatureRules;
import java.util.Map;
import clinic.core.ClinicSystem;
import clinic.model.User;
import java.awt.Component;
import java.util.List;

/** Shared consultation form configuration for the dashboard and Consultations tab. */
public final class NewConsultationForm {
    private NewConsultationForm() {}

    public static void open(Component owner, ApplicationContext app, Runnable saved) {
        final ClinicSystem clinic;
        final String staffId;
        try {
            app.require(User.RECORD_TRANSACTION);
            clinic = app.clinic();
            staffId = app.current().getUserId();
        } catch (RuntimeException error) {
            MessagePopup.error(owner, error);
            return;
        }
        FormPopup.show(owner, "New Consultation", List.of(
            FormField.text("Patient ID"),
            FormField.text("Symptoms"),
            FormField.text("Reason"), FormField.optional("Remarks")),
            Map.of("Symptoms",new MultiSelectField(FeatureRules.SYMPTOM_CHOICES)), (values,selections) -> {
                String patientId = values.get("Patient ID");
                clinic.findPatient(patientId).orElseThrow(() ->
                    new IllegalArgumentException("Patient ID was not found. Enter a registered patient's ID."));
                clinic.recordVisit(patientId, selections.get("Symptoms"), values.get("Reason"),
                    values.get("Remarks"), staffId);
            }, () -> {
                saved.run();
                MessagePopup.info(owner, "Consultation saved.");
            });
    }
}
