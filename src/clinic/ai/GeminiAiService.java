package clinic.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Minimal Gemini REST client. It deliberately has no clinic-system dependency. */
public final class GeminiAiService {
    public static final String KEY_VARIABLE = "GEMINI_API_KEY";
    private static final int MAX_PROMPT_CHARS = 3_500;
    // Gemini 2.0 Flash is shut down. This application's configured project exposes 2.5 Flash.
    private static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
    private static final String SYSTEM = "You are the SCMS AI Nurse Assistant, a support tool for authorized school-clinic nurses. "
        + "Do not claim to be a clinician or to access SCMS records, databases, or actions. "
        + "Do not diagnose, prescribe, or make autonomous clinical decisions. Give general, cautious information and recommend established protocols or a qualified clinician when appropriate. "
        + "Help with SCMS navigation, administration, and non-identifying documentation drafts. Clearly label any clinical wording as a draft for nurse review. Keep responses concise.";
    private final HttpClient client;
    private final String key;
    private final String model;

    public static GeminiAiService fromEnvironment() throws AiServiceException {
        String key = System.getenv(KEY_VARIABLE);
        if (key == null || key.isBlank())
            throw new AiServiceException("AI is not configured. Set the " + KEY_VARIABLE + " environment variable, then restart the application.");
        String configured = System.getenv("GEMINI_MODEL");
        String model = configured == null || configured.isBlank() ? DEFAULT_MODEL : configured.trim();
        if (!model.matches("[A-Za-z0-9._-]{1,100}"))
            throw new AiServiceException("The GEMINI_MODEL setting is invalid.");
        return new GeminiAiService(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), key.trim(), model);
    }

    GeminiAiService(HttpClient client, String key, String model) {
        this.client = Objects.requireNonNull(client);
        this.key = Objects.requireNonNull(key);
        this.model = Objects.requireNonNull(model);
    }

    public String ask(String question) throws AiServiceException {
        if (question == null || question.isBlank()) throw new AiServiceException("Enter a question for the assistant.");
        if (question.length() > MAX_PROMPT_CHARS) throw new AiServiceException("Keep the message below " + MAX_PROMPT_CHARS + " characters.");
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
            int status = response.statusCode();
            if (status == 401 || status == 403) throw new AiServiceException("The AI service rejected its configuration. Check the API key and its permissions.");
            if (status == 429) throw new AiServiceException("The AI service is rate-limited or out of quota. Please try again later.");
            if (status == 404) {
                String detail = errorMessage(response.body());
                throw new AiServiceException("Gemini returned 404 for model '" + model + "'. "
                    + (detail.isBlank()
                        ? "Confirm this model supports generateContent and is available to the configured API key."
                        : detail));
            }
            if (status < 200 || status >= 300) {
                String detail = errorMessage(response.body());
                throw new AiServiceException("The AI service returned HTTP " + status + "."
                    + (detail.isBlank() ? " Please try again later." : " " + detail));
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
        String text = jsonStringValue(json, "\"text\"");
        if (text == null) throw new AiServiceException("The AI service returned an unexpected response.");
        return text;
    }

    private String errorMessage(String json) {
        String message = jsonStringValue(json, "\"message\"");
        if (message == null) return "";
        String safe = key.isEmpty() ? message : message.replace(key, "[redacted]");
        return safe.replaceAll("(?i)(key[=: ]+)[^\\s,;]+", "$1[redacted]");
    }

    private static String jsonStringValue(String json, String field) {
        int marker = json.indexOf(field);
        if (marker < 0) return null;
        int colon = json.indexOf(':', marker);
        int start = colon < 0 ? -1 : json.indexOf('"', colon + 1);
        if (start < 0) return null;
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (!escaped && c == '"') return out.toString();
            if (!escaped && c == '\\') { escaped = true; continue; }
            if (escaped) {
                if (c == 'u' && i + 4 < json.length()) {
                    try { out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); i += 4; }
                    catch (NumberFormatException invalid) { out.append('u'); }
                } else switch (c) { case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t'); case '"' -> out.append('"'); case '\\' -> out.append('\\'); default -> out.append(c); }
                escaped = false;
            } else out.append(c);
        }
        return null;
    }
    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    private static boolean looksIdentifying(String value) {
        String text = value.toLowerCase(java.util.Locale.ROOT);
        return text.matches("(?s).*\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b.*")
            || text.matches("(?s).*\\b(?:\\+?\\d[ .()-]*){7,}\\d\\b.*")
            || text.matches("(?s).*\\b(?:student|patient|medical)\\s*(?:id|number|no\\.?)\\s*[:#-]?\\s*\\S+.*");
    }
}
