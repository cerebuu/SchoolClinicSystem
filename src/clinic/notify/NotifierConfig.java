package clinic.notify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class NotifierConfig {
    public enum Mode { SIMULATED, LIVE }
    private final Mode mode;
    private final Properties values;
    private final String loadError;

    private NotifierConfig(Mode mode,Properties values,String loadError) {
        this.mode=mode;this.values=values;this.loadError=loadError;
    }

    public static NotifierConfig load() {
        Path path=Path.of(System.getProperty("clinic.notifier.config","notifier.properties"));
        Properties values=new Properties();
        if(Files.exists(path)) {
            try(InputStream input=Files.newInputStream(path)) { values.load(input); }
            catch(IOException failure) {
                return new NotifierConfig(Mode.SIMULATED,values,
                    "Notifier configuration could not be read; simulated mode is active.");
            }
        }
        return fromProperties(values);
    }

    public static NotifierConfig fromProperties(Properties properties) {
        Properties values=new Properties();
        values.putAll(java.util.Objects.requireNonNull(properties));
        try {
            String selected=values.getProperty("mode","SIMULATED").trim().toUpperCase(java.util.Locale.ROOT);
            return new NotifierConfig(Mode.valueOf(selected),values,null);
        } catch(IllegalArgumentException invalidMode) {
            return new NotifierConfig(Mode.SIMULATED,values,
                "Notifier mode must be SIMULATED or LIVE; simulated mode is active.");
        }
    }

    public static NotifierConfig simulated() {
        Properties values=new Properties();
        values.setProperty("mode","SIMULATED");
        return fromProperties(values);
    }

    public Mode mode() { return mode; }
    public String value(String key) { return values.getProperty(key,"").trim(); }
    public String loadError() { return loadError; }
}
