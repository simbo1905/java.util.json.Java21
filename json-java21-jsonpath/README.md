# JsonPath (Goessner) for `java.util.json` (Java 21)

This module implements **JsonPath** as described by Stefan Goessner:
`https://goessner.net/articles/JsonPath/index.html`.

Design constraints:
- Evaluates over already-parsed JSON (`jdk.sandbox.java.util.json.JsonValue`)
- Parses JsonPath strings into a custom AST
- No runtime dependencies outside `java.base` (and the core `java.util.json` backport)

## Usage (planned API)

```java
import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import json.java21.jsonpath.JsonPath;

JsonValue doc = Json.parse("{\"a\": {\"b\": [1,2,3]}}");
var expr = JsonPath.parse("$.a.b[0]");
var matches = expr.select(doc);
```

