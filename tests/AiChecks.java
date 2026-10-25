package clinic.ai;

import clinic.app.ApplicationContext;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Deterministic provider fixtures: no live network, key, or health data. */
public final class AiChecks {
    private static int checks;
    private static void check(boolean ok) { if(!ok)throw new AssertionError("AI check " + checks); checks++; }
    private interface Work { void run() throws Exception; }
    private static void rejects(Class<? extends Throwable> type, Work work) throws Exception {
        try { work.run(); } catch(Throwable failure) { if(!type.isInstance(failure))throw new AssertionError(failure);checks++;return; }
        throw new AssertionError("Expected " + type);
    }
    private static final String OK = "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private reasoning\"},{\"text\":\"Hello \\u263a\"},{\"text\":\" nurse\"}]}}]}";
    public static void main(String[] args) throws Exception {
        check(GeminiAiService.responseText(OK).equals("Hello ☺ nurse"));
        rejects(AiServiceException.class,()->GeminiAiService.responseText("{\"text\":\"unrelated error\"}"));
        rejects(AiServiceException.class,()->GeminiAiService.responseText(OK+" garbage"));
        rejects(AiServiceException.class,()->GeminiAiService.responseText(OK.replace("STOP","MAX_TOKENS")));
        rejects(AiServiceException.class,()->GeminiAiService.responseText("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
        rejects(AiServiceException.class,()->GeminiAiService.responseText("[".repeat(40)));
        String controls="quote\" slash\\\n\t\b\f\r\u0001";
        check(Json.parse(Json.quote(controls)).equals(controls));
        rejects(IllegalArgumentException.class,()->Json.parse("{\"x\":1,\"x\":2}"));
        rejects(IllegalArgumentException.class,()->Json.parse("\"\\q\""));
        try(ApplicationContext app=new ApplicationContext(true)) {
            app.login.login("AD001","admin123");
            rejects(SecurityException.class,app::nurseSessionGuard);
            app.login.login("N001","nurse123");
            Runnable guard=app.nurseSessionGuard();
            FakeClient client=new FakeClient();
            GeminiAiService service=new GeminiAiService(client,"fixture-secret","fixture-model",guard);
            check(service.ask("General question").equals("Hello ☺ nurse"));
            check(client.last.uri().getScheme().equals("https") && client.last.uri().getQuery()==null);
            check(client.last.headers().firstValue("x-goog-api-key").orElseThrow().equals("fixture-secret"));
            int before=client.calls;
            rejects(AiServiceException.class,()->service.ask(" "));
            rejects(AiServiceException.class,()->service.ask("x".repeat(3501)));
            rejects(AiServiceException.class,()->service.ask("contact@example.org"));
            check(client.calls==before);
            for(int code:List.of(400,401,403,404,429,500)) {
                client.status=code;client.body="{\"error\":{\"message\":\"fixture-secret private patient text\"}}";
                try { service.ask("General question");throw new AssertionError(); }
                catch(AiServiceException error) {check(!error.getMessage().contains("fixture-secret")&&!error.getMessage().contains("private patient"));}
            }
            client.status=200;client.body=OK;
            client.failure=new HttpTimeoutException("fixture-secret");
            rejects(AiServiceException.class,()->service.ask("General question"));client.failure=null;
            app.login.logout();before=client.calls;
            rejects(SecurityException.class,()->service.ask("General question"));check(client.calls==before);
            app.login.login("N001","nurse123");
            rejects(SecurityException.class,()->service.ask("General question"));
            GeminiAiService newSession=new GeminiAiService(client,"fixture-secret","fixture-model",app.nurseSessionGuard());
            client.duringSend=app.login::logout;
            rejects(SecurityException.class,()->newSession.ask("General question"));
        }
        System.out.println("PASS: "+checks+" AI parsing, simulated HTTP, privacy-error, and session checks (no live provider call).");
    }
    private static final class FakeClient extends HttpClient {
        int status=200,calls; String body=OK; HttpRequest last; java.io.IOException failure; Runnable duringSend=()->{};
        public Optional<java.net.CookieHandler> cookieHandler(){return Optional.empty();}
        public Optional<Duration> connectTimeout(){return Optional.of(Duration.ofSeconds(10));}
        public Redirect followRedirects(){return Redirect.NEVER;}
        public Optional<ProxySelector> proxy(){return Optional.empty();}
        public SSLContext sslContext(){try{return SSLContext.getDefault();}catch(Exception e){throw new IllegalStateException(e);}}
        public SSLParameters sslParameters(){return new SSLParameters();}
        public Optional<Authenticator> authenticator(){return Optional.empty();}
        public Version version(){return Version.HTTP_2;}
        public Optional<Executor> executor(){return Optional.empty();}
        @SuppressWarnings("unchecked") public <T> HttpResponse<T> send(HttpRequest req,HttpResponse.BodyHandler<T> handler)throws java.io.IOException {
            calls++;last=req;if(failure!=null)throw failure;duringSend.run();
            return new HttpResponse<T>() {
                public int statusCode(){return status;}
                public HttpRequest request(){return req;}
                public Optional<HttpResponse<T>> previousResponse(){return Optional.empty();}
                public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}
                public T body(){return (T)body;}
                public Optional<SSLSession> sslSession(){return Optional.empty();}
                public URI uri(){return req.uri();}
                public Version version(){return Version.HTTP_2;}
            };
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){throw new UnsupportedOperationException();}
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){throw new UnsupportedOperationException();}
    }
}
