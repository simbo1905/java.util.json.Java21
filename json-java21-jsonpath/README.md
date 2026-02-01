# JsonPath

This module provides a JSONPath-style query engine for JSON documents parsed with `jdk.sandbox.java.util.json`.

It is based on the original Stefan Goessner JSONPath article:
https://goessner.net/articles/JsonPath/

## Usage

Parse JSON once with `Json.parse(...)`, compile the JsonPath once with `JsonPath.parse(...)`, then query multiple documents:

```java
import jdk.sandbox.java.util.json.*;
import json.java21.jsonpath.JsonPath;

JsonValue doc = Json.parse("""
  {"store": {"book": [{"author": "A"}, {"author": "B"}]}}
  """);

JsonPath path = JsonPath.parse("$.store.book[*].author");
var authors = path.query(doc);

// If you want a static call site:
var sameAuthors = JsonPath.query(path, doc);
```

Notes:
- Prefer `JsonPath.parse(String)` + `query(JsonValue)` to avoid repeatedly parsing the same path.
- `JsonPath.query(String, JsonValue)` is intended for one-off usage.

## Supported Syntax

This implementation follows Goessner-style JSONPath operators, including:
- `$` root
- `.name` / `['name']` property access
- `[n]` array index (including negative indices)
- `[start:end:step]` slices
- `*` wildcards
- `..` recursive descent
- `[n,m]` and `['a','b']` unions
- `[?(@.prop)]` and `[?(@.prop op value)]` basic filters
- `[(@.length-1)]` limited script support

## Stream-Based Functions (Aggregations)

Some JsonPath implementations for older versions of Java provided aggregation functions such as `$.numbers.avg()`.
In this implementation we provide first class stream support so you can use standard JDK aggregation functions on `JsonPath.query(...)` results.

The `query()` method returns a standard `List<JsonValue>`. You can stream, filter, map, and reduce these results using standard Java APIs. To make this easier, we provide the `JsonPathStreams` utility class with predicate and conversion methods.

### Strict vs. Lax Conversions

We follow a pattern of "Strict" (`asX`) vs "Lax" (`asXOrNull`) converters:
- **Strict (`asX`)**: Throws `ClassCastException` (or similar) if the value is not the expected type. Use this when you are certain of the schema.
- **Lax (`asXOrNull`)**: Returns `null` if the value is not the expected type. Use this with `.filter(Objects::nonNull)` for robust processing of messy data.

### Examples

**Summing Numbers (Lax - safe against bad data)**
```java
import json.java21.jsonpath.JsonPathStreams;
import java.util.Objects;

// Calculate sum of all 'price' fields, ignoring non-numbers
double total = path.query(doc).stream()
    .map(JsonPathStreams::asDoubleOrNull) // Convert to Double or null
    .filter(Objects::nonNull)             // Remove non-numbers
    .mapToDouble(Double::doubleValue)     // Unbox
    .sum();
```

**Average (Strict - expects valid data)**
```java
import java.util.OptionalDouble;

// Calculate average, fails if any value is not a number
OptionalDouble avg = path.query(doc).stream()
    .map(JsonPathStreams::asDouble)       // Throws if not a number
    .mapToDouble(Double::doubleValue)
    .average();
```

**Filtering by Type**
```java
import java.util.List;

// Get all strings
List<String> strings = path.query(doc).stream()
    .filter(JsonPathStreams::isString)
    .map(JsonPathStreams::asString)
    .toList();
```

### Available Helpers (`JsonPathStreams`)

**Predicates:**
- `isNumber(JsonValue)`
- `isString(JsonValue)`
- `isBoolean(JsonValue)`
- `isArray(JsonValue)`
- `isObject(JsonValue)`
- `isNull(JsonValue)`

**Converters (Strict):**
- `asDouble(JsonValue)` -> `double`
- `asLong(JsonValue)` -> `long`
- `asString(JsonValue)` -> `String`
- `asBoolean(JsonValue)` -> `boolean`

**Converters (Lax):**
- `asDoubleOrNull(JsonValue)` -> `Double`
- `asLongOrNull(JsonValue)` -> `Long`
- `asStringOrNull(JsonValue)` -> `String`
- `asBooleanOrNull(JsonValue)` -> `Boolean`

## Testing

```bash
./mvnw test -pl json-java21-jsonpath -am -Djava.util.logging.ConsoleHandler.level=INFO
```

```bash
./mvnw test -pl json-java21-jsonpath -am -Dtest=JsonPathGoessnerTest -Djava.util.logging.ConsoleHandler.level=FINE
```
