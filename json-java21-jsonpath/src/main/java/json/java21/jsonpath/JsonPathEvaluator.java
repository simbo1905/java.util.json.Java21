package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonNull;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static json.java21.jsonpath.BoolExpr.CmpOp;
import static json.java21.jsonpath.JsonPathAst.Step;

sealed interface JsonPathEvaluator permits JsonPathEvaluator.Impl {

    static List<JsonValue> select(JsonPathAst ast, JsonValue document) {
        return Impl.select(ast, document);
    }

    final class Impl implements JsonPathEvaluator {
        static List<JsonValue> select(JsonPathAst ast, JsonValue document) {
            var current = List.of(document);
            for (final var step : ast.steps()) {
                current = apply(step, current);
            }
            return current;
        }

        private static List<JsonValue> apply(Step step, List<JsonValue> current) {
            return switch (step) {
                case Step.Child child -> selectChild(current, child.name());
                case Step.Wildcard ignored -> selectWildcard(current);
                case Step.RecursiveDescent rd -> selectRecursiveDescent(current, rd);
                case Step.ArrayIndex idx -> selectArrayIndex(current, idx.index());
                case Step.ArraySlice slice -> selectArraySlice(current, slice.startInclusive(), slice.endExclusive());
                case Step.Union union -> selectUnion(current, union.selectors());
                case Step.Filter filter -> selectFilter(current, filter.predicate());
                case Step.ScriptIndex script -> selectScriptIndex(current, script.expr());
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

        private static List<JsonValue> selectRecursiveDescent(List<JsonValue> current, Step.RecursiveDescent rd) {
            final var out = new ArrayList<JsonValue>();
            for (final var node : current) {
                final Deque<JsonValue> stack = new ArrayDeque<>();
                stack.push(node);

                while (!stack.isEmpty()) {
                    final var v = stack.pop();
                    out.addAll(applyRecursiveSelector(rd.selector(), v));
                    pushChildrenReversed(stack, v);
                }
            }
            return List.copyOf(out);
        }

        private static List<JsonValue> applyRecursiveSelector(Step.RecursiveSelector selector, JsonValue node) {
            return switch (selector) {
                case Step.RecursiveSelector.Name name -> {
                    if (node instanceof JsonObject obj) {
                        final var val = obj.members().get(name.name());
                        if (val == null) yield List.of();
                        yield List.of(val);
                    }
                    yield List.of();
                }
                case Step.RecursiveSelector.Wildcard ignored -> {
                    if (node instanceof JsonObject obj) {
                        yield List.copyOf(obj.members().values());
                    }
                    if (node instanceof JsonArray arr) {
                        yield arr.elements();
                    }
                    yield List.of();
                }
            };
        }

        private static void pushChildrenReversed(Deque<JsonValue> stack, JsonValue node) {
            switch (node) {
                case JsonObject obj -> {
                    final var values = obj.members().values().toArray(JsonValue[]::new);
                    for (int i = values.length - 1; i >= 0; i--) {
                        stack.push(values[i]);
                    }
                }
                case JsonArray arr -> {
                    final var values = arr.elements();
                    for (int i = values.size() - 1; i >= 0; i--) {
                        stack.push(values.get(i));
                    }
                }
                default -> {
                }
            }
        }

        private static List<JsonValue> selectArrayIndex(List<JsonValue> current, int index) {
            final var out = new ArrayList<JsonValue>();
            for (final var node : current) {
                if (node instanceof JsonArray arr) {
                    final int idx = normalizeIndex(index, arr.elements().size());
                    if (idx >= 0 && idx < arr.elements().size()) {
                        out.add(arr.elements().get(idx));
                    }
                }
            }
            return List.copyOf(out);
        }

        private static List<JsonValue> selectArraySlice(List<JsonValue> current, Integer startInclusive, Integer endExclusive) {
            final var out = new ArrayList<JsonValue>();
            for (final var node : current) {
                if (node instanceof JsonArray arr) {
                    final int len = arr.elements().size();
                    int start = startInclusive == null ? 0 : normalizeIndex(startInclusive, len);
                    int end = endExclusive == null ? len : normalizeIndex(endExclusive, len);

                    if (start < 0) start = 0;
                    if (end < 0) end = 0;
                    if (start > len) start = len;
                    if (end > len) end = len;

                    for (int i = start; i < end; i++) {
                        out.add(arr.elements().get(i));
                    }
                }
            }
            return List.copyOf(out);
        }

        private static int normalizeIndex(int index, int len) {
            return index < 0 ? len + index : index;
        }

        private static List<JsonValue> selectUnion(List<JsonValue> current, List<Step.Selector> selectors) {
            final var out = new ArrayList<JsonValue>();
            for (final var node : current) {
                switch (node) {
                    case JsonObject obj -> selectors.forEach(sel -> {
                        if (sel instanceof Step.Selector.Name n) {
                            final var v = obj.members().get(n.name());
                            if (v != null) out.add(v);
                        }
                    });
                    case JsonArray arr -> selectors.forEach(sel -> {
                        if (sel instanceof Step.Selector.Index idx) {
                            final int i = normalizeIndex(idx.index(), arr.elements().size());
                            if (i >= 0 && i < arr.elements().size()) {
                                out.add(arr.elements().get(i));
                            }
                        }
                    });
                    default -> {
                    }
                }
            }
            return List.copyOf(out);
        }

        private static List<JsonValue> selectFilter(List<JsonValue> current, BoolExpr predicate) {
            final var out = new ArrayList<JsonValue>();
            for (final var node : current) {
                if (node instanceof JsonArray arr) {
                    for (final var elem : arr.elements()) {
                        if (evalPredicate(predicate, elem)) {
                            out.add(elem);
                        }
                    }
                }
            }
            return List.copyOf(out);
        }

        private static List<JsonValue> selectScriptIndex(List<JsonValue> current, ValueExpr expr) {
            final var out = new ArrayList<JsonValue>();
            for (final var node : current) {
                if (node instanceof JsonArray arr) {
                    final var value = evalValue(expr, arr);
                    final var number = toNumber(value);
                    if (number == null) continue;
                    final int idx = normalizeIndex((int) number.doubleValue(), arr.elements().size());
                    if (idx >= 0 && idx < arr.elements().size()) {
                        out.add(arr.elements().get(idx));
                    }
                }
            }
            return List.copyOf(out);
        }

        private static boolean evalPredicate(BoolExpr predicate, JsonValue at) {
            return switch (predicate) {
                case BoolExpr.Exists exists -> {
                    final var value = evalValue(exists.path(), at);
                    yield switch (value) {
                        case Value.Missing ignored -> false;
                        case Value.Json(var jv) -> !(jv instanceof JsonNull);
                        case Value.Num ignored -> true;
                        case Value.Str ignored -> true;
                    };
                }
                case BoolExpr.Compare cmp -> evalCompare(cmp.op(), cmp.left(), cmp.right(), at);
            };
        }

        private static boolean evalCompare(CmpOp op, ValueExpr left, ValueExpr right, JsonValue at) {
            final var lv = evalValue(left, at);
            final var rv = evalValue(right, at);

            return switch (op) {
                case LT, LE, GT, GE -> {
                    final var lnum = toNumber(lv);
                    final var rnum = toNumber(rv);
                    if (lnum == null || rnum == null) yield false;
                    final double a = lnum;
                    final double b = rnum;
                    yield switch (op) {
                        case LT -> a < b;
                        case LE -> a <= b;
                        case GT -> a > b;
                        case GE -> a >= b;
                        default -> throw new IllegalStateException("unexpected op: " + op);
                    };
                }
                case EQ, NE -> {
                    final boolean eq = equalsValue(lv, rv);
                    yield op == CmpOp.EQ ? eq : !eq;
                }
            };
        }

        private static boolean equalsValue(Value a, Value b) {
            return switch (a) {
                case Value.Missing ignored -> b instanceof Value.Missing;
                case Value.Num(var x) -> (b instanceof Value.Num n) && Double.compare(x, n.value()) == 0;
                case Value.Str(var x) -> (b instanceof Value.Str s) && x.equals(s.value());
                case Value.Json(var x) -> {
                    if (b instanceof Value.Json j) {
                        yield switch (x) {
                            case JsonString js -> (j.value() instanceof JsonString rhs) && js.string().equals(rhs.string());
                            case JsonNumber jn -> (j.value() instanceof JsonNumber rhs) && Double.compare(jn.toDouble(), rhs.toDouble()) == 0;
                            default -> x.equals(j.value());
                        };
                    }
                    yield false;
                }
            };
        }

        private static Value evalValue(ValueExpr expr, JsonValue at) {
            return switch (expr) {
                case ValueExpr.Num n -> new Value.Num(n.value());
                case ValueExpr.Str s -> new Value.Str(s.value());
                case ValueExpr.Path p -> evalPath(p.parts(), at);
                case ValueExpr.Arith arith -> {
                    final var l = toNumber(evalValue(arith.left(), at));
                    final var r = toNumber(evalValue(arith.right(), at));
                    if (l == null || r == null) yield new Value.Missing();
                    yield new Value.Num(arith.op() == ValueExpr.ArithOp.ADD ? l + r : l - r);
                }
            };
        }

        private static Value evalPath(List<String> parts, JsonValue at) {
            if (parts.size() == 1 && "length".equals(parts.getFirst()) && at instanceof JsonArray arr) {
                return new Value.Num(arr.elements().size());
            }
            JsonValue cur = at;
            for (final var part : parts) {
                if (cur instanceof JsonObject obj) {
                    final var next = obj.members().get(part);
                    if (next == null) return new Value.Missing();
                    cur = next;
                } else {
                    return new Value.Missing();
                }
            }
            return new Value.Json(cur);
        }

        private static Double toNumber(Value v) {
            return switch (v) {
                case Value.Num n -> n.value();
                case Value.Str ignored -> null;
                case Value.Missing ignored -> null;
                case Value.Json(var jv) -> switch (jv) {
                    case JsonNumber n -> n.toDouble();
                    case JsonString s -> {
                        try {
                            yield Double.parseDouble(s.string());
                        } catch (NumberFormatException ex) {
                            yield null;
                        }
                    }
                    default -> null;
                };
            };
        }

        private sealed interface Value permits Value.Num, Value.Str, Value.Json, Value.Missing {
            record Num(double value) implements Value {}

            record Str(String value) implements Value {}

            record Json(JsonValue value) implements Value {}

            record Missing() implements Value {}
        }
    }
}

