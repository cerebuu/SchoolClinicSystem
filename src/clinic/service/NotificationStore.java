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
        return create(patient,transaction,reason,null);
    }
    public NotificationLog create(Patient patient,MedicineTransaction transaction,String reason,String campusId) {
        if(patient==null)throw new IllegalArgumentException("Patient not found.");
        GuardianMessageTemplates.MessageType type=messageType(reason);
        boolean employee=patient instanceof Employee;
        String contact=employee?patient.getContactInfo():patient.getGuardianContact();
        if(contact==null||contact.isBlank())
            throw new IllegalStateException(employee?"No employee contact on file":"No guardian contact on file");
        if(type==GuardianMessageTemplates.MessageType.MEDICINE_GIVEN&&transaction==null)
            throw new IllegalArgumentException("Select a transaction for a medicine notification.");
        if(transaction!=null&&!transaction.getPatientId().equals(patient.getId()))
            throw new IllegalArgumentException("Transaction does not belong to patient.");
        if(transaction!=null&&campusId!=null&&!campusId.equals(transaction.getCampusId()))
            throw new SecurityException("Access denied.");
        clinic.util.Validation.required(reason,"Reason");
        NotificationLog n=new NotificationLog(nextId(),patient.getId(),transaction==null?null:transaction.getTransactionId(),
            displayName(type),LocalDateTime.now(),patient.getName(),transaction==null?null:transaction.getMedicineName(),
            type,"the school clinic",
            employee?"Employee contact (own)":"Guardian contact",contact,campusId);
        logs.put(n.getNotificationId(),n);
        return n.copy();
    }
    private static GuardianMessageTemplates.MessageType messageType(String value) {
        String key=value==null?"":value.trim().replace('-', '_').replace(' ','_').toUpperCase(Locale.ROOT);
        return switch(key) {
            case "CLINIC_VISIT" -> GuardianMessageTemplates.MessageType.CLINIC_VISIT;
            case "SENT_HOME" -> GuardianMessageTemplates.MessageType.SENT_HOME;
            case "FOLLOW_UP_NEEDED", "FOLLOW_UP" -> GuardianMessageTemplates.MessageType.FOLLOW_UP_NEEDED;
            case "EMERGENCY" -> GuardianMessageTemplates.MessageType.EMERGENCY;
            case "MEDICINE_GIVEN", "MEDICINE" -> GuardianMessageTemplates.MessageType.MEDICINE_GIVEN;
            default -> GuardianMessageTemplates.MessageType.MEDICINE_GIVEN;
        };
    }
    private static String displayName(GuardianMessageTemplates.MessageType type) {
        return switch(type) {
            case CLINIC_VISIT -> "Clinic visit";
            case MEDICINE_GIVEN -> "Medicine given";
            case SENT_HOME -> "Sent home";
            case FOLLOW_UP_NEEDED -> "Follow-up needed";
            case EMERGENCY -> "Emergency";
        };
    }
    public NotificationLog createEmergency(Patient patient) {
        return createEmergency(patient,null);
    }
    public NotificationLog createEmergency(Patient patient,String campusId) {
        if (patient == null) throw new IllegalArgumentException("Patient not found.");
        boolean employee=patient instanceof Employee;
        String contact=employee?patient.getContactInfo():patient.getGuardianContact();
        NotificationLog n=new NotificationLog(nextId(),patient.getId(),null,
            "Emergency notification",LocalDateTime.now(),patient.getName(),null,
            GuardianMessageTemplates.MessageType.EMERGENCY,"the school clinic",
            employee?"Employee contact (own)":"Guardian contact",contact,campusId);
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
    public void update(String id,NotificationStatus status,String campusId) {
        NotificationLog n=requiredForCampus(id,campusId);
        if(status!=NotificationStatus.SENT
                ||(n.getStatus()!=NotificationStatus.PENDING&&n.getStatus()!=NotificationStatus.FAILED))
            throw new IllegalArgumentException("Only pending or failed notifications can be marked sent manually.");
        n.markSent();
    }
    public NotificationLog beginAttempt(String id,Channel channel,LocalDateTime attemptedAt) {
        return beginAttempt(required(id),channel,attemptedAt);
    }
    public NotificationLog beginAttempt(String id,Channel channel,LocalDateTime attemptedAt,String campusId) {
        return beginAttempt(requiredForCampus(id,campusId),channel,attemptedAt);
    }
    private NotificationLog beginAttempt(NotificationLog n,Channel channel,LocalDateTime attemptedAt) {
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
    public void completeAttempt(String id,NotificationStatus status,String campusId) {
        requiredForCampus(id,campusId).completeAttempt(status);
    }
    private NotificationLog required(String id) {
        NotificationLog n=logs.get(id);
        if(n==null)throw new IllegalArgumentException("Notification not found.");
        return n;
    }
    private NotificationLog requiredForCampus(String id,String campusId) {
        String campus=clinic.util.Validation.required(campusId,"Campus ID");
        NotificationLog n=logs.get(id);
        if(n==null||!campus.equals(n.getCampusId()))throw new SecurityException("Access denied.");
        return n;
    }
    public List<NotificationLog> forPatient(Patient patient) {
        if(patient==null)throw new IllegalArgumentException("Patient not found.");
        return all().stream().filter(n->n.getPatientId().equals(patient.getId())).toList();
    }
    public List<NotificationLog> forPatient(Patient patient,String campusId) {
        if(patient==null)throw new IllegalArgumentException("Patient not found.");
        String campus=clinic.util.Validation.required(campusId,"Campus ID");
        return all().stream().filter(n->n.getPatientId().equals(patient.getId())
            &&campus.equals(n.getCampusId())).toList();
    }
    public List<NotificationLog> all() {
        List<NotificationLog> newest=new ArrayList<>(logs.values());
        Collections.reverse(newest);
        newest.sort(Comparator.comparing(NotificationLog::getDateTime).reversed());
        return newest.stream().map(NotificationLog::copy).toList();
    }
    public PageResult<NotificationLog> page(int page,int size,String filter) {
        return page(page,size,filter,null,false);
    }
    public PageResult<NotificationLog> page(int page,int size,String filter,String campusId) {
        return page(page,size,filter,clinic.util.Validation.required(campusId,"Campus ID"),true);
    }
    private PageResult<NotificationLog> page(int page,int size,String filter,String campusId,boolean scoped) {
        String query=filter==null?"":filter.trim().toLowerCase(Locale.ROOT);
        return PageResult.select(all(),page,size,n->(!scoped||campusId.equals(n.getCampusId()))
            &&(query.isEmpty()||n.getPatientId().toLowerCase(Locale.ROOT).contains(query)));
    }
}
