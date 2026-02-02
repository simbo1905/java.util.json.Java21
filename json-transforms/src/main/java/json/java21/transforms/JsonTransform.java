package json.java21.transforms;

import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.Objects;

/// A compiled json-transforms program.
///
/// This implementation follows the Microsoft json-document-transforms specification (wiki):
/// `https://github.com/Microsoft/json-document-transforms/wiki`
///
/// Parse/compile once via `parse(...)`, then apply to many source documents via `run(...)`.
public final class JsonTransform {

    private final TransformAst.ObjectTransform root;

    private JsonTransform(TransformAst.ObjectTransform root) {
        this.root = root;
    }

    /// Parses (compiles) a transform JSON value into a reusable program.
    /// @param transform the transform JSON (must be an object)
    /// @return a compiled, reusable transform program
    public static JsonTransform parse(JsonValue transform) {
        Objects.requireNonNull(transform, "transform must not be null");
        if (!(transform instanceof JsonObject obj)) {
            throw new JsonTransformException("transform must be a JSON object, got: " + transform.getClass().getSimpleName());
        }
        return new JsonTransform(TransformCompiler.compileObject(obj));
    }

    /// Applies this transform to a source JSON value.
    ///
    /// The source must be a JSON object, matching the reference implementation expectation.
    ///
    /// @param source the source JSON value (must be an object)
    /// @return the transformed JSON value
    public JsonValue run(JsonValue source) {
        Objects.requireNonNull(source, "source must not be null");
        if (!(source instanceof JsonObject obj)) {
            throw new JsonTransformException("source must be a JSON object, got: " + source.getClass().getSimpleName());
        }
        return TransformRunner.applyAtDocumentRoot(obj, root);
    }
}

