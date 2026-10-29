package clinic.model;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
public class Alert implements Formattable {
    private final String type, message;
    private final LocalDateTime createdAt;
    private final String trackingKey, fingerprint;
    private final long generation;
    public Alert(String type, String message) {
        this(type,message,message);
    }
    public Alert(String type,String message,String identity) {
        this(type,message,hash(type+"\u0000"+identity),hash(message),0,LocalDateTime.now());
    }
    private Alert(String type,String message,String trackingKey,String fingerprint,long generation,
            LocalDateTime createdAt) {
        this.type=type;
        this.message=message;
        this.trackingKey=trackingKey;
        this.fingerprint=fingerprint;
        this.generation=generation;
        this.createdAt=createdAt;
    }
    public Alert withTrackingState(String key,long version) {
        return new Alert(type,message,key,fingerprint,version,createdAt);
    }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.",impossible);
        }
    }
    public String getType() {
        return type;
    }
    public String getMessage() {
        return message;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public String getTrackingKey() { return trackingKey; }
    public String getFingerprint() { return fingerprint; }
    public long getGeneration() { return generation; }
    public String toString() {
        return type+": "+message;
    }
    public String toReport() {
        return clinic.util.DateUtil.format(createdAt)+" | "+toString();
    }
}
