package json.java21.jsonpath;

import java.util.List;
import java.util.Objects;

/// AST representation for JsonPath expressions.
/// Based on the JSONPath specification from https://goessner.net/articles/JsonPath/
///
/// A JsonPath expression is a sequence of path segments starting from root ($).
/// Each segment can be:
/// - PropertyAccess: access a named property (e.g., .store or ['store'])
/// - ArrayIndex: access array element by index (e.g., [0] or [-1])
/// - ArraySlice: slice array with start:end:step (e.g., [0:2] or [::2])
/// - Wildcard: match all children (e.g., .* or [*])
/// - RecursiveDescent: search all descendants (e.g., ..author)
/// - Filter: filter by predicate (e.g., [?(@.isbn)] or [?(@.price<10)])
/// - Union: multiple indices or names (e.g., [0,1] or ['a','b'])
/// - ScriptExpression: computed index (e.g., [(@.length-1)])
sealed interface JsonPathAst {

    /// Root element ($) - the starting point of all JsonPath expressions
    record Root(List<Segment> segments) implements JsonPathAst {
        public Root {
            Objects.requireNonNull(segments, "segments must not be null");
            segments = List.copyOf(segments); // defensive copy
        }
    }

    /// A single segment in a JsonPath expression
    sealed interface Segment permits
            PropertyAccess,
            ArrayIndex,
            ArraySlice,
            Wildcard,
            RecursiveDescent,
            Filter,
            Union,
            ScriptExpression {}

    /// Access a named property: .name or ['name']
    record PropertyAccess(String name) implements Segment {
        public PropertyAccess {
            Objects.requireNonNull(name, "name must not be null");
        }
    }

    /// Access array element by index: [n] where n can be negative for reverse indexing
    record ArrayIndex(int index) implements Segment {}

    /// Slice array: [start:end:step]
    /// All fields are optional (null means not specified)
    record ArraySlice(Integer start, Integer end, Integer step) implements Segment {}

    /// Wildcard: * matches all children (both object properties and array elements)
    record Wildcard() implements Segment {}

    /// Recursive descent: .. searches all descendants
    /// The property field specifies what to search for (can be null for ..[*] or ..*)
    record RecursiveDescent(Segment target) implements Segment {
        public RecursiveDescent {
            Objects.requireNonNull(target, "target must not be null");
        }
    }

    /// Filter expression: [?(@.isbn)] or [?(@.price < 10)]
    record Filter(FilterExpression expression) implements Segment {
        public Filter {
            Objects.requireNonNull(expression, "expression must not be null");
        }
    }

    /// Union of multiple selectors: [0,1] or ['a','b']
    record Union(List<Segment> selectors) implements Segment {
        public Union {
            Objects.requireNonNull(selectors, "selectors must not be null");
            if (selectors.size() < 2) {
                throw new IllegalArgumentException("Union must have at least 2 selectors");
            }
            selectors = List.copyOf(selectors); // defensive copy
        }
    }

    /// Script expression for computed index: [(@.length-1)]
    record ScriptExpression(String script) implements Segment {
        public ScriptExpression {
            Objects.requireNonNull(script, "script must not be null");
        }
    }

    /// Filter expressions used in [?(...)] predicates
    sealed interface FilterExpression permits
            ExistsFilter,
            ComparisonFilter,
            LogicalFilter,
            CurrentNode,
            PropertyPath,
            LiteralValue {}

    /// Check if property exists: [?(@.isbn)]
    record ExistsFilter(PropertyPath path) implements FilterExpression {
        public ExistsFilter {
            Objects.requireNonNull(path, "path must not be null");
        }
    }

    /// Comparison filter: [?(@.price < 10)]
    record ComparisonFilter(
            FilterExpression left,
            ComparisonOp op,
            FilterExpression right
    ) implements FilterExpression {
        public ComparisonFilter {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(op, "op must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }
    }

    /// Logical combination of filters: &&, ||, !
    record LogicalFilter(
            FilterExpression left,
            LogicalOp op,
            FilterExpression right
    ) implements FilterExpression {
        public LogicalFilter {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(op, "op must not be null");
            // right can be null for NOT operator
        }
    }

    /// Current node reference: @ in filter expressions
    record CurrentNode() implements FilterExpression {}

    /// Property path in filter expressions: @.price or @.store.book
    record PropertyPath(List<String> properties) implements FilterExpression {
        public PropertyPath {
            Objects.requireNonNull(properties, "properties must not be null");
            if (properties.isEmpty()) {
                throw new IllegalArgumentException("PropertyPath must have at least one property");
            }
            properties = List.copyOf(properties); // defensive copy
        }
    }

    /// Literal value in filter expressions
    record LiteralValue(Object value) implements FilterExpression {
        // value can be null (for JSON null), String, Number, or Boolean
    }

    /// Comparison operators
    enum ComparisonOp {
        EQ("=="),      // equals
        NE("!="),      // not equals
        LT("<"),       // less than
        LE("<="),      // less than or equal
        GT(">"),       // greater than
        GE(">=");      // greater than or equal

        private final String symbol;

        ComparisonOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    /// Logical operators
    enum LogicalOp {
        AND("&&"),
        OR("||"),
        NOT("!");

        private final String symbol;

        LogicalOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }
}
