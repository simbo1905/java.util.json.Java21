package json.java21.jsonpath;

import java.util.Objects;

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

