# JSON Experimental - JDK 21+ Backport

This repository contains a backport of the experimental JSON API from the [jdk-sandbox project](https://github.com/openjdk/jdk-sandbox) to JDK 21 and later.

## Origin

This code is derived from the official OpenJDK sandbox repository at commit [d22dc2ba89789041c3908cdaafadc1dcf8882ebf](https://github.com/openjdk/jdk-sandbox/commit/d22dc2ba89789041c3908cdaafadc1dcf8882ebf) ("Improve hash code spec wording").

The original proposal and design rationale can be found in the included PDF: [Towards a JSON API for the JDK.pdf](Towards%20a%20JSON%20API%20for%20the%20JDK.pdf)

## Modifications

This is a simplified backport with the following changes from the original:
- Replaced StableValue optimizations with double-checked locking pattern
- Removed value-based class annotations  
- Basic implementation without advanced performance optimizations
- Compatible with JDK 21+ instead of future JDK versions

## Building

Requires JDK 21 or later. Build with Maven:

```bash
mvn clean compile
mvn package
```

## License

Licensed under the GNU General Public License version 2 with Classpath exception. See [LICENSE](LICENSE) for details.

## API Overview

The API provides immutable JSON value types:
- `JsonValue` - Base type for all JSON values
- `JsonObject` - JSON objects (key-value pairs)
- `JsonArray` - JSON arrays
- `JsonString` - JSON strings
- `JsonNumber` - JSON numbers
- `JsonBoolean` - JSON booleans (true/false)
- `JsonNull` - JSON null

Parsing is done via the `Json` class:
```java
JsonValue value = Json.parse(jsonString);
```

## Type Conversion Utilities

The `Json` class provides bidirectional conversion between `JsonValue` objects and standard Java types:

### Converting from Java Objects to JSON (`fromUntyped`)
```java
// Convert standard Java collections to JsonValue
Map<String, Object> data = Map.of(
    "name", "John",
    "age", 30,
    "scores", List.of(85, 92, 78)
);
JsonValue json = Json.fromUntyped(data);
```

### Converting from JSON to Java Objects (`toUntyped`)
```java
// Convert JsonValue back to standard Java types
JsonValue parsed = Json.parse("{\"name\":\"John\",\"age\":30}");
Object data = Json.toUntyped(parsed);
// Returns a Map<String, Object> with standard Java types
```

The conversion mappings are:
- `JsonObject` ↔ `Map<String, Object>`
- `JsonArray` ↔ `List<Object>`
- `JsonString` ↔ `String`
- `JsonNumber` ↔ `Number` (Long, Double, BigInteger, or BigDecimal)
- `JsonBoolean` ↔ `Boolean`
- `JsonNull` ↔ `null`

This is useful for:
- Integrating with existing code that uses standard collections
- Serializing/deserializing to formats that expect Java types
- Working with frameworks that use reflection on standard types