package clinic.model;

import java.time.LocalDateTime;

import clinic.notify.Channel;
import clinic.service.GuardianMessageTemplates;

// This class is responsible for guardian notifications and their status.
//
// Stores a patient-linked message, delivery state, selected channel, and attempt metadata.
// Constructor sets status to PENDING.
// toMessage() remains the legacy display form; messageBody() excludes recipient contact data.
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
    private final String recipientLabel, recipientContact;
    private final String campusId;
    private Channel channel;
    private int attemptCount;
    private LocalDateTime lastAttemptTime;

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
        this(notificationId,patientId,transactionId,reason,dateTime,patientName,medicineName,messageType,clinicContact,null,null);
    }

    public NotificationLog(String notificationId,String patientId,String transactionId,String reason,
            LocalDateTime dateTime,String patientName,String medicineName,
            GuardianMessageTemplates.MessageType messageType,String clinicContact,String recipientLabel,String recipientContact) {
        this(notificationId,patientId,transactionId,reason,dateTime,patientName,medicineName,
            messageType,clinicContact,recipientLabel,recipientContact,null);
    }

    /** Campus-aware constructor; older callers retain their original null-campus behavior. */
    public NotificationLog(String notificationId,String patientId,String transactionId,String reason,
            LocalDateTime dateTime,String patientName,String medicineName,
            GuardianMessageTemplates.MessageType messageType,String clinicContact,String recipientLabel,
            String recipientContact,String campusId) {
        require(notificationId, "notification ID");
        require(patientId, "patient ID");
        require(reason, "reason");
        if (dateTime == null) {
            throw new IllegalArgumentException("date and time is required.");
        }
        require(patientName, "patient name");
        require(clinicContact, "clinic contact");
        if (messageType == null) {
            throw new IllegalArgumentException("message type is required.");
        }
        if (transactionId == null && messageType != GuardianMessageTemplates.MessageType.EMERGENCY) {
            throw new IllegalArgumentException("transaction ID is required.");
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
        this.recipientLabel = recipientLabel;
        this.recipientContact = recipientContact;
        this.campusId=campusId==null||campusId.isBlank()?null:clinic.util.Validation.required(campusId,"Campus ID");
    }

    public void markSent() {
        status = NotificationStatus.SENT;
    }

    /** Detached snapshot: status changes must go through the authorized service. */
    public NotificationLog copy() {
        NotificationLog result = new NotificationLog(notificationId, patientId, transactionId,
            reason, dateTime, patientName, medicineName, messageType, clinicContact, recipientLabel,
            recipientContact,campusId);
        result.restoreState(status, channel, attemptCount, lastAttemptTime);
        return result;
    }

    public void beginAttempt(Channel selectedChannel, LocalDateTime attemptedAt) {
        if (selectedChannel == null || attemptedAt == null) {
            throw new IllegalArgumentException("Notification channel and attempt time are required.");
        }
        channel = selectedChannel;
        attemptCount++;
        lastAttemptTime = attemptedAt;
        status = NotificationStatus.PENDING;
    }

    public void completeAttempt(NotificationStatus result) {
        if ((result != NotificationStatus.SENT && result != NotificationStatus.FAILED)
                || status != NotificationStatus.PENDING || attemptCount == 0) {
            throw new IllegalArgumentException("A pending notification attempt is required.");
        }
        status = result;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String toMessage() {
        return (recipientContact==null?"":recipientLabel+": "+recipientContact+"\n")+messageBody();
    }

    public String messageBody() {
        return GuardianMessageTemplates.create(messageType, patientName, reason,
            medicineName, dateTime, clinicContact);
    }
    public String messageSubject() { return "School Clinic notification"; }

    public String getNotificationId() {
        return notificationId;
    }
    public String getRecipientLabel() { return recipientLabel; }
    public String getRecipientContact() { return recipientContact; }
    public String getPatientName() { return patientName; }
    public GuardianMessageTemplates.MessageType getMessageType() { return messageType; }
    public Channel getChannel() { return channel; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getLastAttemptTime() { return lastAttemptTime; }
    public void restoreState(NotificationStatus restoredStatus, Channel restoredChannel,
            int restoredAttemptCount, LocalDateTime restoredLastAttempt) {
        if (restoredStatus == null || restoredAttemptCount < 0) {
            throw new IllegalArgumentException("Invalid notification state.");
        }
        status = restoredStatus;
        channel = restoredChannel;
        attemptCount = restoredAttemptCount;
        lastAttemptTime = restoredLastAttempt;
    }

    public String getPatientId() {
        return patientId;
    }
    public String getCampusId() { return campusId; }

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
