package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.Actions;
import clinic.app.ui.common.PatientLookupField;
import clinic.app.ui.common.popup.FormField;
import clinic.app.ui.common.popup.FormPopup;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.app.ui.common.popup.MultiSelectField;
import clinic.app.ui.common.popup.AllergyConfirmPopup;
import clinic.app.ui.dispense.DispenseItemsPanel;
import clinic.service.DispenseItem;
import clinic.service.DispenseWorkflow;
import clinic.service.FeatureRules;
import clinic.service.SafetyChecker;
import clinic.service.ConsultationWorkflow;
import java.util.Map;
import clinic.core.ClinicSystem;
import clinic.model.User;
import clinic.model.Patient;
import java.awt.Component;
import java.util.List;

/** Shared consultation form configuration for the dashboard and Consultations tab. */
public final class NewConsultationForm {
    private NewConsultationForm() {}

    public static void open(Component owner, ApplicationContext app, Runnable saved) {
        final ClinicSystem clinic;
        final String staffId;
        final User sessionUser;
        final long sessionVersion;
        try {
            synchronized(app.login) {
                app.require(User.RECORD_TRANSACTION);
                clinic = app.clinic();
                sessionUser=app.current();
                sessionVersion=app.login.getSessionVersion();
                staffId = sessionUser.getUserId();
            }
        } catch (RuntimeException error) {
            MessagePopup.error(owner, error);
            return;
        }
        DispenseItemsPanel medicines=app.current().canAccess(User.DISPENSE)
            ?new DispenseItemsPanel(app,true):null;
        List<FormField> fields=new java.util.ArrayList<>(List.of(
            FormField.text("Patient ID"),
            FormField.text("Symptoms"),
            FormField.text("Concern"), FormField.optional("Remarks")));
        Map<String,MultiSelectField> selections=new java.util.LinkedHashMap<>();
        selections.put("Symptoms",new MultiSelectField(FeatureRules.SYMPTOM_CHOICES));
        Map<String,javax.swing.JComponent> components=new java.util.LinkedHashMap<>();
        components.putAll(selections);
        PatientLookupField patientLookup=new PatientLookupField(app,PatientLookupField.Mode.EXISTING,"Patient ID");
        components.put("Patient ID",patientLookup);
        if(medicines!=null) {
            patientLookup.addPropertyChangeListener(PatientLookupField.PATIENT_PROPERTY,event->
                medicines.setPatient((Patient)event.getNewValue()));
            fields.add(FormField.optional("Medicines administered (optional)"));
            components.put("Medicines administered (optional)",medicines);
        }
        FormPopup.showWithComponents(owner,"New Consultation",fields,components,(values,symptoms,controller)->{
            String patientId=values.get("Patient ID");
            try {
                List<clinic.service.DispenseItem> requested=medicines==null?List.of():medicines.items();
                controller.prepareAsync(()->{
                    Patient profile=clinic.findPatient(patientId).orElseThrow(()->
                        new IllegalArgumentException("Patient ID was not found. Enter a registered patient's ID."));
                    boolean warning=false;
                    for(DispenseItem item:requested) {
                        List<String> safety=clinic.checkSafety(patientId,item.medicine());
                        List<String> hard=safety.stream()
                            .filter(value->!value.equals(SafetyChecker.ALLERGY_CONFIRMATION)).toList();
                        if(!hard.isEmpty())throw new IllegalStateException(String.join("; ",hard));
                        warning|=safety.contains(SafetyChecker.ALLERGY_CONFIRMATION);
                    }
                    verifyStock(clinic,requested);
                    return new PreparedConsultation(profile,warning);
                },prepared->{
                    if(!owner.isShowing()||!Actions.isCurrentSession(app.login,sessionVersion,sessionUser)) {
                        controller.abort();
                        return;
                    }
                    String overrideReason="";
                    if(!requested.isEmpty()&&prepared.allergyWarning()) {
                        var confirmation=AllergyConfirmPopup.show(owner,prepared.patient(),requested);
                        if(confirmation.isEmpty())return;
                        overrideReason=confirmation.get();
                    }
                    String finalOverrideReason=overrideReason;
                    controller.saveAsync(()->{
                        verifyStock(clinic,requested);
                        ((ConsultationWorkflow)clinic).recordVisitAndAdministerWithReason(patientId,
                            symptoms.get("Symptoms"),values.get("Concern"),values.get("Remarks"),staffId,
                            requested,finalOverrideReason);
                        return true;
                    });
                });
            } catch(RuntimeException error) {
                MessagePopup.error(owner,error);
            }
        }, () -> {
                if(!owner.isShowing()||!Actions.isCurrentSession(app.login,sessionVersion,sessionUser))return;
                saved.run();
                MessagePopup.info(owner, "Consultation saved.");
            });
    }

    static void verifyStock(ClinicSystem clinic,List<DispenseItem> requested) {
        List<DispenseItem> items=requested==null||requested.isEmpty()?List.of():DispenseWorkflow.merge(requested);
        for(DispenseItem item:items) {
            int available=clinic.getStock(item.product());
            if(item.quantity()>available)throw new IllegalStateException("Only "+available+" left in stock for "
                +item.product().displayName()+". Nothing was recorded or dispensed.");
        }
    }

    private record PreparedConsultation(Patient patient,boolean allergyWarning) {}
}
