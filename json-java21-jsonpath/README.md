# JsonPath

This module provides a JSONPath-style query engine for JSON documents parsed with `jdk.incubator.java.util.json`.

It is based on the original Stefan Goessner JSONPath article:
https://goessner.net/articles/JsonPath/

## Quick Start

```java
import jdk.incubator.java.util.json.*;
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

## Two Engines: Interpreted and Compiled

This module ships the same JsonPath language with two interchangeable engines behind one API:

| Engine | Class | Availability | Mechanism |
|---|---|---|---|
| Interpreted | `JsonPath.parse(...)` result | JDK 21+ (default) | walks the `JsonPathAst` tree per query |
| Compiled | `JsonPathCodegen.compile(...)` result | JDK 24+ runtime | bytecode generated with the JDK 24 ClassFile API (JEP 484), targeting Java 21 bytecode (`ClassFile.JAVA_21_VERSION`, class file version 65) |

Both engines implement the same `JsonPath` public surface, so callers can hand either one to the same API. The compiled engine mirrors the approach already shipped for JTD validation in [`json-java21-jtd-codegen`](../json-java21-jtd-codegen/README.md) (`JtdCodegen` / `JtdValidator.compileGenerated`).

### Using the Compiled Engine

```java
// Parse once, compile for the best repeated-query performance (JDK 24+)
JsonPath path = JsonPathCodegen.compile(JsonPath.parse("$.store.book[*].author"));

for (JsonValue doc : documents) {
    List<JsonValue> results = path.query(doc);
    // process results...
}
```

`JsonPathCodegen.compile(...)` mirrors `JtdValidator.compileGenerated(...)`: it accepts any `JsonPath`, returns a `JsonPath` implementing the same public surface, and is idempotent (compiling a compiled path returns it unchanged).

The compiled engine keeps the original path text (`toString()`) and the AST (`ast()`), so a compiled path is a drop-in replacement anywhere an interpreted path is accepted.

### Runtime Requirements

- **Interpreted engine**: JDK 21+; no dependency on the ClassFile API; unchanged semantics.
- **Compiled engine**: generating classes requires a JDK 24+ runtime (`java.lang.classfile`, JEP 484) because the generator is compiled into the artifact only on JDK 24+. The generated classes are Java 21 bytecode and only need Java 21 API surface at query time.
- **Ahead-of-time (build-time) tooling**: `JsonPathCodegen.compileBytes(...)` returns the raw generated classfile bytes without loading them, in the same style as `JtdCodegen.compileBytes(...)` for `jtd2jar`-style standalone JAR builds. Generated executor classes are always emitted into the `json.java21.jsonpath` package so they can implement the package-private executor interface.
- **Diagnostics**: semantics and result order are identical between the engines. One deliberate, exact difference: when an expression carries an unsupported recursive-descent target or union selector, the interpreted engine logs it at `FINER` for every visited node, while the compiled engine logs it once at compile time; unsupported script expressions log `WARNING` at query time in both engines.

### Parity Guarantee

Compiled and interpreted engines produce identical results, in the same order, for every expression in the supported-syntax table above. The module's parity test suite (`JsonPathCompilerParityTest`) reuses the existing test corpus and asserts `compiled.query(doc)` equals `interpreted.query(doc)` element-for-element for both engines.

### Supported Features in Compiled Mode

All supported operators are supported by the compiled engine:
- property access (dot and bracket), array indices (including negative), slices
- wildcards, recursive descent (`$..name`, `$..*`), unions, filters with comparisons and logical operators, and the limited `[(@.length-1)]` script expression

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
