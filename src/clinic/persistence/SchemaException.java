package clinic.persistence;

/** Safe, actionable schema diagnostics; never includes a connection URL or credentials. */
final class SchemaException extends java.sql.SQLException {
    SchemaException(String message) { super(message); }
}
