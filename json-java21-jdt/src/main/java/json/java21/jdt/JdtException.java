package json.java21.jdt;

/// Exception thrown when a JDT transform specification is invalid or cannot be applied.
@SuppressWarnings("serial")
public final class JdtException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /// Creates a new JdtException with the given message.
    /// @param message the error message
    public JdtException(String message) {
        super(message);
    }

    /// Creates a new JdtException with the given message and cause.
    /// @param message the error message
    /// @param cause the underlying cause
    public JdtException(String message, Throwable cause) {
        super(message, cause);
    }
}
