package clinic.notify;

import clinic.util.Json;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class SemaphoreSmsAdapter implements SmsProviderAdapter {
    @Override public URI endpoint(String value) {
        URI uri=URI.create(value);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)
            throw new IllegalArgumentException("The SMS endpoint must use HTTPS.");
        return uri;
    }
    @Override public String requestBody(String key,String sender,String phone,String text) {
        return "apikey="+encode(key)+"&number="+encode(phone)+"&message="+encode(text)+
            "&sendername="+encode(sender);
    }
    @Override public Receipt receipt(String response) {
        if(response==null||response.length()>65_536)
            throw new IllegalArgumentException("Invalid SMS provider response.");
        Object parsed;
        try { parsed=Json.parse(response); }
        catch(IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Invalid SMS provider response.");
        }
        if(!(parsed instanceof List<?> receipts)||receipts.size()!=1
                ||!(receipts.get(0) instanceof Map<?,?> fields))
            throw new IllegalArgumentException("Invalid SMS provider response.");
        String messageId=messageId(fields.get("message_id"));
        if(messageId==null||!(fields.get("status") instanceof String rawStatus))
            throw new IllegalArgumentException("Invalid SMS provider response.");
        String delivery=rawStatus.trim();
        if(!delivery.equalsIgnoreCase("Queued")&&!delivery.equalsIgnoreCase("Pending")
                &&!delivery.equalsIgnoreCase("Sent"))
            throw new IllegalArgumentException("SMS provider did not accept the message.");
        return new Receipt(messageId,delivery);
    }
    private static String messageId(Object value) {
        if(value instanceof String text)return text.matches("[0-9]+")?text:null;
        if(value instanceof BigDecimal number) {
            try {
                java.math.BigInteger integer=number.toBigIntegerExact();
                return integer.signum()>0?integer.toString():null;
            } catch(ArithmeticException invalidId) { return null; }
        }
        return null;
    }
    private static String encode(String value) {
        return URLEncoder.encode(value,StandardCharsets.UTF_8);
    }
}
