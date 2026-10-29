package clinic.ai;

import clinic.util.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Minimal Gemini REST client. It deliberately has no clinic-system dependency. */
public final class GeminiAiService {
    public static final String KEY_VARIABLE = AiConfig.KEY_VARIABLE;
    private static final int MAX_PROMPT_CHARS = 3_500;
    private static final java.util.regex.Pattern CREDENTIAL_ASSIGNMENT = java.util.regex.Pattern.compile(
        "(?i)\\b(?:(?:[a-z][a-z0-9]*[_-])+)?(?:password|passwd|passcode|api[\\s_-]*key|"
            + "api[\\s_-]*secret|client[\\s_-]*secret|aws[\\s_-]*secret[\\s_-]*access[\\s_-]*key|"
            + "secret[\\s_-]*access[\\s_-]*key|private[\\s_-]*key|secret|access[\\s_-]*token|"
            + "refresh[\\s_-]*token|token)\\s*[:=]\\s*"
            + "(?:\"[^\"\\r\\n]{1,512}\"|'[^'\\r\\n]{1,512}'|[^\\s,;]{1,512})");
    private static final java.util.regex.Pattern AUTHORIZATION_ASSIGNMENT = java.util.regex.Pattern.compile(
        "(?i)\\bauthorization\\s*[:=]\\s*(?!(?:bearer|basic)\\b)"
            + "(?:\"[^\"\\r\\n]{1,512}\"|'[^'\\r\\n]{1,512}'|[^\\s,;]{1,512})");
    private static final java.util.regex.Pattern BEARER_TOKEN = java.util.regex.Pattern.compile(
        "(?i)\\bbearer\\s+(?:(?=[a-z0-9._~+/-]{8,})(?=[a-z0-9._~+/-]*[0-9._~+/-])"
            + "[a-z0-9._~+/-]{8,}={0,2}|[a-z]{32,})");
    private static final java.util.regex.Pattern BASIC_AUTHORIZATION = java.util.regex.Pattern.compile(
        "(?i)\\bauthorization\\s*[:=]\\s*basic\\s+([a-z0-9+/]{4,}={0,2})");
    private static final java.util.regex.Pattern GOOGLE_API_KEY = java.util.regex.Pattern.compile(
        "\\bAIza[0-9A-Za-z_-]{20,}\\b");
    private static final java.util.regex.Pattern GENERIC_SECRET_KEY = java.util.regex.Pattern.compile(
        "\\b(?:sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16})\\b");
    private static final java.util.regex.Pattern PRIVATE_KEY_HEADER = java.util.regex.Pattern.compile(
        "(?i)-----BEGIN (?:(?:RSA|EC|OPENSSH) )?PRIVATE KEY-----");
    // Override with a text model supported by your provider project.
    private static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
    private static final String SYSTEM = "You are the SCMS AI Nurse Assistant, a support tool for authorized school-clinic nurses. "
        + "Do not claim to be a clinician or to access SCMS records, databases, or actions. "
        + "Do not diagnose, prescribe, or make autonomous clinical decisions. Give general, cautious information and recommend established protocols or a qualified clinician when appropriate. "
        + "Help with SCMS navigation, administration, and non-identifying documentation drafts. Clearly label any clinical wording as a draft for nurse review. Keep responses concise.";
    private final HttpClient client;
    private final String key;
    private final String model;
    private final Runnable sessionGuard;

    public static GeminiAiService fromEnvironment(clinic.app.ApplicationContext app) throws AiServiceException {
        Runnable guard = app.nurseSessionGuard();
        String key = AiConfig.getApiKey();
        if (key == null || key.isBlank())
            throw new AiServiceException(AiConfig.MISSING_KEY_MESSAGE);
        String configured = System.getenv("GEMINI_MODEL");
        String model = configured == null || configured.isBlank() ? DEFAULT_MODEL : configured.trim();
        if (!model.matches("[A-Za-z0-9._-]{1,100}"))
            throw new AiServiceException("The GEMINI_MODEL setting is invalid.");
        return new GeminiAiService(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), key.trim(), model, guard);
    }

    GeminiAiService(HttpClient client, String key, String model, Runnable sessionGuard) {
        this.client = Objects.requireNonNull(client);
        this.key = Objects.requireNonNull(key);
        this.model = Objects.requireNonNull(model);
        this.sessionGuard = Objects.requireNonNull(sessionGuard);
    }

    public String ask(String question) throws AiServiceException {
        sessionGuard.run();
        if (question == null || question.isBlank()) throw new AiServiceException("Enter a question for the assistant.");
        if (question.length() > MAX_PROMPT_CHARS) throw new AiServiceException("Keep the message below " + MAX_PROMPT_CHARS + " characters.");
        if (looksCredentialLike(question))
            throw new AiServiceException("Remove passwords, API keys, secrets, tokens, and authorization credentials before sending.");
        if (looksIdentifying(question)) throw new AiServiceException("Remove contact details or a labeled student/patient ID before sending this message.");
        String body = "{\"systemInstruction\":{\"parts\":[{\"text\":\"" + json(SYSTEM) + "\"}]},"
            + "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"" + json(question) + "\"}]}],"
            + "\"generationConfig\":{\"maxOutputTokens\":1024}}";
        URI endpoint = URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(35))
            .header("Content-Type", "application/json").header("x-goog-api-key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            sessionGuard.run();
            int status = response.statusCode();
            if (status == 401 || status == 403 || status == 400)
                throw new AiServiceException(configurationFailure(status, response.body()));
            if (status == 429) throw new AiServiceException("The AI service is rate-limited or out of quota. Please try again later.");
            if (status == 404) {
                throw new AiServiceException("Gemini returned 404 for model '" + model + "'. "
                    + "Choose a model supporting generateContent in GEMINI_MODEL and restart. No automatic retry was made.");
            }
            if (status < 200 || status >= 300) {
                throw new AiServiceException("The AI service returned HTTP " + status + "."
                    + " Check configuration or try again later.");
            }
            String answer = responseText(response.body());
            if (answer.isBlank()) throw new AiServiceException("The AI service returned no usable response. Please rephrase and try again.");
            return answer;
        } catch (AiServiceException failure) { throw failure;
        } catch (java.net.http.HttpTimeoutException failure) { throw new AiServiceException("The AI request timed out. Please try again.", failure);
        } catch (java.io.IOException failure) { throw new AiServiceException("Unable to reach the AI service. Check your network and try again.", failure);
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AiServiceException("The AI request was cancelled.", failure); }
    }

    static String responseText(String json) throws AiServiceException {
        try {
            if (!(Json.parse(json) instanceof java.util.Map<?,?> root)) throw new IllegalArgumentException();
            if (root.get("promptFeedback") instanceof java.util.Map<?,?> feedback && feedback.containsKey("blockReason"))
                throw new AiServiceException("The provider blocked this request. Rephrase using general, non-identifying language.");
            if (!(root.get("candidates") instanceof java.util.List<?> candidates) || candidates.isEmpty()
                    || !(candidates.get(0) instanceof java.util.Map<?,?> candidate)) throw new IllegalArgumentException();
            String finish = String.valueOf(candidate.get("finishReason"));
            if (!finish.equals("STOP")) throw new AiServiceException(finish.equals("MAX_TOKENS")
                ? "The response exceeded its output limit. Ask a shorter question."
                : "The provider did not return a complete answer. Rephrase your question.");
            if (!(candidate.get("content") instanceof java.util.Map<?,?> content)
                    || !(content.get("parts") instanceof java.util.List<?> parts)) throw new IllegalArgumentException();
            StringBuilder answer = new StringBuilder();
            for (Object part : parts) if (part instanceof java.util.Map<?,?> item
                    && !Boolean.TRUE.equals(item.get("thought")) && item.get("text") instanceof String text) answer.append(text);
            if(answer.isEmpty() || answer.length()>16000) throw new IllegalArgumentException();
            return answer.toString();
        } catch (IllegalArgumentException ex) {
            throw new AiServiceException("The provider returned an invalid or oversized response. Try again later.");
        }
    }

    /** Convert only documented provider codes to UI text; never surface the raw error body. */
    static String configurationFailure(int httpStatus, String body) {
        String reason = providerErrorReason(body);
        if (reason.equals("API_KEY_INVALID") || reason.equals("UNAUTHENTICATED"))
            return "Gemini rejected GEMINI_API_KEY. Check the environment first, then -DGEMINI_API_KEY, then config.local.properties; replace the active source with a valid Google AI Studio key and restart.";
        if (reason.equals("API_KEY_SERVICE_BLOCKED"))
            return "Gemini has blocked this API key. Create an active replacement key in Google AI Studio.";
        if (reason.equals("API_KEY_IP_ADDRESS_BLOCKED") || reason.equals("API_KEY_HTTP_REFERRER_BLOCKED")
                || reason.equals("API_KEY_ANDROID_APP_BLOCKED") || reason.equals("API_KEY_IOS_APP_BLOCKED"))
            return "The API key's application restriction blocks this desktop app. Update the key restriction in Google AI Studio.";
        if (reason.equals("API_KEY_API_TARGET_BLOCKED"))
            return "The API key is not allowed to call Gemini. Restrict it to the Generative Language API in Google AI Studio.";
        if (reason.equals("SERVICE_DISABLED") || reason.equals("SERVICE_NOT_ACTIVATED"))
            return "The Generative Language API is disabled for this project. Enable it in Google AI Studio or Google Cloud.";
        if (reason.equals("BILLING_DISABLED"))
            return "Gemini project access is blocked by billing. Check the project's billing status in Google AI Studio.";
        if (httpStatus == 401)
            return "Gemini rejected the API key (HTTP 401). Check that GEMINI_API_KEY is active and belongs to a Gemini-enabled project.";
        if (httpStatus == 403)
            return "Gemini denied access to this key or project (HTTP 403). In Google AI Studio, check key status and restrictions, project access, and billing. Unrestricted standard keys are rejected; use an authorization key or restrict the key to Gemini API.";
        return "Gemini rejected the request (HTTP " + httpStatus + "). Check the API key and project configuration.";
    }

    private static String providerErrorReason(String body) {
        try {
            if (!(Json.parse(body) instanceof java.util.Map<?,?> root)
                    || !(root.get("error") instanceof java.util.Map<?,?> error)) return "";
            if (error.get("details") instanceof java.util.List<?> details) {
                for (Object detail : details) {
                    if (detail instanceof java.util.Map<?,?> item && item.get("reason") instanceof String value
                            && isKnownProviderReason(value)) return value;
                }
            }
            if (error.get("status") instanceof String value && isKnownProviderReason(value)) return value;
            // Check only for a few known conditions; never return or display provider prose.
            if (error.get("message") instanceof String message) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("reported as leaked") || lower.contains("key has been blocked"))
                    return "API_KEY_SERVICE_BLOCKED";
                if (lower.contains("unrestricted standard key") || lower.contains("unrestricted api key"))
                    return "API_KEY_UNRESTRICTED";
            }
        } catch (RuntimeException ignored) {
            // Provider error bodies are untrusted and diagnostics must fail closed.
        }
        return "";
    }

    private static boolean isKnownProviderReason(String value) {
        return switch (value) {
            case "API_KEY_INVALID", "API_KEY_SERVICE_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED",
                    "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_ANDROID_APP_BLOCKED", "API_KEY_IOS_APP_BLOCKED",
                    "API_KEY_API_TARGET_BLOCKED", "SERVICE_DISABLED", "SERVICE_NOT_ACTIVATED",
                    "BILLING_DISABLED", "UNAUTHENTICATED", "PERMISSION_DENIED" -> true;
            default -> false;
        };
    }

    private static String json(String value) {
        String quoted = Json.quote(value);
        return quoted.substring(1, quoted.length()-1);
    }
    private static boolean looksIdentifying(String value) {
        String text = value.toLowerCase(java.util.Locale.ROOT);
        return text.matches("(?s).*\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b.*")
            || text.matches("(?s).*\\b(?:\\+?\\d[ .()-]*){7,}\\d\\b.*")
            || text.matches("(?s).*\\b(?:student|patient|medical)\\s*(?:id|number|no\\.?)\\s*[:#-]?\\s*\\S+.*");
    }
    private static boolean looksCredentialLike(String value) {
        return CREDENTIAL_ASSIGNMENT.matcher(value).find()
            || AUTHORIZATION_ASSIGNMENT.matcher(value).find()
            || BEARER_TOKEN.matcher(value).find()
            || hasBasicCredentials(value)
            || GOOGLE_API_KEY.matcher(value).find()
            || GENERIC_SECRET_KEY.matcher(value).find()
            || PRIVATE_KEY_HEADER.matcher(value).find();
    }
    private static boolean hasBasicCredentials(String value) {
        var matcher=BASIC_AUTHORIZATION.matcher(value);
        while(matcher.find()) {
            byte[] decoded=null;
            try {
                decoded=java.util.Base64.getDecoder().decode(matcher.group(1));
                boolean hasSeparator=false;
                for(byte character:decoded)if(character==':'){hasSeparator=true;break;}
                if(hasSeparator)return true;
            } catch(IllegalArgumentException invalidBase64) { /* Not a credential-shaped Basic value. */ }
            finally { if(decoded!=null)java.util.Arrays.fill(decoded,(byte)0); }
        }
        return false;
    }
}
