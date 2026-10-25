package clinic.service;
import clinic.model.*;
import java.util.*;
import java.time.LocalDateTime;

/** Owns notification state and returns detached copies. No clinical free text is retained. */
public final class NotificationStore {
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
    public void update(String id,NotificationStatus status) {
        NotificationLog n=logs.get(id);
        if(n==null)throw new IllegalArgumentException("Notification not found.");
        if(status!=NotificationStatus.SENT||n.getStatus()!=NotificationStatus.PENDING)
            throw new IllegalArgumentException("Only PENDING to SENT is allowed.");
        n.markSent();
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
