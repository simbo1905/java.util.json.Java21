package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;

/// Functional interface for compiled JsonPath executors.
/// This interface is public to allow generated code to implement it.
@FunctionalInterface
public interface JsonPathExecutor {
    /// Executes the compiled JsonPath query against a JSON document.
    /// @param current the current node being evaluated
    /// @param root the root document (for $ references in filters)
    /// @return a list of matching JsonValue instances
    List<JsonValue> execute(JsonValue current, JsonValue root);
}
