# JSON Experimental - JDK 24 Backport

This repository contains a backport of the experimental JSON API from the [jdk-sandbox project](https://github.com/openjdk/jdk-sandbox) to JDK 24.

## Origin

This code is derived from the official OpenJDK sandbox repository at commit [d22dc2ba89789041c3908cdaafadc1dcf8882ebf](https://github.com/openjdk/jdk-sandbox/commit/d22dc2ba89789041c3908cdaafadc1dcf8882ebf) ("Improve hash code spec wording").

The original proposal and design rationale can be found in the included PDF: [Towards a JSON API for the JDK.pdf](Towards%20a%20JSON%20API%20for%20the%20JDK.pdf)

## Modifications

This is a simplified backport with the following changes from the original:
- Removed StableValue optimizations
- Removed value-based class annotations  
- Basic implementation without performance optimizations
- Compatible with JDK 24 instead of future JDK versions

## Building

Requires JDK 24 (Early Access). Build with Maven:

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