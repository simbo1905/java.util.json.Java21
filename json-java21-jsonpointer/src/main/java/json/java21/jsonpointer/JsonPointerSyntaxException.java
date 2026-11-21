package json.java21.jsonpointer;

import java.io.Serial;

/// Exception thrown when a JSON Pointer string cannot be parsed.
///
/// A non-empty JSON Pointer must start with `/`. Escape sequences must be
/// well-formed: `~` must be followed by `0` or `1`; no other tilde sequences
/// are permitted (RFC 6901 §3).
///
/// This is an unchecked exception because pointer syntax errors are typically
/// programming errors discovered at development time.
public final class JsonPointerSyntaxException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String pointer;

    /// Creates a new syntax exception with the given message and the pointer string
    /// that was being parsed.
    ///
    /// @param message human-readable description of the syntax error
    /// @param pointer the invalid pointer string
    public JsonPointerSyntaxException(String message, String pointer) {
        super(formatMessage(message, pointer));
        this.pointer = pointer;
    }

    /// Returns the pointer string that failed to parse.
    ///
    /// @return the invalid pointer string
    public String pointer() {
        return pointer;
    }

    private static String formatMessage(String message, String pointer) {
        return message + ": \"" + pointer + "\"";
    }
}
