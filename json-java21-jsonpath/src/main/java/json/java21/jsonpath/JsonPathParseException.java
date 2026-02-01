package json.java21.jsonpath;

/// Exception thrown when a JsonPath expression cannot be parsed.
/// This is a runtime exception as JsonPath parsing failures are typically programming errors.
public class JsonPathParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int position;
    private final String path;

    /// Creates a new parse exception with the given message.
    public JsonPathParseException(String message) {
        super(message);
        this.position = -1;
        this.path = null;
    }

    /// Creates a new parse exception with position information.
    public JsonPathParseException(String message, String path, int position) {
        super(formatMessage(message, path, position));
        this.position = position;
        this.path = path;
    }

    /// Creates a new parse exception with a cause.
    public JsonPathParseException(String message, Throwable cause) {
        super(message, cause);
        this.position = -1;
        this.path = null;
    }

    /// Returns the position in the path where the error occurred, or -1 if unknown.
    public int position() {
        return position;
    }

    /// Returns the path that was being parsed, or null if unknown.
    public String path() {
        return path;
    }

    private static String formatMessage(String message, String path, int position) {
        if (path == null || position < 0) {
            return message;
        }
        final var sb = new StringBuilder();
        sb.append(message);
        sb.append(" at position ").append(position);
        sb.append(" in path: ").append(path);
        if (position < path.length()) {
            sb.append(" (near '").append(path.charAt(position)).append("')");
        }
        return sb.toString();
    }
}
