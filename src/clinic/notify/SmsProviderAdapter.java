package clinic.notify;

import java.net.URI;

interface SmsProviderAdapter {
    URI endpoint(String configuredEndpoint);
    String requestBody(String apiKey,String senderName,String phone,String body);
    String providerId(String responseBody);
}
