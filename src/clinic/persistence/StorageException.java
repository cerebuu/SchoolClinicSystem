package clinic.persistence;

/** User-safe storage failure; SQL text and stored values are never displayed. */
public final class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
