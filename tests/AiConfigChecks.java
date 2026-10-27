package clinic.ai;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.assistant.AiNurseAssistantPanel;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Offline synthetic configuration and Swing tests; never reads a real key. */
public final class AiConfigChecks {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("Configuration check " + checks); checks++; }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            ProcessBuilder child = new ProcessBuilder(java, "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"), AiConfigChecks.class.getName(), "isolated");
            child.environment().remove(AiConfig.KEY_VARIABLE);
            child.environment().remove("JAVA_TOOL_OPTIONS");
            child.environment().remove("JDK_JAVA_OPTIONS");
            child.environment().remove("_JAVA_OPTIONS");
            if (child.inheritIO().start().waitFor() != 0) throw new AssertionError("Isolated configuration checks failed");
            return;
        }
        Path root = Files.createTempDirectory("clinic-ai-config-check-");
        String originalDir = System.getProperty("user.dir");
        String originalKey = System.getProperty(AiConfig.KEY_VARIABLE);
        try {
            Path file = root.resolve("config.local.properties");
            check(AiConfig.lookup(() -> "  'environment-fixture'  ", () -> "property-fixture", () -> root).equals("environment-fixture"));
            check(AiConfig.lookup(() -> "\" \"", () -> " \"property-fixture\" ", () -> root).equals("property-fixture"));
            Files.writeString(file, "GEMINI_API_KEY= 'file-fixture' \n");
            check(AiConfig.lookup(() -> null, () -> "", () -> root).equals("file-fixture"));
            check(AiConfig.lookup(() -> "env-fixture", () -> { throw new AssertionError("Should not read property"); }, () -> root).equals("env-fixture"));
            Files.writeString(file, "GEMINI_API_KEY=your-key-here\n");
            check(AiConfig.lookup(() -> "", () -> null, () -> root).isEmpty());
            Files.writeString(file, "GEMINI_API_KEY=\\uBAD-synthetic\n");
            try { AiConfig.lookup(() -> "", () -> "", () -> root); throw new AssertionError(); }
            catch (AiServiceException expected) { check(!expected.getMessage().contains("BAD-synthetic")); }
            try { AiConfig.lookup(() -> { throw new SecurityException("hidden-fixture"); }, () -> "", () -> root); throw new AssertionError(); }
            catch (AiServiceException expected) { check(!expected.getMessage().contains("hidden-fixture")); }
            Files.delete(file);
            check(AiConfig.lookup(() -> null, () -> null, () -> root).isEmpty());
            // Isolate the public lookup from any real project configuration.
            Files.createDirectories(root.resolve("src/clinic"));
            System.setProperty("user.dir", root.toString());
            System.clearProperty(AiConfig.KEY_VARIABLE);
            if (System.getenv(AiConfig.KEY_VARIABLE) != null) throw new AssertionError("Run this test with GEMINI_API_KEY unset in the child process");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try (ApplicationContext app = new ApplicationContext(true)) {
                    Theme.install(); app.login.login("N001", "nurse123");
                    AiNurseAssistantPanel panel = new AiNurseAssistantPanel(app);
                    JButton send = (JButton) field(panel,"send");
                    JCheckBox privacy = (JCheckBox) field(panel,"privacy");
                    JTextArea status = (JTextArea) field(panel,"status");
                    JTextArea input = (JTextArea) field(panel,"input");
                    check(!send.isEnabled()); check(status.getText().contains("config.local.properties.example"));
                    check(status.getForeground().equals(Theme.DANGER));
                    privacy.doClick(); check(!send.isEnabled());
                    input.setText("How do I record a consultation?");
                    input.getActionMap().get("send-ai-message").actionPerformed(null);
                    check(!send.isEnabled()); check(field(panel,"request") == null);
                    for (String action : new String[]{"cancelRequest","clear"}) {
                        var method = panel.getClass().getDeclaredMethod(action); method.setAccessible(true); method.invoke(panel);
                        check(!send.isEnabled());
                    }
                    System.setProperty(AiConfig.KEY_VARIABLE, "'synthetic-test-key'");
                    AiNurseAssistantPanel configured = new AiNurseAssistantPanel(app);
                    JButton configuredSend = (JButton) field(configured,"send");
                    JCheckBox configuredPrivacy = (JCheckBox) field(configured,"privacy");
                    JTextArea configuredInput = (JTextArea) field(configured,"input");
                    check(!configuredSend.isEnabled());
                    configuredInput.setText("General navigation question");
                    configuredInput.getActionMap().get("send-ai-message").actionPerformed(null);
                    check(field(configured,"request") == null); // Enter cannot bypass consent.
                    configuredPrivacy.doClick(); check(configuredSend.isEnabled());
                    configuredPrivacy.doClick(); check(!configuredSend.isEnabled());
                    System.clearProperty(AiConfig.KEY_VARIABLE);
                    Files.writeString(file,"GEMINI_API_KEY=\\uBAD-synthetic\n");
                    AiNurseAssistantPanel malformed = new AiNurseAssistantPanel(app);
                    check(!((JButton)field(malformed,"send")).isEnabled());
                    check(((JTextArea)field(malformed,"status")).getForeground().equals(Theme.DANGER));
                } catch (Throwable error) { failure.set(error); }
            });
            if (failure.get()!=null) throw new AssertionError("Swing config checks failed",failure.get());
            System.out.println("PASS: " + checks + " lookup, malformed-config, Swing error, disabled-send, and consent checks; no network calls.");
        } finally {
            System.setProperty("user.dir", originalDir);
            if (originalKey==null) System.clearProperty(AiConfig.KEY_VARIABLE); else System.setProperty(AiConfig.KEY_VARIABLE,originalKey);
            try (var paths=Files.walk(root)) { for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
}
