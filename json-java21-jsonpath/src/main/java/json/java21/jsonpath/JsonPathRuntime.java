package json.java21.jsonpath;

import jdk.sandbox.java.util.json.*;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/// Runtime helpers used by generated, compiler-backed JsonPath implementations.
///
/// These helpers are package-private so generated code can call them without expanding the public API.
final class JsonPathRuntime {

    private static final Logger LOG = Logger.getLogger(JsonPathRuntime.class.getName());

    private JsonPathRuntime() {
    }

    static Stream<JsonValue> selectProperty(JsonValue v, String name) {
        if (v instanceof JsonObject obj) {
            final var value = obj.members().get(name);
            return value == null ? Stream.empty() : Stream.of(value);
        }
        return Stream.empty();
    }

    static Stream<JsonValue> selectIndex(JsonValue v, int index) {
        if (v instanceof JsonArray array) {
            final var elements = array.elements();
            int idx = index;
            if (idx < 0) {
                idx = elements.size() + idx;
            }
            if (idx >= 0 && idx < elements.size()) {
                return Stream.of(elements.get(idx));
            }
        }
        return Stream.empty();
    }

    static Stream<JsonValue> selectSlice(JsonValue v, Integer start, Integer end, Integer step) {
        if (!(v instanceof JsonArray array)) {
            return Stream.empty();
        }

        final var elements = array.elements();
        final int size = elements.size();
        final int actualStep = step != null ? step : 1;
        if (actualStep == 0) {
            return Stream.empty();
        }

        if (actualStep > 0) {
            int s = start != null ? normalizeIndex(start, size) : 0;
            int e = end != null ? normalizeIndex(end, size) : size;

            s = Math.max(0, Math.min(s, size));
            e = Math.max(0, Math.min(e, size));

            return IntStream.iterate(s, i -> i < e, i -> i + actualStep)
                    .mapToObj(elements::get);
        }

        int s = start != null ? normalizeIndex(start, size) : size - 1;
        final int e = end != null ? normalizeIndex(end, size) : -1;
        s = Math.max(0, Math.min(s, size - 1));

        return IntStream.iterate(s, i -> i > e, i -> i + actualStep)
                .mapToObj(elements::get);
    }

    static Stream<JsonValue> selectWildcard(JsonValue v) {
        if (v instanceof JsonObject obj) {
            return obj.members().values().stream();
        }
        if (v instanceof JsonArray array) {
            return array.elements().stream();
        }
        return Stream.empty();
    }

    static Stream<JsonValue> selectUnionProperties(JsonValue v, String[] names) {
        Objects.requireNonNull(names, "names must not be null");
        if (!(v instanceof JsonObject obj)) {
            return Stream.empty();
        }
        return Stream.of(names)
                .map(n -> obj.members().get(n))
                .filter(Objects::nonNull);
    }

    static Stream<JsonValue> selectUnionIndices(JsonValue v, int[] indices) {
        Objects.requireNonNull(indices, "indices must not be null");
        if (!(v instanceof JsonArray array)) {
            return Stream.empty();
        }
        final var elements = array.elements();
        return IntStream.of(indices)
                .map(i -> i < 0 ? elements.size() + i : i)
                .filter(i -> i >= 0 && i < elements.size())
                .mapToObj(elements::get);
    }

    static Stream<JsonValue> selectScript(JsonValue v, String script) {
        Objects.requireNonNull(script, "script must not be null");
        if (!(v instanceof JsonArray array)) {
            return Stream.empty();
        }
        final var scriptText = script.trim();
        if (scriptText.equals("@.length-1")) {
            final int lastIndex = array.elements().size() - 1;
            if (lastIndex >= 0) {
                return Stream.of(array.elements().get(lastIndex));
            }
            return Stream.empty();
        }
        LOG.warning(() -> "Unsupported script expression: " + scriptText);
        return Stream.empty();
    }

    static Stream<JsonValue> filterArray(JsonValue v, Predicate<JsonValue> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (v instanceof JsonArray array) {
            return array.elements().stream().filter(predicate);
        }
        return Stream.empty();
    }

    static Stream<JsonValue> selectRecursiveProperty(JsonValue v, String name) {
        final var b = Stream.<JsonValue>builder();
        walkRecursive(v, node -> {
            if (node instanceof JsonObject obj) {
                final var value = obj.members().get(name);
                if (value != null) {
                    b.add(value);
                }
            }
        });
        return b.build();
    }

    static Stream<JsonValue> selectRecursiveWildcard(JsonValue v) {
        final var b = Stream.<JsonValue>builder();
        walkRecursive(v, node -> {
            if (node instanceof JsonObject obj) {
                obj.members().values().forEach(b::add);
            } else if (node instanceof JsonArray array) {
                array.elements().forEach(b::add);
            }
        });
        return b.build();
    }

    private static void walkRecursive(JsonValue v, java.util.function.Consumer<JsonValue> atNode) {
        atNode.accept(v);
        if (v instanceof JsonObject obj) {
            obj.members().values().forEach(child -> walkRecursive(child, atNode));
        } else if (v instanceof JsonArray array) {
            array.elements().forEach(child -> walkRecursive(child, atNode));
        }
    }

    static JsonValue resolvePropertyPath(JsonValue current, String[] properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        JsonValue value = current;
        for (final var prop : properties) {
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

    static Object toComparable(JsonValue value) {
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
    static boolean compareComparable(Object left, JsonPathAst.ComparisonOp op, Object right) {
        if (left == null || right == null) {
            return switch (op) {
                case EQ -> left == right;
                case NE -> left != right;
                default -> false;
            };
        }

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

        return switch (op) {
            case EQ -> left.equals(right);
            case NE -> !left.equals(right);
            default -> false;
        };
    }

    private static int normalizeIndex(int index, int size) {
        if (index < 0) {
            return size + index;
        }
        return index;
    }
}

