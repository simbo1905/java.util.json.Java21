package json.java21.jsonpath;

import java.util.List;
import java.util.Objects;

record JsonPathAst(List<Step> steps) {
    JsonPathAst {
        Objects.requireNonNull(steps, "steps must not be null");
        steps.forEach(s -> Objects.requireNonNull(s, "step must not be null"));
    }

    sealed interface Step permits
            Step.Child,
            Step.Wildcard,
            Step.RecursiveDescent,
            Step.ArrayIndex,
            Step.ArraySlice,
            Step.Union,
            Step.Filter,
            Step.ScriptIndex {

        record Child(String name) implements Step {
            public Child {
                Objects.requireNonNull(name, "name must not be null");
            }
        }

        record Wildcard() implements Step {}

        record RecursiveDescent(RecursiveSelector selector) implements Step {
            public RecursiveDescent {
                Objects.requireNonNull(selector, "selector must not be null");
            }
        }

        sealed interface RecursiveSelector permits RecursiveSelector.Name, RecursiveSelector.Wildcard {
            record Name(String name) implements RecursiveSelector {
                public Name {
                    Objects.requireNonNull(name, "name must not be null");
                }
            }

            record Wildcard() implements RecursiveSelector {}
        }

        record ArrayIndex(int index) implements Step {}

        record ArraySlice(Integer startInclusive, Integer endExclusive) implements Step {}

        record Union(List<Selector> selectors) implements Step {
            public Union {
                Objects.requireNonNull(selectors, "selectors must not be null");
                selectors.forEach(s -> Objects.requireNonNull(s, "selector must not be null"));
            }
        }

        sealed interface Selector permits Selector.Name, Selector.Index {
            record Name(String name) implements Selector {
                public Name {
                    Objects.requireNonNull(name, "name must not be null");
                }
            }

            record Index(int index) implements Selector {}
        }

        record Filter(BoolExpr predicate) implements Step {
            public Filter {
                Objects.requireNonNull(predicate, "predicate must not be null");
            }
        }

        record ScriptIndex(ValueExpr expr) implements Step {
            public ScriptIndex {
                Objects.requireNonNull(expr, "expr must not be null");
            }
        }
    }
}

sealed interface BoolExpr permits BoolExpr.Exists, BoolExpr.Compare {
    record Exists(ValueExpr.Path path) implements BoolExpr {
        public Exists {
            Objects.requireNonNull(path, "path must not be null");
        }
    }

    enum CmpOp {
        LT, LE, GT, GE, EQ, NE
    }

    record Compare(CmpOp op, ValueExpr left, ValueExpr right) implements BoolExpr {
        public Compare {
            Objects.requireNonNull(op, "op must not be null");
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }
    }
}

sealed interface ValueExpr permits ValueExpr.Path, ValueExpr.Num, ValueExpr.Str, ValueExpr.Arith {

    record Path(List<String> parts) implements ValueExpr {
        public Path {
            Objects.requireNonNull(parts, "parts must not be null");
            parts.forEach(p -> Objects.requireNonNull(p, "path part must not be null"));
        }
    }

    record Num(double value) implements ValueExpr {}

    record Str(String value) implements ValueExpr {
        public Str {
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    enum ArithOp {
        ADD, SUB
    }

    record Arith(ArithOp op, ValueExpr left, ValueExpr right) implements ValueExpr {
        public Arith {
            Objects.requireNonNull(op, "op must not be null");
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }
    }
}

