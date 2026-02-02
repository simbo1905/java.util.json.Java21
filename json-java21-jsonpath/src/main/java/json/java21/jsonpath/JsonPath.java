package json.java21.jsonpath;

import jdk.sandbox.java.util.json.*;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// JsonPath query evaluator for JSON documents.
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
/// ```
///
/// Based on the JSONPath specification from [...](https://goessner.net/articles/JsonPath/)
public interface JsonPath {

    Logger LOG = Logger.getLogger(JsonPath.class.getName());

    /// Parses a JsonPath expression and returns a compiled JsonPath for reuse.
    /// @param path the JsonPath expression
    /// @return a compiled JsonPath that can be used to select from multiple documents
    /// @throws NullPointerException if path is null
    /// @throws JsonPathParseException if the path is invalid
    static JsonPath parse(String path) {
        Objects.requireNonNull(path, "path must not be null");
        LOG.fine(() -> "Parsing path: " + path);
        final var ast = JsonPathParser.parse(path);
        return new JsonPathAstPath(ast);
    }

    /// Queries matching values from a JSON document.
    ///
    /// Instance API: compile once via `parse(String)`, then call `query(JsonValue)` for each already-parsed
    /// JSON document.
    ///
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (maybe empty)
    /// @throws NullPointerException if JSON is null
    List<JsonValue> query(JsonValue json);

    /// Evaluates a compiled JsonPath against a JSON document.
    /// @param path a compiled JsonPath (typically cached)
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (maybe empty)
    /// @throws NullPointerException if path or JSON is null
    static List<JsonValue> query(JsonPath path, JsonValue json) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(json, "json must not be null");
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

    /// Returns a (potentially) runtime-compiled version of this JsonPath.
    ///
    /// This method is idempotent: if the receiver is already compiled, it returns itself.
    /// Implementations that do not support runtime compilation may return themselves.
    default JsonPath compile() {
        return JsonPathCompiler.compile(this);
    }

    /// Returns a (potentially) runtime-compiled version of the provided JsonPath.
    static JsonPath compile(JsonPath path) {
        Objects.requireNonNull(path, "path must not be null");
        return JsonPathCompiler.compile(path);
    }
}
