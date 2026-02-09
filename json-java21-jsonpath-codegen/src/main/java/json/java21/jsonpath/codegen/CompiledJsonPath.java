package json.java21.jsonpath.codegen;

import jdk.sandbox.java.util.json.JsonValue;
import java.util.List;

/// A compiled JsonPath query that executes against a JSON document.
///
/// Generated implementations contain bytecode-inlined evaluation logic
/// with no interpretation overhead. The generated classfiles target Java 21.
public interface CompiledJsonPath {

    /// Evaluates this JsonPath expression against the given JSON document.
    ///
    /// @param root the root JSON document to query
    /// @return a list of matched JSON values (never null, may be empty)
    List<JsonValue> query(JsonValue root);

    /// Returns the original JsonPath expression string.
    ///
    /// @return the JsonPath expression this was compiled from
    @Override
    String toString();
}
