package json.java21.jsonpath;

import java.util.List;
import java.util.Objects;

record JsonPathAst(List<Segment> segments) {
    JsonPathAst {
        Objects.requireNonNull(segments, "segments must not be null");
        segments.forEach(s -> Objects.requireNonNull(s, "segment must not be null"));
    }

    sealed interface Segment permits Segment.Child, Segment.Wildcard {
        record Child(String name) implements Segment {
            public Child {
                Objects.requireNonNull(name, "name must not be null");
            }
        }

        record Wildcard() implements Segment {}
    }
}

