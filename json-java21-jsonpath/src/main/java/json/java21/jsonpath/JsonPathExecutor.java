package json.java21.jsonpath;

import jdk.incubator.java.util.json.JsonValue;

import java.util.List;

/// Functional interface implemented by the bytecode-generated JsonPath executors.
///
/// The compiled engine's generated class (emitted by `JsonPathCodegen` with the JDK 24+
/// ClassFile API targeting Java 21 bytecode) implements this interface. It is package-private
/// by design: generated classes are always defined in the `json.java21.jsonpath` runtime
/// package, so the interface can stay internal to the module.
@FunctionalInterface
interface JsonPathExecutor {

    /// Executes the compiled JsonPath query against a JSON document.
    /// @param current the current node being evaluated
    /// @param root the root document (for `$` references)
    /// @return a list of matching JsonValue instances (maybe empty)
    List<JsonValue> execute(JsonValue current, JsonValue root);
}
