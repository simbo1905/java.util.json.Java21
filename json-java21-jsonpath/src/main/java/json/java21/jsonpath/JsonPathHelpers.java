package json.java21.jsonpath;

import jdk.incubator.java.util.json.JsonArray;
import jdk.incubator.java.util.json.JsonBoolean;
import jdk.incubator.java.util.json.JsonNull;
import jdk.incubator.java.util.json.JsonNumber;
import jdk.incubator.java.util.json.JsonObject;
import jdk.incubator.java.util.json.JsonString;
import jdk.incubator.java.util.json.JsonValue;

import java.util.List;
import java.util.logging.Logger;

/// Evaluation helpers shared verbatim by both JsonPath engines.
///
/// The interpreted engine and the bytecode-generated engine (JDK 24+ `JsonPathCodegen`)
/// both call these for filter-expression evaluation, so comparisons, path resolution,
/// and diagnostics behave identically regardless of which engine runs a query.
final class JsonPathHelpers {

    private static final Logger LOG = Logger.getLogger(JsonPathHelpers.class.getName());

    private JsonPathHelpers() {}

    /// Resolves a property path against a JsonValue.
    /// @param current the JSON value to resolve from
    /// @param properties the property names, in order
    /// @return the resolved value, or null when any step is missing or the value is not an object
    static JsonValue resolvePropertyPath(JsonValue current, List<String> properties) {
        JsonValue value = current;
        for (final var prop : properties) {
            if (value instanceof JsonObject obj) {
                value = obj.asMap().get(prop);
                if (value == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return value;
    }

    /// Converts a JsonValue to a comparable object for filter comparisons.
    /// @param value the JSON value to convert
    /// @return a String, Double, or Boolean; null for JSON null and other value kinds
    static Object toJsonComparable(JsonValue value) {
        if (value == null) return null;
        return switch (value) {
            case JsonString s -> s.asString();
            case JsonNumber n -> n.asDouble();
            case JsonBoolean b -> b.asBoolean();
            case JsonNull ignored -> null;
            default -> value;
        };
    }

    /// Evaluates a filter comparison using the JsonPath engine's comparison rules.
    /// @param left the left operand (possibly null)
    /// @param op the comparison operator
    /// @param right the right operand (possibly null)
    /// @return true when the comparison is satisfied
    @SuppressWarnings("unchecked")
    static boolean compareValues(Object left, JsonPathAst.ComparisonOp op, Object right) {
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

    /// Normalizes an array index against an array size.
    /// @param index the index, possibly counting from the end
    /// @param size the array size
    /// @return the resolved non-negative index (may be out of bounds)
    static int normalizeIndex(int index, int size) {
        return index < 0 ? size + index : index;
    }

    /// Logs an unsupported script expression in the same way the interpreter does.
    /// Generated executors call this so compiled queries surface the same diagnostics.
    /// @param script the unsupported script text
    static void unsupportedScript(String script) {
        LOG.warning(() -> "Unsupported script expression: " + script);
    }

    /// Logs an unsupported recursive-descent target in the same way the interpreter does.
    /// @param target the unsupported target segment
    static void unsupportedRecursiveTarget(JsonPathAst.Segment target) {
        LOG.finer(() -> "Unsupported target in recursive descent: " + target);
    }

    /// Logs an unsupported union selector in the same way the interpreter does.
    /// @param selector the unsupported selector segment
    static void unsupportedUnionSelector(JsonPathAst.Segment selector) {
        LOG.finer(() -> "Unsupported selector in union: " + selector);
    }
}
