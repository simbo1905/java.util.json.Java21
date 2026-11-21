package json.java21.jsonpath;

import jdk.incubator.java.util.json.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// AST-walking interpreter implementation of [JsonPath].
/// Parses JsonPath expressions and evaluates them against JsonValue instances by walking the AST.
///
/// This is the default engine returned by {@code JsonPath.parse(String)}; it is available
/// on JDK 21+ and has no dependency on the ClassFile API. For the bytecode-generated
/// engine, see {@code JsonPathCodegen} (JDK 24+).
final class JsonPathInterpreted implements JsonPath {

    private static final Logger LOG = Logger.getLogger(JsonPathInterpreted.class.getName());

    private final JsonPathAst.Root astRoot;

    JsonPathInterpreted(JsonPathAst.Root ast) {
        this.astRoot = ast;
    }

    /// Returns the AST for this interpreted JsonPath.
    /// @return the parsed AST root
    @Override
    public JsonPathAst.Root ast() {
        return astRoot;
    }

    @Override
    public List<JsonValue> query(JsonValue json) {
        Objects.requireNonNull(json, "json must not be null");
        LOG.fine(() -> "Querying document with path: " + this);
        return evaluate(astRoot, json);
    }

    /// Reconstructs the JsonPath expression from the AST.
    @Override
    public String toString() {
        return reconstruct(astRoot);
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
            final var value = obj.asMap().get(prop.name());
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
            final var elements = array.asList();
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
            final var elements = array.asList();
            final int size = elements.size();

            final int step = slice.step() != null ? slice.step() : 1;

            if (step == 0) {
                return; // Invalid step
            }

            if (step > 0) {
                int start = slice.start() != null ? JsonPathHelpers.normalizeIndex(slice.start(), size) : 0;
                int end = slice.end() != null ? JsonPathHelpers.normalizeIndex(slice.end(), size) : size;

                start = Math.max(0, Math.min(start, size));
                end = Math.max(0, Math.min(end, size));

                for (int i = start; i < end; i += step) {
                    evaluateSegments(segments, index + 1, elements.get(i), root, results);
                }
            } else {
                int start = slice.start() != null ? JsonPathHelpers.normalizeIndex(slice.start(), size) : size - 1;
                final int end = slice.end() != null ? JsonPathHelpers.normalizeIndex(slice.end(), size) : -1;

                start = Math.max(0, Math.min(start, size - 1));

                for (int i = start; i > end; i += step) {
                    evaluateSegments(segments, index + 1, elements.get(i), root, results);
                }
            }
        }
    }

    private static void evaluateWildcard(
            List<JsonPathAst.Segment> segments,
            int index,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        if (current instanceof JsonObject obj) {
            for (final var value : obj.asMap().values()) {
                evaluateSegments(segments, index + 1, value, root, results);
            }
        } else if (current instanceof JsonArray array) {
            for (final var element : array.asList()) {
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
            for (final var value : obj.asMap().values()) {
                evaluateRecursiveDescent(desc, segments, index, value, root, results);
            }
        } else if (current instanceof JsonArray array) {
            for (final var element : array.asList()) {
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
                    final var value = obj.asMap().get(prop.name());
                    if (value != null) {
                        evaluateSegments(segments, index + 1, value, root, results);
                    }
                }
            }
            case JsonPathAst.Wildcard ignored -> {
                if (current instanceof JsonObject obj) {
                    for (final var value : obj.asMap().values()) {
                        evaluateSegments(segments, index + 1, value, root, results);
                    }
                } else if (current instanceof JsonArray array) {
                    for (final var element : array.asList()) {
                        evaluateSegments(segments, index + 1, element, root, results);
                    }
                }
            }
            case JsonPathAst.ArrayIndex arr -> {
                if (current instanceof JsonArray array) {
                    final var elements = array.asList();
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
            for (final var element : array.asList()) {
                if (matchesFilter(filter.expression(), element)) {
                    evaluateSegments(segments, index + 1, element, root, results);
                }
            }
        }
    }

    private static boolean matchesFilter(JsonPathAst.FilterExpression expr, JsonValue current) {
        return switch (expr) {
            case JsonPathAst.ExistsFilter exists -> {
                final var value = JsonPathHelpers.resolvePropertyPath(current, exists.path().properties());
                yield value != null;
            }
            case JsonPathAst.ComparisonFilter comp -> {
                final var leftValue = resolveFilterExpression(comp.left(), current);
                final var rightValue = resolveFilterExpression(comp.right(), current);
                yield JsonPathHelpers.compareValues(leftValue, comp.op(), rightValue);
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
            case JsonPathAst.PropertyPath path -> JsonPathHelpers.resolvePropertyPath(current, path.properties()) != null;
            case JsonPathAst.LiteralValue ignored -> true;
        };
    }

    private static Object resolveFilterExpression(JsonPathAst.FilterExpression expr, JsonValue current) {
        return switch (expr) {
            case JsonPathAst.PropertyPath path -> {
                final var value = JsonPathHelpers.resolvePropertyPath(current, path.properties());
                yield JsonPathHelpers.toJsonComparable(value);
            }
            case JsonPathAst.LiteralValue lit -> lit.value();
            case JsonPathAst.CurrentNode ignored -> JsonPathHelpers.toJsonComparable(current);
            default -> null;
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
                final int lastIndex = array.asList().size() - 1;
                if (lastIndex >= 0) {
                    evaluateSegments(segments, index + 1, array.asList().get(lastIndex), root, results);
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
