package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// Runtime-compiled implementation of JsonPath for optimal performance.
/// Uses JDK compiler tools to generate and compile Java bytecode at runtime.
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

    @Override
    public JsonPathAst.Root ast() {
        return astRoot;
    }

    @Override
    public List<JsonValue> query(JsonValue json) {
        Objects.requireNonNull(json, "json must not be null");
        LOG.fine(() -> "Querying document with compiled path: " + this);
        return executor.execute(json, json);
    }

    @Override
    public String toString() {
        return originalPath;
    }

    /// Functional interface for compiled JsonPath executors.
    @FunctionalInterface
    interface JsonPathExecutor {
        /// Executes the compiled JsonPath query against a JSON document.
        /// @param current the current node being evaluated
        /// @param root the root document (for $ references in filters)
        /// @return a list of matching JsonValue instances
        List<JsonValue> execute(JsonValue current, JsonValue root);
    }
}
