package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;
import java.util.Objects;

/// A `JsonPath` backed by a runtime-compiled generated class.
///
/// This wrapper intentionally does not retain the AST; it only retains the generated Java source
/// (for diagnostics/debugging) and delegates evaluation to the compiled instance.
final class JsonPathCompiledPath implements JsonPathCompiled {

    private final String expression;
    private final String generatedClassName;
    private final String javaSource;
    private final JsonPath delegate;

    JsonPathCompiledPath(String expression, String generatedClassName, String javaSource, JsonPath delegate) {
        this.expression = Objects.requireNonNull(expression, "expression must not be null");
        this.generatedClassName = Objects.requireNonNull(generatedClassName, "generatedClassName must not be null");
        this.javaSource = Objects.requireNonNull(javaSource, "javaSource must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public List<JsonValue> query(JsonValue json) {
        return delegate.query(json);
    }

    @Override
    public String toString() {
        return expression;
    }

    String generatedClassName() {
        return generatedClassName;
    }

    String javaSource() {
        return javaSource;
    }
}

