package json.java21.jsonpath;

import java.util.Objects;

/// A single step in a JsonPath match location.
///
/// Locations are expressed relative to the JSON value passed to `JsonPath.queryMatches(...)`.
public sealed interface JsonPathLocationStep permits JsonPathLocationStep.Property, JsonPathLocationStep.Index {

    /// Object member step (by property name).
    record Property(String name) implements JsonPathLocationStep {
        public Property {
            Objects.requireNonNull(name, "name must not be null");
        }
    }

    /// Array element step (by index).
    record Index(int index) implements JsonPathLocationStep {}
}

