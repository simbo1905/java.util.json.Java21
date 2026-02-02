package json.java21.transforms;

/// Exception thrown for invalid transform syntax or runtime failures applying a transform.
public final class JsonTransformException extends RuntimeException {
    public JsonTransformException(String message) {
        super(message);
    }

    public JsonTransformException(String message, Throwable cause) {
        super(message, cause);
    }
}

