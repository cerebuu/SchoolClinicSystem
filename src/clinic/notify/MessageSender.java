package clinic.notify;

public interface MessageSender {
    SendResult send(Recipient to, String subject, String body);
}
