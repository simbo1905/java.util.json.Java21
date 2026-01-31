package json.java21.jsonpath;

import java.util.Objects;

/// JsonPath entry point: parse to an AST-backed expression.
public final class JsonPath {

    public static JsonPathExpression parse(String path) {
        Objects.requireNonNull(path, "path must not be null");
        return new JsonPathExpression(JsonPathParser.parse(path));
    }

    private JsonPath() {}
}

