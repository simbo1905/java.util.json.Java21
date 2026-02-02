package json.java21.jsonpath;

import jdk.sandbox.java.util.json.*;

import java.util.List;

/// Public helper methods for runtime-compiled JsonPath executors.
/// This class provides access to internal evaluation methods that generated code needs.
public final class JsonPathHelpers {

    private JsonPathHelpers() {
        // utility class
    }

    /// Resolves a property path from a JsonValue.
    /// @param current the current JSON value
    /// @param props the property path to resolve
    /// @return the resolved value, or null if not found
    public static JsonValue getPath(JsonValue current, String... props) {
        JsonValue value = current;
        for (final var prop : props) {
            if (value instanceof JsonObject obj) {
                value = obj.members().get(prop);
                if (value == null) return null;
            } else {
                return null;
            }
        }
        return value;
    }

    /// Converts a JsonValue to a comparable object for filter comparisons.
    /// @param value the JSON value to convert
    /// @return a comparable object (String, Number, Boolean, or null)
    public static Object toComparable(JsonValue value) {
        if (value == null) return null;
        return switch (value) {
            case JsonString s -> s.string();
            case JsonNumber n -> n.toDouble();
            case JsonBoolean b -> b.bool();
            case JsonNull ignored -> null;
            default -> value;
        };
    }

    /// Compares two values using the specified operator.
    /// @param left the left operand
    /// @param op the comparison operator name (EQ, NE, LT, LE, GT, GE)
    /// @param right the right operand
    /// @return true if the comparison is satisfied
    @SuppressWarnings("unchecked")
    public static boolean compareValues(Object left, String op, Object right) {
        if (left == null || right == null) {
            return switch (op) {
                case "EQ" -> left == right;
                case "NE" -> left != right;
                default -> false;
            };
        }

        // Numeric comparison
        if (left instanceof Number leftNum && right instanceof Number rightNum) {
            final double l = leftNum.doubleValue();
            final double r = rightNum.doubleValue();
            return switch (op) {
                case "EQ" -> l == r;
                case "NE" -> l != r;
                case "LT" -> l < r;
                case "LE" -> l <= r;
                case "GT" -> l > r;
                case "GE" -> l >= r;
                default -> false;
            };
        }

        // String comparison
        if (left instanceof String && right instanceof String) {
            @SuppressWarnings("rawtypes")
            final int cmp = ((Comparable) left).compareTo(right);
            return switch (op) {
                case "EQ" -> cmp == 0;
                case "NE" -> cmp != 0;
                case "LT" -> cmp < 0;
                case "LE" -> cmp <= 0;
                case "GT" -> cmp > 0;
                case "GE" -> cmp >= 0;
                default -> false;
            };
        }

        // Boolean comparison
        if (left instanceof Boolean && right instanceof Boolean) {
            return switch (op) {
                case "EQ" -> left.equals(right);
                case "NE" -> !left.equals(right);
                default -> false;
            };
        }

        // Fallback equality
        return switch (op) {
            case "EQ" -> left.equals(right);
            case "NE" -> !left.equals(right);
            default -> false;
        };
    }

    /// Normalizes an array index (handles negative indices).
    /// @param index the index (possibly negative)
    /// @param size the array size
    /// @return the normalized index
    public static int normalizeIdx(int index, int size) {
        return index < 0 ? size + index : index;
    }

    /// Evaluates recursive descent from a starting value.
    /// This is used for $.. patterns that need to search all descendants.
    /// @param propertyName the property name to search for (null for wildcard)
    /// @param current the current value to search from
    /// @param results the list to add matching values to
    public static void evaluateRecursiveDescent(String propertyName, JsonValue current, List<JsonValue> results) {
        // First, try matching at current level
        if (propertyName == null) {
            // Wildcard - match all children
            if (current instanceof JsonObject obj) {
                results.addAll(obj.members().values());
                for (final var value : obj.members().values()) {
                    evaluateRecursiveDescent(null, value, results);
                }
            } else if (current instanceof JsonArray array) {
                results.addAll(array.elements());
                for (final var element : array.elements()) {
                    evaluateRecursiveDescent(null, element, results);
                }
            }
        } else {
            // Named property - match specific property
            if (current instanceof JsonObject obj) {
                final var value = obj.members().get(propertyName);
                if (value != null) {
                    results.add(value);
                }
                for (final var child : obj.members().values()) {
                    evaluateRecursiveDescent(propertyName, child, results);
                }
            } else if (current instanceof JsonArray array) {
                for (final var element : array.elements()) {
                    evaluateRecursiveDescent(propertyName, element, results);
                }
            }
        }
    }

    /// Evaluates recursive descent and then applies subsequent segments.
    /// This is a more general version that delegates back to the interpreter for complex cases.
    /// @param path the original JsonPath being evaluated
    /// @param segmentIndex the index of the recursive descent segment
    /// @param current the current value
    /// @param root the root document
    /// @param results the results list
    public static void evaluateRecursiveDescentFull(
            JsonPath path,
            int segmentIndex,
            JsonValue current,
            JsonValue root,
            List<JsonValue> results) {

        // For full recursive descent support, we delegate to the interpreter
        // This handles the case where there are segments after the recursive descent
        if (path instanceof JsonPathInterpreted interpreted) {
            final var ast = interpreted.ast();
            JsonPathInterpreted.evaluateSegments(ast.segments(), segmentIndex, current, root, results);
        } else if (path.ast() != null) {
            JsonPathInterpreted.evaluateSegments(path.ast().segments(), segmentIndex, current, root, results);
        }
    }

    /// Collects all arrays recursively from a JSON value.
    /// Used for recursive descent with array index targets like $..book[2].
    /// @param current the current JSON value to search
    /// @param arrays the list to collect arrays into
    public static void collectArrays(JsonValue current, List<JsonValue> arrays) {
        if (current instanceof JsonArray array) {
            arrays.add(array);
            for (final var element : array.elements()) {
                collectArrays(element, arrays);
            }
        } else if (current instanceof JsonObject obj) {
            for (final var value : obj.members().values()) {
                if (value instanceof JsonArray) {
                    collectArrays(value, arrays);
                } else if (value instanceof JsonObject) {
                    collectArrays(value, arrays);
                }
            }
        }
    }

    /// Collects values at a specific property path recursively.
    /// Used for recursive descent patterns like $..book.
    /// @param propertyName the property name to search for
    /// @param current the current JSON value to search
    /// @param results the list to collect results into
    public static void collectAtPath(String propertyName, JsonValue current, List<JsonValue> results) {
        if (current instanceof JsonObject obj) {
            final var value = obj.members().get(propertyName);
            if (value != null) {
                results.add(value);
            }
            for (final var child : obj.members().values()) {
                collectAtPath(propertyName, child, results);
            }
        } else if (current instanceof JsonArray array) {
            for (final var element : array.elements()) {
                collectAtPath(propertyName, element, results);
            }
        }
    }
}
