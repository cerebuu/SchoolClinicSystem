package clinic.ai;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Supplier;

/** Local configuration only. Never log values or include them in errors. */
public final class AiConfig {
    public static final String KEY_VARIABLE = "GEMINI_API_KEY";
    public static final String MISSING_KEY_MESSAGE = "AI is not configured. Looked for GEMINI_API_KEY in the environment, "
        + "the -DGEMINI_API_KEY system property, and config.local.properties at the project root. "
        + "Set the environment variable or copy config.local.properties.example to config.local.properties "
        + "and fill in GEMINI_API_KEY, then restart the application.";
    private AiConfig() {}

    public static String getApiKey() throws AiServiceException {
        return lookup(() -> System.getenv(KEY_VARIABLE), () -> System.getProperty(KEY_VARIABLE), AiConfig::projectRoot);
    }

    static String lookup(Supplier<String> environment, Supplier<String> property, Supplier<Path> root) throws AiServiceException {
        try {
            String key = normalize(environment.get());
            if (!key.isEmpty()) return key;
            key = normalize(property.get());
            if (!key.isEmpty()) return key;
            Path file = root.get().resolve("config.local.properties");
            Properties settings = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                settings.load(reader);
            } catch (java.nio.file.NoSuchFileException missing) {
                return "";
            }
            key = normalize(settings.getProperty(KEY_VARIABLE));
            return key.equals("your-key-here") ? "" : key;
        } catch (IOException | RuntimeException failure) {
            // Parsing and permission failures must not escape onto Swing's EDT.
            throw new AiServiceException("AI configuration could not be read. Check access to the environment and system properties, "
                + "and check config.local.properties for valid properties syntax. Restart after correcting it.");
        }
    }

    private static String normalize(String value) {
        String result = value == null ? "" : value.trim();
        while (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'")))) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private static Path projectRoot() {
        // IDE launches normally use the project working directory. For launches
        // elsewhere, locate the root above compiled out/ or bin/ classes.
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path found = findRoot(cwd);
        if (found != null) return found;
        try {
            Path classes = Path.of(AiConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            found = findRoot(Files.isDirectory(classes) ? classes : classes.getParent());
            if (found != null) return found;
        } catch (Exception unavailable) { /* Fall back to the launch directory. */ }
        return cwd;
    }

    private static Path findRoot(Path start) {
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("src/clinic"))) return candidate;
        }
        return null;
    }
}
