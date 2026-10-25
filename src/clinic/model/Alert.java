package clinic.model;
import java.time.LocalDateTime;
public class Alert implements Formattable {
    private final String type, message;
    private final LocalDateTime createdAt=LocalDateTime.now();
    public Alert(String type, String message) {
        this.type=type;
        this.message=message;
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
    public String toString() {
        return type+": "+message;
    }
    public String toReport() {
        return clinic.util.DateUtil.format(createdAt)+" | "+toString();
    }
}
