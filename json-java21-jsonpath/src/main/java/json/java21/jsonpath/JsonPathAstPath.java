package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// Package-private AST-backed `JsonPath` implementation.
///
/// This is the behavior-preserving interpreter that walks `JsonPathAst` at runtime.
final class JsonPathAstPath implements JsonPath, JsonPathAstBacked {

    private static final Logger LOG = Logger.getLogger(JsonPathAstPath.class.getName());

    private final JsonPathAst.Root ast;

    JsonPathAstPath(JsonPathAst.Root ast) {
        this.ast = Objects.requireNonNull(ast, "ast must not be null");
    }

    @Override
    public List<JsonValue> query(JsonValue json) {
        Objects.requireNonNull(json, "json must not be null");
        LOG.fine(() -> "Querying document with path: " + this);
        return JsonPathAstInterpreter.evaluate(ast, json);
    }

    @Override
    public String toString() {
        return JsonPathAstInterpreter.reconstruct(ast);
    }

    @Override
    public JsonPathAst.Root ast() {
        return ast;
    }
}

