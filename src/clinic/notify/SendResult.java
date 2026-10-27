package clinic.notify;

public record SendResult(boolean success, String statusMessage, String providerMessageId) {
    public SendResult {
        if (statusMessage == null || statusMessage.isBlank()) {
            throw new IllegalArgumentException("A send status message is required.");
        }
    }

    public static SendResult success(String message, String providerId) {
        return new SendResult(true, message, providerId);
    }

    public static SendResult failure(String message) {
        return new SendResult(false, message, null);
    }
}
