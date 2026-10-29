package clinic.service;

import clinic.model.NotificationLog;

/** Recipient-aware notification creation without changing the public ClinicSystem contract. */
public interface RecipientNotificationWorkflow {
    NotificationLog createGuardianNotificationWithRecipient(String patientId,String transactionId,String reason,
        String recipientLabel,String recipientContact);
}
