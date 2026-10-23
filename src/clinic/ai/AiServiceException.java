package clinic.ai;

/** A safe, user-facing failure from the cloud assistant boundary. */
public final class AiServiceException extends Exception {
    public AiServiceException(String message) { super(message); }
    public AiServiceException(String message, Throwable cause) { super(message, cause); }
}
