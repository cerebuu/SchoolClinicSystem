package clinic.service;
import clinic.model.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.Duration;
import clinic.notify.Channel;

/** Owns notification state and returns detached copies. No clinical free text is retained. */
public final class NotificationStore {
    public static final int MAX_ATTEMPTS=3;
    public static final int COOLDOWN_SECONDS=30;
    private final Map<String,NotificationLog> logs=new LinkedHashMap<>();
    public void restore(List<NotificationLog> saved) {
        saved.forEach(n->logs.put(n.getNotificationId(),n.copy()));
    }
    private String nextId() { return UUID.randomUUID().toString(); }
    public NotificationLog create(Patient patient,MedicineTransaction transaction,String reason) {
        if(patient==null)throw new IllegalArgumentException("Patient not found.");
        boolean employee=patient instanceof Employee;
        String contact=employee?patient.getContactInfo():patient.getGuardianContact();
        if(!employee&&(contact==null||contact.isBlank()))throw new IllegalStateException("No guardian contact on file");
        if(transaction==null||!transaction.getPatientId().equals(patient.getId()))
            throw new IllegalArgumentException("Transaction does not belong to patient.");
        clinic.util.Validation.required(reason,"Reason");
        NotificationLog n=new NotificationLog(nextId(),patient.getId(),transaction.getTransactionId(),
            "Clinic care notification",LocalDateTime.now(),patient.getName(),transaction.getMedicineName(),
            GuardianMessageTemplates.MessageType.MEDICINE_GIVEN,"the school clinic",
            employee?"Employee contact (own)":"Guardian contact",contact);
        logs.put(n.getNotificationId(),n);
        return n.copy();
    }
    public NotificationLog createEmergency(Patient patient) {
        if (patient == null) throw new IllegalArgumentException("Patient not found.");
        boolean employee=patient instanceof Employee;
        String contact=employee?patient.getContactInfo():patient.getGuardianContact();
        NotificationLog n=new NotificationLog(nextId(),patient.getId(),null,
            "Emergency notification",LocalDateTime.now(),patient.getName(),null,
            GuardianMessageTemplates.MessageType.EMERGENCY,"the school clinic",
            employee?"Employee contact (own)":"Guardian contact",contact);
        logs.put(n.getNotificationId(),n);
        return n.copy();
    }
    public void update(String id,NotificationStatus status) {
        NotificationLog n=logs.get(id);
        if(n==null)throw new IllegalArgumentException("Notification not found.");
        if(status!=NotificationStatus.SENT
                ||(n.getStatus()!=NotificationStatus.PENDING&&n.getStatus()!=NotificationStatus.FAILED))
            throw new IllegalArgumentException("Only pending or failed notifications can be marked sent manually.");
        n.markSent();
    }
    public NotificationLog beginAttempt(String id,Channel channel,LocalDateTime attemptedAt) {
        NotificationLog n=required(id);
        if(n.getStatus()==NotificationStatus.SENT)throw new IllegalStateException("This notification has already been sent.");
        if(n.getAttemptCount()>=MAX_ATTEMPTS)throw new IllegalStateException("Maximum of three send attempts reached.");
        if(attemptedAt==null)throw new IllegalArgumentException("Attempt time is required.");
        LocalDateTime previous=n.getLastAttemptTime();
        if(previous!=null&&Duration.between(previous,attemptedAt).getSeconds()<COOLDOWN_SECONDS)
            throw new IllegalStateException("Please wait 30 seconds between send attempts.");
        n.beginAttempt(channel,attemptedAt);
        return n.copy();
    }
    public void completeAttempt(String id,NotificationStatus status) {
        required(id).completeAttempt(status);
    }
    private NotificationLog required(String id) {
        NotificationLog n=logs.get(id);
        if(n==null)throw new IllegalArgumentException("Notification not found.");
        return n;
    }
    public List<NotificationLog> forPatient(Patient patient) {
        if(patient==null)throw new IllegalArgumentException("Patient not found.");
        return all().stream().filter(n->n.getPatientId().equals(patient.getId())).toList();
    }
    public List<NotificationLog> all() {
        List<NotificationLog> newest=new ArrayList<>(logs.values());
        Collections.reverse(newest);
        newest.sort(Comparator.comparing(NotificationLog::getDateTime).reversed());
        return newest.stream().map(NotificationLog::copy).toList();
    }
    public PageResult<NotificationLog> page(int page,int size,String filter) {
        return PageResult.select(all(),page,size,n->filter==null||filter.isBlank()
            ||n.getPatientId().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)));
    }
}
