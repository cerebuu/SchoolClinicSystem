package clinic.notify;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;

public final class SmsSender implements MessageSender {
    private final NotifierConfig config;
    private final SmsProviderAdapter provider;
    private final HttpClient client;

    public SmsSender(NotifierConfig config) {
        this(config,new SemaphoreSmsAdapter(),HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build());
    }

    SmsSender(NotifierConfig config,SmsProviderAdapter provider,HttpClient client) {
        this.config=config;this.provider=provider;this.client=client;
    }

    @Override public SendResult send(Recipient to,String subject,String body) {
        String endpoint=config.value("sms.endpoint");
        String key=config.value("sms.apiKey");
        String sender=config.value("sms.senderName");
        if(endpoint.isBlank()||key.isBlank()||sender.isBlank())
            return SendResult.failure("LIVE SMS settings are incomplete.");
        try {
            URI uri=provider.endpoint(endpoint);
            HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(provider.requestBody(key,sender,to.value(),body)))
                .build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)
                return SendResult.failure("SMS provider rejected the request. Check settings and retry.");
            SmsProviderAdapter.Receipt receipt=provider.receipt(response.body());
            return SendResult.success("SMS accepted by the provider.",receipt.messageId());
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return SendResult.failure("SMS request was interrupted.");
        } catch(IOException|IllegalArgumentException failure) {
            return SendResult.failure("SMS could not be sent. Check the provider settings and retry.");
        }
    }
}
