package clinic.notify;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/** Verifies that successful HTTP status alone cannot mark an SMS delivered. */
public final class SmsSenderChecks {
    private static int checks;

    public static void main(String[] args) {
        SemaphoreSmsAdapter adapter=new SemaphoreSmsAdapter();
        SmsProviderAdapter.Receipt queued=adapter.receipt("[{\"message_id\":12345,\"status\":\"Queued\"}]");
        check(queued.messageId().equals("12345")&&queued.status().equals("Queued"),
            "queued provider receipt is accepted with its provider ID");
        SmsProviderAdapter.Receipt sent=adapter.receipt("[{\"status\":\"Sent\",\"message_id\":\"12345\"}]");
        check(sent.messageId().equals("12345")&&sent.status().equals("Sent"),
            "sent provider receipt is accepted independent of field order");
        rejects("[{\"message_id\":12345,\"status\":\"Failed\"}]",
            "provider failure status is not reported as accepted");
        rejects("[]","empty provider response is not reported as accepted");
        rejects("not json [{\"message_id\":12345,\"status\":\"Queued\"}]",
            "malformed provider response is rejected");
        rejects("[{\"message_id\":12345,\"status\":\"Queued\",\"note\":\"bad\\x\"}]",
            "invalid JSON string escapes are rejected");
        rejects("[{\"message_id\":12345,\"message_id\":67890,\"status\":\"Queued\"}]",
            "duplicate receipt identifiers are rejected");
        rejects("[{\"message_id\":12345,\"status\":\"Queued\",\"status\":\"Failed\"}]",
            "duplicate receipt statuses are rejected");
        rejects("[{\"message_id\":null,\"status\":\"Queued\"}]",
            "receipt without a message ID is rejected");
        rejects("[{\"message_id\":12345,\"status\":\"Unknown\"}]",
            "unknown provider status is rejected");

        SendResult accepted=sender(200,"[{\"message_id\":12345,\"status\":\"Queued\"}]")
            .send(new Recipient("+639123456789"),"Clinic notice","Message");
        check(accepted.success()&&accepted.providerMessageId().equals("12345")
                &&accepted.statusMessage().contains("accepted by the provider"),
            "SMS sender reports the provider receipt rather than claiming delivery");
        SendResult rejected=sender(200,"[{\"message_id\":12345,\"status\":\"Failed\"}]")
            .send(new Recipient("+639123456789"),"Clinic notice","Message");
        check(!rejected.success(),"HTTP 2xx with provider failure is not marked successful");
        SendResult invalid=sender(200,"[]").send(new Recipient("+639123456789"),"Clinic notice","Message");
        check(!invalid.success(),"HTTP 2xx with empty response is not marked successful");
        SendResult badHttp=sender(429,"[]").send(new Recipient("+639123456789"),"Clinic notice","Message");
        check(!badHttp.success(),"HTTP failure is not marked successful");
        System.out.println("PASS: "+checks+" SMS provider response checks.");
    }

    private static SmsSender sender(int status,String body) {
        Properties values=new Properties();
        values.setProperty("mode","LIVE");
        values.setProperty("sms.endpoint","https://api.semaphore.co/api/v4/messages");
        values.setProperty("sms.apiKey","test-key");
        values.setProperty("sms.senderName","Clinic");
        return new SmsSender(NotifierConfig.fromProperties(values),new SemaphoreSmsAdapter(),
            new StubHttpClient(status,body));
    }

    private static void rejects(String response,String message) {
        try { new SemaphoreSmsAdapter().receipt(response); }
        catch(IllegalArgumentException expected) { checks++;return; }
        throw new AssertionError(message);
    }

    private static void check(boolean passed,String message) {
        if(!passed)throw new AssertionError(message);
        checks++;
    }

    private static final class StubHttpClient extends HttpClient {
        private final int status;
        private final String body;
        private StubHttpClient(int status,String body) { this.status=status;this.body=body; }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() {
            try { return SSLContext.getDefault(); }
            catch(java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
        }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
        @Override @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request,BodyHandler<T> handler) {
            return new StubResponse<>(status,(T)body,request);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,BodyHandler<T> handler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Async request not expected."));
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                BodyHandler<T> handler,PushPromiseHandler<T> pushHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Async request not expected."));
        }
    }

    private record StubResponse<T>(int statusCode,T body,HttpRequest request) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(),(key,value)->true); }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public Version version() { return Version.HTTP_1_1; }
    }
}
