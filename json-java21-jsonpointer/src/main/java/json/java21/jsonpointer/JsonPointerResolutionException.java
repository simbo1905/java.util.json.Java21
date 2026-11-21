package json.java21.jsonpointer;

import java.io.Serial;

/// Exception thrown when a JSON Pointer cannot be resolved against a document.
///
/// Resolution failures are distinct from parse failures. A pointer may be
/// syntactically valid but still fail to resolve because:
///
/// - A referenced object member does not exist.
/// - An array index is out of bounds.
/// - An array token is not a valid non-negative integer.
/// - A step traverses a primitive value (string, number, boolean, null) that
///   has no children.
///
/// @see JsonPointerSyntaxException
public final class JsonPointerResolutionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String pointer;
    private final String failingToken;

    /// Creates a new resolution exception.
    ///
    /// @param message      human-readable description of the resolution failure
    /// @param pointer      the pointer that failed to resolve
    /// @param failingToken the reference token that could not be applied, or
    ///                     `null` if the whole pointer is the context
    public JsonPointerResolutionException(String message, String pointer, String failingToken) {
        super(formatMessage(message, pointer, failingToken));
        this.pointer = pointer;
        this.failingToken = failingToken;
    }

    /// Returns the pointer that failed to resolve.
    ///
    /// @return the pointer string
    public String pointer() {
        return pointer;
    }

    /// Returns the reference token that could not be applied, or `null` when not
    /// applicable.
    ///
    /// @return the failing token, or `null`
    public String failingToken() {
        return failingToken;
    }

    private static String formatMessage(String message, String pointer, String failingToken) {
        if (failingToken == null) {
            return message + ": pointer=\"" + pointer + "\"";
        }
        return message + ": pointer=\"" + pointer + "\", token=\"" + failingToken + "\"";
    }
}
