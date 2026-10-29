package clinic.app.ui.notifications;

import clinic.app.ApplicationContext;
import clinic.model.NotificationLog;
import clinic.model.Student;
import clinic.persistence.ClinicRepository;
import clinic.persistence.Database;
import clinic.service.AuditLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Recipient lookup, validation, and one-time notification persistence checks. */
public final class RecipientSelectorChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        persistentLookupNormalizesPatientId();
        AuditLog audit=new AuditLog();
        try(ApplicationContext app=new ApplicationContext(true,audit)) {
            app.login.login("N001","nurse123").orElseThrow();
            Student both=new Student("S-2024001","Fixture student","09178284667","BSIT",3,"BSIT 3-A");
            both.setGuardianContact("guardian@example.test");
            Student guardianOnly=new Student("S-2024002","Guardian only","invalid","BSIT",3,"BSIT 3-A");
            guardianOnly.setGuardianContact("09170000001");
            Student noContact=new Student("S-2024003","Manual recipient","invalid","BSIT",3,"BSIT 3-A");
            app.clinic().registerPatient(both);
            app.clinic().registerPatient(guardianOnly);
            app.clinic().registerPatient(noContact);

            var found=app.clinic().findPatient("  s-2024001 ").orElseThrow();
            check(found.getContactInfo().equals("09178284667"),"trimmed lowercase patient ID finds the profile");
            RecipientSelector selector=create();
            onEdt(()->selector.setPatient(found));
            check(selector.isValidRecipient()
                &&selector.recipientContact().equals("+639178284667")
                &&selector.recipientLabel().equals("Patient contact"),
                "patient contact is listed, normalized, and preselected");
            check(CreateNotificationPopup.canSave(false,true,selector.isValidRecipient(),
                clinic.service.GuardianMessageTemplates.MessageType.EMERGENCY,false),
                "a patient with a saved contact enables emergency Save");
            check(selector.savedContactOptions().equals(List.of(
                "Patient contact · +63 9** *** **67","Guardian contact · g***@example.test")),
                "all saved contact types are listed with masked values");
            onEdt(()->selector.selectSavedContact(1));
            check(selector.isValidRecipient()&&selector.recipientLabel().equals("Guardian contact")
                &&selector.recipientContact().equals("guardian@example.test")
                &&selector.channelDescription().equals("EMAIL"),
                "saved guardian email selects Email channel");
            onEdt(()->selector.selectSavedContact(0));
            onEdt(()->selector.manualTextField().setText("manual@example.test"));
            check(selector.isValidRecipient()&&selector.recipientLabel().equals("Entered manually")
                &&selector.recipientContact().equals("manual@example.test")
                &&selector.channelDescription().contains("EMAIL"),
                "manual email overrides the selected saved contact");
            NotificationLog emergency=app.createNotificationForRecipient(found.getId(),null,"EMERGENCY",
                selector.recipientLabel(),selector.recipientContact());
            check(emergency.getMessageType()==clinic.service.GuardianMessageTemplates.MessageType.EMERGENCY
                &&emergency.messageBody().startsWith("URGENT:"),
                "emergency flow retains its emergency message template");
            Student profileAfterEmergency=(Student)app.clinic().findPatient(found.getId()).orElseThrow();
            check(profileAfterEmergency.getContactInfo().equals("09178284667")
                &&Objects.equals(profileAfterEmergency.getGuardianContact(),"guardian@example.test"),
                "one-time emergency contact does not change patient profile");
            Student guardian=(Student)app.clinic().findPatient("S-2024002").orElseThrow();
            onEdt(()->selector.setPatient(guardian));
            check(selector.isValidRecipient()&&selector.recipientLabel().equals("Guardian contact"),
                "guardian-only contact is selected");
            check(CreateNotificationPopup.canSave(false,true,selector.isValidRecipient(),
                clinic.service.GuardianMessageTemplates.MessageType.EMERGENCY,false),
                "a patient with only a guardian contact enables emergency Save");
            Student repeated=new Student("S-2024005","Repeated contact","09170000005","BSIT",3,"BSIT 3-A");
            repeated.setGuardianContact("09170000005");
            onEdt(()->selector.setPatient(repeated));
            check(selector.savedContactOptions().equals(List.of(
                "Patient contact · +63 9** *** **05","Guardian contact · +63 9** *** **05")),
                "same value in separate patient and guardian fields retains both labels");
            Student empty=(Student)app.clinic().findPatient("S-2024003").orElseThrow();
            onEdt(()->selector.setPatient(empty));
            check(!selector.isValidRecipient()
                &&selector.savedContactMessage().equals("No saved contact. Enter a number or email below to continue."),
                "patient without saved contacts gets the manual-entry instruction");
            check(!CreateNotificationPopup.canSave(false,true,selector.isValidRecipient(),
                clinic.service.GuardianMessageTemplates.MessageType.EMERGENCY,false),
                "patient without a recipient cannot save yet");
            onEdt(()->selector.manualTextField().setText("123"));
            check(!selector.isValidRecipient()&&!selector.validationMessage().isBlank(),
                "invalid manual recipient is rejected with inline error");
            check(!CreateNotificationPopup.canSave(false,true,selector.isValidRecipient(),
                clinic.service.GuardianMessageTemplates.MessageType.EMERGENCY,false),
                "invalid manual recipient keeps Save disabled");
            onEdt(()->selector.manualTextField().setText("nurse@invalid"));
            check(!selector.isValidRecipient()&&!selector.validationMessage().isBlank(),
                "invalid manual email is rejected with inline error");
            onEdt(()->selector.manualTextField().setText("09171234567"));
            check(selector.isValidRecipient()&&selector.recipientContact().equals("+639171234567"),
                "valid Philippine mobile is normalized");
            check(CreateNotificationPopup.canSave(false,true,selector.isValidRecipient(),
                clinic.service.GuardianMessageTemplates.MessageType.EMERGENCY,false),
                "a valid manually entered recipient enables emergency Save");
            var phoneSaved=app.createNotificationForRecipient(empty.getId(),null,"CLINIC_VISIT",
                selector.recipientLabel(),selector.recipientContact());
            check(phoneSaved.getRecipientContact().equals("+639171234567"),
                "normalized manual number is stored in the notification log");
            onEdt(()->selector.manualTextField().setText("+63 917 123 4567"));
            check(selector.isValidRecipient()&&selector.recipientContact().equals("+639171234567")
                &&selector.channelDescription().contains("SMS"),
                "international Philippine mobile input is normalized and selects SMS");
            onEdt(()->selector.manualTextField().setText("nurse@example.test"));
            check(selector.isValidRecipient()&&selector.recipientContact().equals("nurse@example.test")
                &&selector.recipientLabel().equals("Entered manually"),"valid manual email is accepted");

            var saved=app.createNotificationForRecipient(empty.getId(),null,"CLINIC_VISIT",
                selector.recipientLabel(),selector.recipientContact());
            check(saved.getRecipientLabel().equals("Entered manually")
                &&saved.getRecipientContact().equals("nurse@example.test"),
                "manual contact is stored in the notification log");
            check(app.clinic().findPatient(empty.getId()).orElseThrow().getContactInfo().equals("invalid"),
                "manual contact does not update the patient profile");
            check(audit.getAll().stream().noneMatch(entry->entry.getDetails().contains("nurse@example.test")
                ||entry.getDetails().contains("+639171234567")
                ||entry.getDetails().contains("manual@example.test")),
                "recipient contact is excluded from audit details");
            var recipientAudits=audit.getAll().stream()
                .filter(entry->entry.getAction().equals("createGuardianNotification")&&entry.isAllowed()).toList();
            check(recipientAudits.size()==3&&recipientAudits.stream()
                .allMatch(entry->entry.getDetails().startsWith("notificationId=")),
                "recipient creation uses the secured notification audit boundary");
        }
        System.out.println("PASS: "+checks+" recipient selector checks.");
    }

    private static void persistentLookupNormalizesPatientId() throws Exception {
        Path directory=Files.createTempDirectory("scms-recipient-lookup-");
        try(Database database=new Database(directory.resolve("clinic"))) {
            var data=ClinicRepository.create(database);
            Student patient=new Student("S-2024004","Persistent recipient","09170000004","BSIT",3,"BSIT 3-A");
            patient.setGuardianContact("guardian@example.test");
            data.registerPatient(patient);
            Student found=(Student)data.findPatient("  s-2024004 ").orElseThrow();
            check(found.getContactInfo().equals("09170000004")
                &&Objects.equals(found.getGuardianContact(),"guardian@example.test"),
                "database lookup trims and normalizes ID while loading both saved contacts");
        } finally {
            try(var paths=Files.walk(directory)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
    }

    private static RecipientSelector create() throws Exception {
        AtomicReference<RecipientSelector> result=new AtomicReference<>();
        onEdt(()->result.set(new RecipientSelector(valid->{})));
        return result.get();
    }
    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        checks++;
    }
}
