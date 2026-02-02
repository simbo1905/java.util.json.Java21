package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// JsonPath query evaluator interface for JSON documents.
/// Parses JsonPath expressions and evaluates them against JsonValue instances.
///
/// Usage examples:
/// ```java
/// // Fluent API
/// JsonValue json = Json.parse(jsonString);
/// List<JsonValue> results = JsonPath.parse("$.store.book[*].author").query(json);
///
/// // Compiled + static call site (also reusable)
/// JsonPath path = JsonPath.parse("$.store.book[*].author");
/// List<JsonValue> results = JsonPath.query(path, json);
///
/// // Runtime-compiled for best performance
/// JsonPath compiled = JsonPath.compile(path);
/// List<JsonValue> results = compiled.query(json);
/// ```
///
/// Based on the JSONPath specification from [...](https://goessner.net/articles/JsonPath/)
public sealed interface JsonPath permits JsonPathInterpreted, JsonPathCompiled {

    Logger LOG = Logger.getLogger(JsonPath.class.getName());

    /// Queries matching values from a JSON document.
    ///
    /// Instance API: compile once via `parse(String)`, then call `query(JsonValue)` for each already-parsed
    /// JSON document.
    ///
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (maybe empty)
    /// @throws NullPointerException if JSON is null
    List<JsonValue> query(JsonValue json);

    /// Parses a JsonPath expression and returns an interpreted JsonPath for reuse.
    /// @param path the JsonPath expression
    /// @return an interpreted JsonPath that can be used to select from multiple documents
    /// @throws NullPointerException if path is null
    /// @throws JsonPathParseException if the path is invalid
    static JsonPath parse(String path) {
        Objects.requireNonNull(path, "path must not be null");
        LOG.fine(() -> "Parsing path: " + path);
        final var ast = JsonPathParser.parse(path);
        return new JsonPathInterpreted(ast);
    }

    /// Compiles a JsonPath into optimized bytecode for best performance.
    /// If the input is already compiled, returns it unchanged.
    /// @param path the JsonPath to compile (typically from `parse()`)
    /// @return a compiled JsonPath with optimal performance
    static JsonPath compile(JsonPath path) {
        Objects.requireNonNull(path, "path must not be null");
        return switch (path) {
            case JsonPathCompiled compiled -> compiled;
            case JsonPathInterpreted interpreted -> JsonPathCompiler.compile(interpreted);
        };
    }

    /// Evaluates a compiled JsonPath against a JSON document.
    /// @param path a compiled JsonPath (typically cached)
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (maybe empty)
    /// @throws NullPointerException if path or JSON is null
    static List<JsonValue> query(JsonPath path, JsonValue json) {
        Objects.requireNonNull(path, "path must not be null");
        return path.query(json);
    }

    /// Evaluates a JsonPath expression against a JSON document.
    ///
    /// Intended for one-off usage; for hot paths, prefer caching the compiled `JsonPath` via `parse(String)`.
    ///
    /// @param path the JsonPath expression to parse
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (maybe empty)
    /// @throws NullPointerException if path or JSON is null
    /// @throws JsonPathParseException if the path is invalid
    static List<JsonValue> query(String path, JsonValue json) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(json, "json must not be null");
        return parse(path).query(json);
    }

    /// Returns true if this JsonPath is an AST-based interpreter (not yet compiled).
    default boolean isInterpreted() {
        return this instanceof JsonPathInterpreted;
    }

    /// Returns true if this JsonPath is compiled to bytecode.
    default boolean isCompiled() {
        return this instanceof JsonPathCompiled;
    }

    /// Returns the AST for this JsonPath, if available.
    /// Compiled paths may not have the AST readily available.
    /// @return the AST root, or null if not available
    default JsonPathAst.Root ast() {
        return switch (this) {
            case JsonPathInterpreted interpreted -> interpreted.ast();
            case JsonPathCompiled compiled -> compiled.ast();
        };
    }
}
