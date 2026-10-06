package clinic.model;

import java.time.LocalDateTime;

import clinic.service.GuardianMessageTemplates;

// This class is responsible for guardian notifications and their status.
//
// Attributes (private): notificationId, patientId, transactionId, reason (String), dateTime (LocalDateTime), status (NotificationStatus).
// Constructor sets status to PENDING.
// Methods: markSent(), getStatus(), toMessage() returns ready-to-send guardian message text.
// Getters for all attributes.
public class NotificationLog {

    private final String notificationId;
    private final String patientId;
    private final String transactionId;
    private final String reason;
    private final LocalDateTime dateTime;
    private NotificationStatus status;
    private final String patientName;
    private final String medicineName;
    private final GuardianMessageTemplates.MessageType messageType;
    private final String clinicContact;

    public NotificationLog(String notificationId, String patientId, String transactionId,
                           String reason, LocalDateTime dateTime) {
        this(notificationId, patientId, transactionId, reason, dateTime, patientId,
                null, GuardianMessageTemplates.MessageType.CLINIC_VISIT, "the school clinic");
    }

    public NotificationLog(String notificationId, String patientId, String transactionId,
                           String reason, LocalDateTime dateTime, String patientName,
                           String medicineName,
                           GuardianMessageTemplates.MessageType messageType,
                           String clinicContact) {
        require(notificationId, "notification ID");
        require(patientId, "patient ID");
        require(transactionId, "transaction ID");
        require(reason, "reason");
        if (dateTime == null) {
            throw new IllegalArgumentException("date and time is required.");
        }
        require(patientName, "patient name");
        require(clinicContact, "clinic contact");
        if (messageType == null) {
            throw new IllegalArgumentException("message type is required.");
        }
        this.notificationId = notificationId;
        this.patientId = patientId;
        this.transactionId = transactionId;
        this.reason = reason;
        this.dateTime = dateTime;
        this.status = NotificationStatus.PENDING;
        this.patientName = patientName;
        this.medicineName = medicineName;
        this.messageType = messageType;
        this.clinicContact = clinicContact;
    }

    public void markSent() {
        status = NotificationStatus.SENT;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String toMessage() {
        return GuardianMessageTemplates.create(messageType, patientName, reason,
                medicineName, dateTime, clinicContact);
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    private static void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
    }
}
