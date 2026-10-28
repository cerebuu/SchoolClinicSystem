package clinic.ai;

import clinic.app.ApplicationContext;
import clinic.util.Json;
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
            rejectsCredential(service,client,"Please help with password=synthetic-password-42","synthetic-password-42");
            rejectsCredential(service,client,"API key: synthetic-api-key-42","synthetic-api-key-42");
            rejectsCredential(service,client,"secret='synthetic-secret-42'","synthetic-secret-42");
            rejectsCredential(service,client,"token: synthetic-token-42","synthetic-token-42");
            rejectsCredential(service,client,"authorization=synthetic-authorization-42","synthetic-authorization-42");
            rejectsCredential(service,client,"AWS_SECRET_ACCESS_KEY=synthetic-aws-secret-42","synthetic-aws-secret-42");
            rejectsCredential(service,client,"PRIVATE_KEY='synthetic-private-key-42'","synthetic-private-key-42");
            rejectsCredential(service,client,"GEMINI_API_KEY=synthetic-gemini-key-42","synthetic-gemini-key-42");
            rejectsCredential(service,client,"-----BEGIN PRIVATE KEY-----\nsynthetic-key-data",
                "-----BEGIN PRIVATE KEY-----");
            rejectsCredential(service,client,"Authorization: Bearer synthetic-bearer-token-42","synthetic-bearer-token-42");
            rejectsCredential(service,client,"Bearer synthetic-bearer-token-42","synthetic-bearer-token-42");
            String basicValue=java.util.Base64.getEncoder().encodeToString(
                "synthetic-user:synthetic-password".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            rejectsCredential(service,client,"Authorization: Basic "+basicValue,basicValue);
            rejectsCredential(service,client,"AIza1234567890123456789012345678901234","AIza1234567890123456789012345678901234");
            rejectsCredential(service,client,"sk-12345678901234567890123456789012","sk-12345678901234567890123456789012");
            check(service.ask("How do I reset my password?").equals("Hello ☺ nurse"));
            check(service.ask("What is an API key used for?").equals("Hello ☺ nurse"));
            check(service.ask("Explain bearer authentication in general terms.").equals("Hello ☺ nurse"));
            check(service.ask("What does an Authorization: Bearer header mean?").equals("Hello ☺ nurse"));
            check(service.ask("What does Basic authentication mean?").equals("Hello ☺ nurse"));
            check(service.ask("What does an Authorization: Basic header mean?").equals("Hello ☺ nurse"));
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
    private static void rejectsCredential(GeminiAiService service,FakeClient client,String question,String secret) throws Exception {
        int calls=client.calls;
        try { service.ask(question); throw new AssertionError("Credential-like question should be blocked."); }
        catch(AiServiceException error) {
            check(error.getMessage().contains("Remove passwords, API keys, secrets, tokens"));
            check(!error.getMessage().contains(secret));
        }
        check(client.calls==calls);
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
