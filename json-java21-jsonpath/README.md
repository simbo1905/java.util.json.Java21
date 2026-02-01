# JsonPath

This module provides a JSONPath-style query engine for JSON documents parsed with `jdk.sandbox.java.util.json`.

It is based on the original Stefan Goessner JSONPath article:
https://goessner.net/articles/JsonPath/

## Quick Start

```java
import jdk.sandbox.java.util.json.*;
import json.java21.jsonpath.JsonPath;

JsonValue doc = Json.parse("""
  {"store": {"book": [{"title": "A", "price": 8.95}, {"title": "B", "price": 12.99}]}}
  """);

var titles = JsonPath.parse("$.store.book[*].title").query(doc);
var cheap = JsonPath.parse("$.store.book[?(@.price < 10)].title").query(doc);
```

## Syntax At A Glance

Operator | Example | What it selects
---|---|---
root | `$` | the whole document
property | `$.store.book` | a nested object property
bracket property | `$['store']['book']` | same as dot notation, but allows escaping
wildcard | `$.store.*` | all direct children
recursive descent | `$..price` | any matching member anywhere under the document
array index | `$.store.book[0]` / `[-1]` | element by index (negative from end)
slice | `$.store.book[:2]` / `[0:4:2]` / `[::-1]` | slice by start:end:step
union | `$.store['book','bicycle']` / `[0,1]` | select multiple names/indices
filter exists | `$.store.book[?(@.isbn)]` | elements where a member exists
filter compare | `$.store.book[?(@.price < 10)]` | elements matching a comparison
filter logic | `$.store.book[?(@.isbn && (@.price < 10 || @.price > 20))]` | compound boolean logic
script (limited) | `$.store.book[(@.length-1)]` | last element via `length-1`

## Examples

Expression | What it selects
---|---
`$.store.book[*].title` | all book titles
`$.store.book[?(@.price < 10)].title` | titles of books cheaper than 10
`$.store.book[?(@.isbn && (@.price < 10 || @.price > 20))].title` | books with an ISBN and price outside the mid-range
`$..price` | every `price` anywhere under the document
`$.store.book[-1]` | the last book
`$.store.book[0:4:2]` | every other book from the first four

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

Some JsonPath implementations include aggregation functions such as `$.numbers.avg()`.
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
