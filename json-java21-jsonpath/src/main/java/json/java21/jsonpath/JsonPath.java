package json.java21.jsonpath;

import jdk.sandbox.java.util.json.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// JsonPath query evaluator for JSON documents.
/// Parses JsonPath expressions and evaluates them against JsonValue instances.
///
/// Usage examples:
/// ```java
/// // Fluent API (preferred)
/// JsonValue json = Json.parse(jsonString);
/// List<JsonValue> results = JsonPath.parse("$.store.book[*].author").query(json);
///
/// // Compiled + static call site (also reusable)
/// JsonPath path = JsonPath.parse("$.store.book[*].author");
/// List<JsonValue> results = JsonPath.query(path, json);
/// ```
///
/// Based on the JSONPath specification from [...](https://goessner.net/articles/JsonPath/)
public final class JsonPath {

    private static final Logger LOG = Logger.getLogger(JsonPath.class.getName());

    private final JsonPathAst.Root ast;
    private final String pathExpression;

    private JsonPath(String pathExpression, JsonPathAst.Root ast) {
        this.pathExpression = pathExpression;
        this.ast = ast;
    }

    /// Parses a JsonPath expression and returns a compiled JsonPath for reuse.
    /// @param path the JsonPath expression
    /// @return a compiled JsonPath that can be used to select from multiple documents
    /// @throws NullPointerException if path is null
    /// @throws JsonPathParseException if the path is invalid
    public static JsonPath parse(String path) {
        Objects.requireNonNull(path, "path must not be null");
        LOG.fine(() -> "Parsing path: " + path);
        final var ast = JsonPathParser.parse(path);
        return new JsonPath(path, ast);
    }

    /// Selects matching values from a JSON document.
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (may be empty)
    /// @throws NullPointerException if json is null
    /// @deprecated Use `query(JsonValue)` (aligns with Goessner JSONPath terminology).
    @Deprecated(forRemoval = false)
    public List<JsonValue> select(JsonValue json) {
        return query(json);
    }

    /// Queries matching values from a JSON document.
    ///
    /// This is the preferred instance API: compile once via `parse(String)`, then call `query(JsonValue)`
    /// for each already-parsed JSON document.
    ///
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (may be empty)
    /// @throws NullPointerException if json is null
    public List<JsonValue> query(JsonValue json) {
        Objects.requireNonNull(json, "json must not be null");
        LOG.fine(() -> "Querying document with path: " + pathExpression);
        return evaluate(ast, json);
    }

    /// Returns the original path expression.
    public String expression() {
        return pathExpression;
    }

    /// Returns the parsed AST.
    public JsonPathAst.Root ast() {
        return ast;
    }

    /// Evaluates a compiled JsonPath against a JSON document.
    /// @param path a compiled JsonPath (typically cached)
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (may be empty)
    /// @throws NullPointerException if path or json is null
    public static List<JsonValue> query(JsonPath path, JsonValue json) {
        Objects.requireNonNull(path, "path must not be null");
        return path.query(json);
    }

    /// Evaluates a pre-parsed JsonPath AST against a JSON document.
    /// @param ast the parsed JsonPath AST
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (may be empty)
    static List<JsonValue> evaluate(JsonPathAst.Root ast, JsonValue json) {
        Objects.requireNonNull(ast, "ast must not be null");
        Objects.requireNonNull(json, "json must not be null");

        final var results = new ArrayList<JsonValue>();
        evaluateSegments(ast.segments(), 0, json, json, results);
        return results;
    }

    private static void evaluateSegments(
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        // If we've processed all segments, current is a match
        if (index >= segments.size()) {
            results.add(current);
            return;
        }

        final var segment = segments.get(index);
        LOG.finer(() -> "Evaluating segment " + index + ": " + segment);

        switch (segment) {
            case JsonPathAst.PropertyAccess prop -> evaluatePropertyAccess(prop, segments, index, current, root, results);
            case JsonPathAst.ArrayIndex arr -> evaluateArrayIndex(arr, segments, index, current, root, results);
            case JsonPathAst.ArraySlice slice -> evaluateArraySlice(slice, segments, index, current, root, results);
            case JsonPathAst.Wildcard wildcard -> evaluateWildcard(segments, index, current, root, results);
            case JsonPathAst.RecursiveDescent desc -> evaluateRecursiveDescent(desc, segments, index, current, root, results);
            case JsonPathAst.Filter filter -> evaluateFilter(filter, segments, index, current, root, results);
            case JsonPathAst.Union union -> evaluateUnion(union, segments, index, current, root, results);
            case JsonPathAst.ScriptExpression script -> evaluateScriptExpression(script, segments, index, current, root, results);
        }
    }

    private static void evaluatePropertyAccess(
            JsonPathAst.PropertyAccess prop,
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        if (current instanceof JsonObject obj) {
            final var value = obj.members().get(prop.name());
            if (value != null) {
                evaluateSegments(segments, index + 1, value, root, results);
            }
        }
    }

    private static void evaluateArrayIndex(
            JsonPathAst.ArrayIndex arr,
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        if (current instanceof JsonArray array) {
            final var elements = array.elements();
            int idx = arr.index();

            // Handle negative indices (from end)
            if (idx < 0) {
                idx = elements.size() + idx;
            }

            if (idx >= 0 && idx < elements.size()) {
                evaluateSegments(segments, index + 1, elements.get(idx), root, results);
            }
        }
    }

    private static void evaluateArraySlice(
            JsonPathAst.ArraySlice slice,
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        if (current instanceof JsonArray array) {
            final var elements = array.elements();
            final int size = elements.size();

            // Resolve start, end, step with defaults
            int start = slice.start() != null ? normalizeIndex(slice.start(), size) : 0;
            int end = slice.end() != null ? normalizeIndex(slice.end(), size) : size;
            int step = slice.step() != null ? slice.step() : 1;

            if (step == 0) {
                return; // Invalid step
            }

            // Clamp values
            start = Math.max(0, Math.min(start, size));
            end = Math.max(0, Math.min(end, size));

            if (step > 0) {
                for (int i = start; i < end; i += step) {
                    evaluateSegments(segments, index + 1, elements.get(i), root, results);
                }
            } else {
                for (int i = start; i > end; i += step) {
                    evaluateSegments(segments, index + 1, elements.get(i), root, results);
                }
            }
        }
    }

    private static int normalizeIndex(int index, int size) {
        if (index < 0) {
            return size + index;
        }
        return index;
    }

    private static void evaluateWildcard(
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        if (current instanceof JsonObject obj) {
            for (final var value : obj.members().values()) {
                evaluateSegments(segments, index + 1, value, root, results);
            }
        } else if (current instanceof JsonArray array) {
            for (final var element : array.elements()) {
                evaluateSegments(segments, index + 1, element, root, results);
            }
        }
    }

    private static void evaluateRecursiveDescent(
            JsonPathAst.RecursiveDescent desc,
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        // First, try matching the target at current level
        evaluateTargetSegment(desc.target(), segments, index, current, root, results);

        // Then recurse into children
        if (current instanceof JsonObject obj) {
            for (final var value : obj.members().values()) {
                evaluateRecursiveDescent(desc, segments, index, value, root, results);
            }
        } else if (current instanceof JsonArray array) {
            for (final var element : array.elements()) {
                evaluateRecursiveDescent(desc, segments, index, element, root, results);
            }
        }
    }

    private static void evaluateTargetSegment(
            JsonPathAst.Segment target,
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        switch (target) {
            case JsonPathAst.PropertyAccess prop -> {
                if (current instanceof JsonObject obj) {
                    final var value = obj.members().get(prop.name());
                    if (value != null) {
                        evaluateSegments(segments, index + 1, value, root, results);
                    }
                }
            }
            case JsonPathAst.Wildcard w -> {
                if (current instanceof JsonObject obj) {
                    for (final var value : obj.members().values()) {
                        evaluateSegments(segments, index + 1, value, root, results);
                    }
                } else if (current instanceof JsonArray array) {
                    for (final var element : array.elements()) {
                        evaluateSegments(segments, index + 1, element, root, results);
                    }
                }
            }
            case JsonPathAst.ArrayIndex arr -> {
                if (current instanceof JsonArray array) {
                    final var elements = array.elements();
                    int idx = arr.index();
                    if (idx < 0) idx = elements.size() + idx;
                    if (idx >= 0 && idx < elements.size()) {
                        evaluateSegments(segments, index + 1, elements.get(idx), root, results);
                    }
                }
            }
            default -> {
                // Other segment types in recursive descent context
                LOG.finer(() -> "Unsupported target in recursive descent: " + target);
            }
        }
    }

    private static void evaluateFilter(
            JsonPathAst.Filter filter,
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        if (current instanceof JsonArray array) {
            for (final var element : array.elements()) {
                if (matchesFilter(filter.expression(), element)) {
                    evaluateSegments(segments, index + 1, element, root, results);
                }
            }
        }
    }

    private static boolean matchesFilter(JsonPathAst.FilterExpression expr, JsonValue current) {
        return switch (expr) {
            case JsonPathAst.ExistsFilter exists -> {
                final var value = resolvePropertyPath(exists.path(), current);
                yield value != null;
            }
            case JsonPathAst.ComparisonFilter comp -> {
                final var leftValue = resolveFilterExpression(comp.left(), current);
                final var rightValue = resolveFilterExpression(comp.right(), current);
                yield compareValues(leftValue, comp.op(), rightValue);
            }
            case JsonPathAst.LogicalFilter logical -> {
                final var leftMatch = matchesFilter(logical.left(), current);
                yield switch (logical.op()) {
                    case AND -> leftMatch && matchesFilter(logical.right(), current);
                    case OR -> leftMatch || matchesFilter(logical.right(), current);
                    case NOT -> !leftMatch;
                };
            }
            case JsonPathAst.CurrentNode cn -> true;
            case JsonPathAst.PropertyPath path -> resolvePropertyPath(path, current) != null;
            case JsonPathAst.LiteralValue lv -> true;
        };
    }

    private static Object resolveFilterExpression(JsonPathAst.FilterExpression expr, JsonValue current) {
        return switch (expr) {
            case JsonPathAst.PropertyPath path -> {
                final var value = resolvePropertyPath(path, current);
                yield jsonValueToComparable(value);
            }
            case JsonPathAst.LiteralValue lit -> lit.value();
            case JsonPathAst.CurrentNode cn2 -> jsonValueToComparable(current);
            default -> null;
        };
    }

    private static JsonValue resolvePropertyPath(JsonPathAst.PropertyPath path, JsonValue current) {
        JsonValue value = current;
        for (final var prop : path.properties()) {
            if (value instanceof JsonObject obj) {
                value = obj.members().get(prop);
                if (value == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return value;
    }

    private static Object jsonValueToComparable(JsonValue value) {
        if (value == null) return null;
        return switch (value) {
            case JsonString s -> s.string();
            case JsonNumber n -> n.toDouble();
            case JsonBoolean b -> b.bool();
            case JsonNull jn -> null;
            default -> value;
        };
    }

    @SuppressWarnings("unchecked")
    private static boolean compareValues(Object left, JsonPathAst.ComparisonOp op, Object right) {
        if (left == null || right == null) {
            return switch (op) {
                case EQ -> left == right;
                case NE -> left != right;
                default -> false;
            };
        }

        // Try numeric comparison
        if (left instanceof Number leftNum && right instanceof Number rightNum) {
            final double l = leftNum.doubleValue();
            final double r = rightNum.doubleValue();
            return switch (op) {
                case EQ -> l == r;
                case NE -> l != r;
                case LT -> l < r;
                case LE -> l <= r;
                case GT -> l > r;
                case GE -> l >= r;
            };
        }

        // String comparison
        if (left instanceof String && right instanceof String) {
            @SuppressWarnings("rawtypes")
            final int cmp = ((Comparable) left).compareTo(right);
            return switch (op) {
                case EQ -> cmp == 0;
                case NE -> cmp != 0;
                case LT -> cmp < 0;
                case LE -> cmp <= 0;
                case GT -> cmp > 0;
                case GE -> cmp >= 0;
            };
        }

        // Boolean comparison
        if (left instanceof Boolean && right instanceof Boolean) {
            return switch (op) {
                case EQ -> left.equals(right);
                case NE -> !left.equals(right);
                default -> false;
            };
        }

        // Fallback equality
        return switch (op) {
            case EQ -> left.equals(right);
            case NE -> !left.equals(right);
            default -> false;
        };
    }

    private static void evaluateUnion(
            JsonPathAst.Union union,
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        for (final var selector : union.selectors()) {
            switch (selector) {
                case JsonPathAst.ArrayIndex arr -> evaluateArrayIndex(arr, segments, index, current, root, results);
                case JsonPathAst.PropertyAccess prop -> evaluatePropertyAccess(prop, segments, index, current, root, results);
                default -> LOG.finer(() -> "Unsupported selector in union: " + selector);
            }
        }
    }

    private static void evaluateScriptExpression(
            JsonPathAst.ScriptExpression script,
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        if (current instanceof JsonArray array) {
            // Simple support for @.length-1 pattern
            final var scriptText = script.script().trim();
            if (scriptText.equals("@.length-1")) {
                final int lastIndex = array.elements().size() - 1;
                if (lastIndex >= 0) {
                    evaluateSegments(segments, index + 1, array.elements().get(lastIndex), root, results);
                }
            } else {
                LOG.warning(() -> "Unsupported script expression: " + scriptText);
            }
        }
    }
}
