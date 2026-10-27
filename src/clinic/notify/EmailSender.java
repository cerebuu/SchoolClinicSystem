package clinic.notify;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

public final class EmailSender implements MessageSender {
    private static final int TIMEOUT_MS=15_000;
    private final NotifierConfig config;

    public EmailSender(NotifierConfig config) { this.config=config; }

    @Override public SendResult send(Recipient to,String subject,String body) {
        String host=config.value("email.smtp.host");
        String username=config.value("email.username");
        String password=config.value("email.appPassword");
        String from=config.value("email.from");
        int port;
        try { port=Integer.parseInt(config.value("email.smtp.port")); }
        catch(NumberFormatException invalid) { return SendResult.failure("LIVE email settings are incomplete."); }
        if(host.isBlank()||username.isBlank()||password.isBlank()||from.isBlank()||port<1||port>65535)
            return SendResult.failure("LIVE email settings are incomplete.");
        Properties properties=new Properties();
        properties.put("mail.smtp.host",host);
        properties.put("mail.smtp.port",Integer.toString(port));
        properties.put("mail.smtp.auth","true");
        properties.put("mail.smtp.starttls.enable","true");
        properties.put("mail.smtp.starttls.required","true");
        properties.put("mail.smtp.ssl.checkserveridentity","true");
        properties.put("mail.smtp.connectiontimeout",Integer.toString(TIMEOUT_MS));
        properties.put("mail.smtp.timeout",Integer.toString(TIMEOUT_MS));
        properties.put("mail.smtp.writetimeout",Integer.toString(TIMEOUT_MS));
        Session session=Session.getInstance(properties,new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username,password);
            }
        });
        try {
            MimeMessage message=new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO,new InternetAddress(to.value()));
            message.setSubject(subject,"UTF-8");
            message.setText(body,"UTF-8");
            Transport.send(message);
            return SendResult.success("Email sent.",null);
        } catch(MessagingException failure) {
            return SendResult.failure("Email could not be sent. Check the mail settings and retry.");
        }
    }
}
