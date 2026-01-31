package json.java21.jsonpath;

import java.util.List;
import java.util.Objects;

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

