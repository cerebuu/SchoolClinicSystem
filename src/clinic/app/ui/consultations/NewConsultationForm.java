package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.popup.FormField;
import clinic.app.ui.common.popup.FormPopup;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.app.ui.common.popup.MultiSelectField;
import clinic.app.ui.common.popup.AllergyConfirmPopup;
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
        ConsultationMedicinePanel medicines=app.current().canAccess(User.DISPENSE)
            ?new ConsultationMedicinePanel(app):null;
        List<FormField> fields=new java.util.ArrayList<>(List.of(
            FormField.text("Patient ID"),
            FormField.text("Symptoms"),
            FormField.text("Concern"), FormField.optional("Remarks")));
        Map<String,MultiSelectField> selections=new java.util.LinkedHashMap<>();
        selections.put("Symptoms",new MultiSelectField(FeatureRules.SYMPTOM_CHOICES));
        Map<String,javax.swing.JComponent> components=new java.util.LinkedHashMap<>();
        components.putAll(selections);
        if(medicines!=null) {
            fields.add(FormField.optional("Medicines administered (optional)"));
            components.put("Medicines administered (optional)",medicines);
        }
        FormPopup.showWithComponents(owner,"New Consultation",fields,components,(values,symptoms,controller)->{
            String patientId=values.get("Patient ID");
            try {
                clinic.findPatient(patientId).orElseThrow(()->
                    new IllegalArgumentException("Patient ID was not found. Enter a registered patient's ID."));
                List<clinic.service.DispenseItem> requested=medicines==null?List.of():medicines.items();
                boolean confirmed=false;
                if(!requested.isEmpty()) {
                    var profile=clinic.findPatient(patientId).orElseThrow();
                    if(!profile.getAllergies().isEmpty()) {
                        confirmed=AllergyConfirmPopup.show(owner,profile,requested);
                        if(!confirmed)return;
                    }
                }
                boolean allergyConfirmed=confirmed;
                controller.saveAsync(()->{
                    clinic.recordVisitAndAdminister(patientId,symptoms.get("Symptoms"),values.get("Concern"),
                        values.get("Remarks"),staffId,requested,allergyConfirmed);
                    return true;
                });
            } catch(RuntimeException error) {
                MessagePopup.error(owner,error);
            }
        }, () -> {
                saved.run();
                MessagePopup.info(owner, "Consultation saved.");
            });
    }
}
