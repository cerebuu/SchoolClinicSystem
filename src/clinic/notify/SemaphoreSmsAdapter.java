package clinic.notify;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SemaphoreSmsAdapter implements SmsProviderAdapter {
    private static final Pattern MESSAGE_ID=Pattern.compile("\"message_id\"\\s*:\\s*\"?([^\",}\\]]+)");
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
    @Override public String providerId(String response) {
        Matcher match=MESSAGE_ID.matcher(response);
        return match.find()?match.group(1).trim():null;
    }
    private static String encode(String value) {
        return URLEncoder.encode(value,StandardCharsets.UTF_8);
    }
}
