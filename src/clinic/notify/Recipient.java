package clinic.notify;

public record Recipient(String value) {
    public Recipient {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A recipient address or phone number is required.");
        }
    }
}
