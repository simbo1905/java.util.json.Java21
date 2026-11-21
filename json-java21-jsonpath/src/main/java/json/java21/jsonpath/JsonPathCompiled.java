package json.java21.jsonpath;

import jdk.incubator.java.util.json.JsonValue;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// Bytecode-backed implementation of [JsonPath].
///
/// Instances are produced by the compiled engine only: `JsonPathCodegen` (JDK 24+)
/// generates a Java 21 classfile implementing [JsonPathExecutor] and wraps it here.
/// The resulting path implements the same [JsonPath] public surface as the
/// interpreted engine, so callers can hand either to the same API.
final class JsonPathCompiled implements JsonPath {

    private static final Logger LOG = Logger.getLogger(JsonPathCompiled.class.getName());

    private final JsonPathAst.Root astRoot;
    private final JsonPathExecutor executor;
    private final String originalPath;

    JsonPathCompiled(JsonPathAst.Root ast, JsonPathExecutor executor, String originalPath) {
        this.astRoot = Objects.requireNonNull(ast, "ast must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.originalPath = Objects.requireNonNull(originalPath, "originalPath must not be null");
    }

    /// Returns the AST shared by both engines for this compiled JsonPath.
    /// @return the parsed AST root
    @Override
    public JsonPathAst.Root ast() {
        return astRoot;
    }

    /// Exposes the underlying generated executor for parity tests and diagnostics.
    /// @return the bytecode-generated executor
    JsonPathExecutor executor() {
        return executor;
    }

    /// The original path text that was compiled, preserved for diagnostics.
    /// @return the original JsonPath expression
    String originalPath() {
        return originalPath;
    }

    @Override
    public List<JsonValue> query(JsonValue json) {
        Objects.requireNonNull(json, "json must not be null");
        LOG.fine(() -> "Querying document with compiled path: " + originalPath);
        return executor.execute(json, json);
    }

    @Override
    public String toString() {
        return originalPath;
    }
}
