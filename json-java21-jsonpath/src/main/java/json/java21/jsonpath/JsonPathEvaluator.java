package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

import static json.java21.jsonpath.JsonPathAst.Segment;

sealed interface JsonPathEvaluator permits JsonPathEvaluator.Impl {

    static List<JsonValue> select(JsonPathAst ast, JsonValue document) {
        return Impl.select(ast, document);
    }

    final class Impl implements JsonPathEvaluator {
        static List<JsonValue> select(JsonPathAst ast, JsonValue document) {
            var current = List.of(document);
            for (final var seg : ast.segments()) {
                current = apply(seg, current);
            }
            return current;
        }

        private static List<JsonValue> apply(Segment seg, List<JsonValue> current) {
            return switch (seg) {
                case Segment.Child child -> selectChild(current, child.name());
                case Segment.Wildcard ignored -> selectWildcard(current);
            };
        }

        private static List<JsonValue> selectChild(List<JsonValue> current, String name) {
            final var out = new ArrayList<JsonValue>();
            for (final var node : current) {
                switch (node) {
                    case JsonObject obj -> {
                        final var val = obj.members().get(name);
                        if (val != null) out.add(val);
                    }
                    case JsonArray arr -> arr.elements().forEach(v -> {
                        if (v instanceof JsonObject o) {
                            final var val = o.members().get(name);
                            if (val != null) out.add(val);
                        }
                    });
                    default -> {
                    }
                }
            }
            return List.copyOf(out);
        }

        private static List<JsonValue> selectWildcard(List<JsonValue> current) {
            final var out = new ArrayList<JsonValue>();
            for (final var node : current) {
                switch (node) {
                    case JsonObject obj -> out.addAll(obj.members().values());
                    case JsonArray arr -> out.addAll(arr.elements());
                    default -> {
                    }
                }
            }
            return List.copyOf(out);
        }
    }
}

