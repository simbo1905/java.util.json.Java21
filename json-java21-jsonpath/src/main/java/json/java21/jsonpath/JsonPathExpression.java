package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;
import java.util.Objects;

/// A compiled JsonPath expression (AST + evaluator).
public record JsonPathExpression(JsonPathAst ast) {

    public JsonPathExpression {
        Objects.requireNonNull(ast, "ast must not be null");
    }

    public List<JsonValue> select(JsonValue document) {
        Objects.requireNonNull(document, "document must not be null");
        return JsonPathEvaluator.select(ast, document);
    }
}

