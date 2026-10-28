package clinic.notify;

import clinic.core.ClinicSystem;
import clinic.model.NotificationLog;
import clinic.model.NotificationStatus;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class NotifierService {
    public static final int SMS_LIMIT=160;
    private final ClinicSystem clinic;
    private final NotifierConfig config;
    private final Map<Channel,MessageSender> senders;
    private final Supplier<LocalDateTime> clock;

    public NotifierService(ClinicSystem clinic,NotifierConfig config) {
        this(clinic,config,configuredSenders(config),LocalDateTime::now);
    }

    public NotifierService(ClinicSystem clinic,NotifierConfig config,Map<Channel,MessageSender> senders) {
        this(clinic,config,senders,LocalDateTime::now);
    }

    public NotifierService(ClinicSystem clinic,NotifierConfig config,
            Map<Channel,MessageSender> senders,Supplier<LocalDateTime> clock) {
        this.clinic=java.util.Objects.requireNonNull(clinic);
        this.config=java.util.Objects.requireNonNull(config);
        this.senders=new EnumMap<>(Channel.class);
        this.senders.putAll(senders);
        this.clock=java.util.Objects.requireNonNull(clock);
    }

    public static NotifierService fromDefault(ClinicSystem clinic) {
        NotifierConfig config=NotifierConfig.load();
        return new NotifierService(clinic,config);
    }

    public List<NotificationRecipient.Option> recipients(NotificationLog notification) {
        return NotificationRecipient.options(notification);
    }

    public String preview(NotificationLog notification,Channel channel) {
        String text=notification.messageBody();
        return channel==Channel.SMS?limitSms(text):text;
    }

    public SendResult send(NotificationLog selected,Channel channel) {
        NotificationLog stored;
        NotificationRecipient.Option destination;
        try {
            stored=clinic.getNotifications(selected.getPatientId()).stream()
                .filter(row->row.getNotificationId().equals(selected.getNotificationId()))
                .findFirst().orElseThrow(()->new IllegalArgumentException("Notification not found."));
            destination=NotificationRecipient.select(stored,channel);
        } catch(SecurityException|IllegalArgumentException|IllegalStateException
                |clinic.persistence.StorageException failure) {
            return SendResult.failure(safeMessage(failure));
        }
        if(config.loadError()!=null)return SendResult.failure(config.loadError());
        MessageSender sender=senders.get(channel);
        if(sender==null)return SendResult.failure("No sender is configured for "+channel+".");
        NotificationLog attempt;
        try {
            attempt=clinic.beginNotificationAttempt(stored.getNotificationId(),channel,clock.get());
        } catch(SecurityException|IllegalArgumentException|IllegalStateException
                |clinic.persistence.StorageException failure) {
            return SendResult.failure(safeMessage(failure));
        }
        String body=preview(attempt,channel);
        SendResult result;
        try {
            result=sender.send(destination.recipient(),attempt.messageSubject(),body);
            if(result==null)result=SendResult.failure("Sender did not return a result.");
        } catch(RuntimeException failure) {
            java.util.logging.Logger.getLogger(NotifierService.class.getName()).warning(
                "Message sender failed ("+failure.getClass().getSimpleName()+").");
            result=SendResult.failure("The sender failed unexpectedly. Please retry.");
        }
        try {
            clinic.completeNotificationAttempt(attempt.getNotificationId(),
                result.success()?NotificationStatus.SENT:NotificationStatus.FAILED);
        } catch(SecurityException|IllegalArgumentException|IllegalStateException
                |clinic.persistence.StorageException failure) {
            return SendResult.failure("The send result could not be saved. Contact an administrator before retrying.");
        }
        return result;
    }

    public static String limitSms(String body) {
        int characters=body.codePointCount(0,body.length());
        if(characters<=SMS_LIMIT)return body;
        int end=body.offsetByCodePoints(0,SMS_LIMIT-3);
        return body.substring(0,end)+"...";
    }

    private static String safeMessage(RuntimeException failure) {
        String message=failure.getMessage();
        return message==null||message.isBlank()?"Notification could not be sent.":message;
    }

    private static Map<Channel,MessageSender> configuredSenders(NotifierConfig config) {
        EnumMap<Channel,MessageSender> result=new EnumMap<>(Channel.class);
        if(config.mode()==NotifierConfig.Mode.SIMULATED) {
            result.put(Channel.EMAIL,new SimulatedSender(Channel.EMAIL));
            result.put(Channel.SMS,new SimulatedSender(Channel.SMS));
        } else {
            result.put(Channel.EMAIL,new EmailSender(config));
            result.put(Channel.SMS,new SmsSender(config));
        }
        return result;
    }
}
