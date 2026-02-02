package json.java21.jsonpath;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;
import java.util.Objects;

/// A single JsonPath match: the matched value plus its location.
///
/// The `location` is a list of steps from the queried root value to the match.
/// The empty list indicates the root value itself.
public record JsonPathMatch(List<JsonPathLocationStep> location, JsonValue value) {
    public JsonPathMatch {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(value, "value must not be null");
        location = List.copyOf(location);
    }
}

