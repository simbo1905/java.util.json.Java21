package json.java21.transforms;

/// Exception thrown when a JSON transform specification cannot be parsed.
/// This indicates a syntactically invalid or semantically incorrect transform definition.
public class JsonTransformsParseException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /// Creates a new parse exception with the given message.
    /// @param message the error message
    public JsonTransformsParseException(String message) {
        super(message);
    }

    /// Creates a new parse exception with the given message and cause.
    /// @param message the error message
    /// @param cause the underlying cause
    public JsonTransformsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
