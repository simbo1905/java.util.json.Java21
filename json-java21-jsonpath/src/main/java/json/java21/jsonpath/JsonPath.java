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
public final class JsonPath {

    private static final Logger LOG = Logger.getLogger(JsonPath.class.getName());

    private final JsonPathAst.Root ast;

    private JsonPath(JsonPathAst.Root ast) {
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
        return new JsonPath(ast);
    }

    /// Queries matching values from a JSON document.
    ///
    /// Instance API: compile once via `parse(String)`, then call `query(JsonValue)` for each already-parsed
    /// JSON document.
    ///
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (maybe empty)
    /// @throws NullPointerException if JSON is null
    public List<JsonValue> query(JsonValue json) {
        Objects.requireNonNull(json, "json must not be null");
        LOG.fine(() -> "Querying document with path: " + this);
        return evaluate(ast, json);
    }

    /// Reconstructs the JsonPath expression from the AST.
    @Override
    public String toString() {
        return reconstruct(ast);
    }

    /// Evaluates a compiled JsonPath against a JSON document.
    /// @param path a compiled JsonPath (typically cached)
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (maybe empty)
    /// @throws NullPointerException if path or JSON is null
    public static List<JsonValue> query(JsonPath path, JsonValue json) {
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
    public static List<JsonValue> query(String path, JsonValue json) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(json, "json must not be null");
        return parse(path).query(json);
    }

    /// Evaluates a pre-parsed JsonPath AST against a JSON document.
    /// @param ast the parsed JsonPath AST
    /// @param json the JSON document to query
    /// @return a list of matching JsonValue instances (maybe empty)
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
            case JsonPathAst.Wildcard ignored -> evaluateWildcard(segments, index, current, root, results);
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

            final int step = slice.step() != null ? slice.step() : 1;

            if (step == 0) {
                return; // Invalid step
            }

            if (step > 0) {
                int start = slice.start() != null ? normalizeIndex(slice.start(), size) : 0;
                int end = slice.end() != null ? normalizeIndex(slice.end(), size) : size;

                start = Math.max(0, Math.min(start, size));
                end = Math.max(0, Math.min(end, size));

                for (int i = start; i < end; i += step) {
                    evaluateSegments(segments, index + 1, elements.get(i), root, results);
                }
            } else {
                int start = slice.start() != null ? normalizeIndex(slice.start(), size) : size - 1;
                final int end = slice.end() != null ? normalizeIndex(slice.end(), size) : -1;

                start = Math.max(0, Math.min(start, size - 1));

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
            case JsonPathAst.Wildcard ignored -> {
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
            default -> // Other segment types in recursive descent context
                    LOG.finer(() -> "Unsupported target in recursive descent: " + target);
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
            case JsonPathAst.CurrentNode ignored1 -> true;
            case JsonPathAst.PropertyPath path -> resolvePropertyPath(path, current) != null;
            case JsonPathAst.LiteralValue ignored -> true;
        };
    }

    private static Object resolveFilterExpression(JsonPathAst.FilterExpression expr, JsonValue current) {
        return switch (expr) {
            case JsonPathAst.PropertyPath path -> {
                final var value = resolvePropertyPath(path, current);
                yield jsonValueToComparable(value);
            }
            case JsonPathAst.LiteralValue lit -> lit.value();
            case JsonPathAst.CurrentNode ignored -> jsonValueToComparable(current);
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
            case JsonNull ignored -> null;
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
        switch (left) {
            case Number leftNum when right instanceof Number rightNum -> {
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
            case String ignored when right instanceof String -> {
                @SuppressWarnings("rawtypes") final int cmp = ((Comparable) left).compareTo(right);
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
            case Boolean ignored when right instanceof Boolean -> {
                return switch (op) {
                    case EQ -> left.equals(right);
                    case NE -> !left.equals(right);
                    default -> false;
                };
            }
            default -> {
            }
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

    private static String reconstruct(JsonPathAst.Root root) {
        final var sb = new StringBuilder("$");
        for (final var segment : root.segments()) {
            appendSegment(sb, segment);
        }
        return sb.toString();
    }

    private static void appendSegment(StringBuilder sb, JsonPathAst.Segment segment) {
        switch (segment) {
            case JsonPathAst.PropertyAccess prop -> {
                if (isSimpleIdentifier(prop.name())) {
                    sb.append(".").append(prop.name());
                } else {
                    sb.append("['").append(escape(prop.name())).append("']");
                }
            }
            case JsonPathAst.ArrayIndex arr -> sb.append("[").append(arr.index()).append("]");
            case JsonPathAst.ArraySlice slice -> {
                sb.append("[");
                if (slice.start() != null) sb.append(slice.start());
                sb.append(":");
                if (slice.end() != null) sb.append(slice.end());
                if (slice.step() != null) sb.append(":").append(slice.step());
                sb.append("]");
            }
            case JsonPathAst.Wildcard ignored -> sb.append(".*");
            case JsonPathAst.RecursiveDescent desc -> {
                sb.append("..");
                // RecursiveDescent target is usually PropertyAccess or Wildcard, 
                // but can be other things in theory. 
                // If target is PropertyAccess("foo"), append "foo".
                // If target is Wildcard, append "*".
                // Our AST structure wraps the target segment.
                // We need to handle how it's appended. 
                // appendSegment prepends "." or "[" usually.
                // But ".." replaces the dot.
                // Let special case the target printing.
                appendRecursiveTarget(sb, desc.target());
            }
            case JsonPathAst.Filter filter -> {
                sb.append("[?(");
                appendFilterExpression(sb, filter.expression());
                sb.append(")]");
            }
            case JsonPathAst.Union union -> {
                sb.append("[");
                final var selectors = union.selectors();
                for (int i = 0; i < selectors.size(); i++) {
                    if (i > 0) sb.append(",");
                    appendUnionSelector(sb, selectors.get(i));
                }
                sb.append("]");
            }
            case JsonPathAst.ScriptExpression script -> sb.append("[(").append(script.script()).append(")]");
        }
    }

    private static void appendRecursiveTarget(StringBuilder sb, JsonPathAst.Segment target) {
        if (target instanceof JsonPathAst.PropertyAccess(String name)) {
            sb.append(name); // ..name
        } else if (target instanceof JsonPathAst.Wildcard) {
            sb.append("*"); // ..*
        } else {
            // Fallback for other types if they ever occur in recursive position
            appendSegment(sb, target);
        }
    }

    private static void appendUnionSelector(StringBuilder sb, JsonPathAst.Segment selector) {
        if (selector instanceof JsonPathAst.PropertyAccess(String name)) {
            sb.append("'").append(escape(name)).append("'");
        } else if (selector instanceof JsonPathAst.ArrayIndex(int index)) {
            sb.append(index);
        } else {
            // Fallback
            appendSegment(sb, selector);
        }
    }

    private static void appendFilterExpression(StringBuilder sb, JsonPathAst.FilterExpression expr) {
        switch (expr) {
            case JsonPathAst.ExistsFilter exists -> appendFilterExpression(sb, exists.path()); // Should print the path
            case JsonPathAst.ComparisonFilter comp -> {
                appendFilterExpression(sb, comp.left());
                sb.append(comp.op().symbol());
                appendFilterExpression(sb, comp.right());
            }
            case JsonPathAst.LogicalFilter logical -> {
                if (logical.op() == JsonPathAst.LogicalOp.NOT) {
                    sb.append("!");
                    appendFilterExpression(sb, logical.left());
                } else {
                    sb.append("(");
                    appendFilterExpression(sb, logical.left());
                    sb.append(" ").append(logical.op().symbol()).append(" ");
                    appendFilterExpression(sb, logical.right());
                    sb.append(")");
                }
            }
            case JsonPathAst.CurrentNode ignored -> sb.append("@");
            case JsonPathAst.PropertyPath path -> {
                sb.append("@");
                for (String p : path.properties()) {
                    if (isSimpleIdentifier(p)) {
                        sb.append(".").append(p);
                    } else {
                        sb.append("['").append(escape(p)).append("']");
                    }
                }
            }
            case JsonPathAst.LiteralValue lit -> {
                if (lit.value() instanceof String s) {
                    sb.append("'").append(escape(s)).append("'");
                } else {
                    sb.append(lit.value());
                }
            }
        }
    }

    private static boolean isSimpleIdentifier(String name) {
        if (name.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return true;
    }

    private static String escape(String s) {
        return s.replace("'", "\\'");
    }
}
