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

## Testing

```bash
./mvnw test -pl json-java21-jsonpath -am -Djava.util.logging.ConsoleHandler.level=INFO
```

```bash
./mvnw test -pl json-java21-jsonpath -am -Dtest=JsonPathGoessnerTest -Djava.util.logging.ConsoleHandler.level=FINE
```
