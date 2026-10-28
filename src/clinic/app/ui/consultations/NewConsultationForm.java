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
            fields.add(FormField.optional("Medicines administered (optional)"));
            components.put("Medicines administered (optional)",medicines);
        }
        FormPopup.showWithComponents(owner,"New Consultation",fields,components,(values,symptoms,controller)->{
            String patientId=values.get("Patient ID");
            try {
                List<clinic.service.DispenseItem> requested=medicines==null?List.of():medicines.items();
                controller.prepareAsync(()->clinic.findPatient(patientId).orElseThrow(()->
                    new IllegalArgumentException("Patient ID was not found. Enter a registered patient's ID.")),profile->{
                    if(!owner.isShowing()||!Actions.isCurrentSession(app.login,sessionVersion,sessionUser)) {
                        controller.abort();
                        return;
                    }
                    boolean confirmed=false;
                    if(!requested.isEmpty()&&!profile.getAllergies().isEmpty()) {
                        confirmed=AllergyConfirmPopup.show(owner,profile,requested);
                        if(!confirmed) return;
                    }
                    boolean allergyConfirmed=confirmed;
                    controller.saveAsync(()->{
                        verifyStock(clinic,requested);
                        clinic.recordVisitAndAdminister(patientId,symptoms.get("Symptoms"),values.get("Concern"),
                            values.get("Remarks"),staffId,requested,allergyConfirmed);
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
            if(item.quantity()>available)throw new IllegalStateException(item.product().displayName()
                +": requested "+item.quantity()+", available "+available
                +" unexpired units. Nothing was recorded or dispensed.");
        }
    }
}
