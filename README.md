# java.util.json Backport for JDK 21+

Early access to the future `java.util.json` API - taken from OpenJDK sandbox July 2025.

## Project Vision

This project provides Java developers with early access to the future `java.util.json` API patterns today, allowing code written against this API to migrate seamlessly when the official API is released. Rather than adopting third-party JSON libraries that will never align with future JDK standards, developers can start using tomorrow's API patterns today.

## Current Status

This code is derived from the official OpenJDK sandbox repository at commit [d22dc2ba89789041c3908cdaafadc1dcf8882ebf](https://github.com/openjdk/jdk-sandbox/commit/d22dc2ba89789041c3908cdaafadc1dcf8882ebf) (Mid July 2025 "Improve hash code spec wording").

The original proposal and design rationale can be found in the included PDF: [Towards a JSON API for the JDK.pdf](Towards%20a%20JSON%20API%20for%20the%20JDK.pdf)

## Back Port Project Goals

- **Enable early adoption**: Let developers use future Java JSON patterns today on JDK 21+
- **Smooth migration path**: Code written against this API should require less changes when migrating to the eventual official release
- **API compatibility over performance**: Focus on matching the emerging "batteries included" API design rather than competing with existing JSON libraries on speed

## Non-Goals

- **Performance competition**: This is not intended to be the fastest JSON library the JDK internal annotations had to be removed. 
- **Feature additions**: No features beyond what's in the official sandbox/preview. 
- **Production optimization**: The final official implementation will have JVM-level optimizations unavailable to a backport on Java 21.
- **API stability**: This backport may evolve as the official specification develops (only if folks find it useful)

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

## Usage Examples

### Record Mapping

The most powerful feature is mapping between Java records and JSON:

```java
// Domain model using records
record User(String name, String email, boolean active) {}
record Team(String teamName, List<User> members) {}

// Create a team with users
Team team = new Team("Engineering", List.of(
    new User("Alice", "alice@example.com", true),
    new User("Bob", "bob@example.com", false)
));

// Convert records to JSON
JsonValue teamJson = Json.fromUntyped(Map.of(
    "teamName", team.teamName(),
    "members", team.members().stream()
        .map(u -> Map.of(
            "name", u.name(),
            "email", u.email(),
            "active", u.active()
        ))
        .toList()
));

// Parse JSON back to records
JsonObject parsed = (JsonObject) Json.parse(teamJson.toString());
Team reconstructed = new Team(
    ((JsonString) parsed.members().get("teamName")).value(),
    ((JsonArray) parsed.members().get("members")).values().stream()
        .map(v -> {
            JsonObject member = (JsonObject) v;
            return new User(
                ((JsonString) member.members().get("name")).value(),
                ((JsonString) member.members().get("email")).value(),
                ((JsonBoolean) member.members().get("active")).value()
            );
        })
        .toList()
);
```

### Building Complex JSON

Create structured JSON programmatically:

```java
// Building a REST API response
JsonObject response = JsonObject.of(Map.of(
    "status", JsonString.of("success"),
    "data", JsonObject.of(Map.of(
        "user", JsonObject.of(Map.of(
            "id", JsonNumber.of(12345),
            "name", JsonString.of("John Doe"),
            "roles", JsonArray.of(List.of(
                JsonString.of("admin"),
                JsonString.of("user")
            ))
        )),
        "timestamp", JsonNumber.of(System.currentTimeMillis())
    )),
    "errors", JsonArray.of(List.of())
));
```

### Stream Processing

Process JSON arrays efficiently with Java streams:

```java
// Filter active users from a JSON array
JsonArray users = (JsonArray) Json.parse(jsonArrayString);
List<String> activeUserEmails = users.values().stream()
    .map(v -> (JsonObject) v)
    .filter(obj -> ((JsonBoolean) obj.members().get("active")).value())
    .map(obj -> ((JsonString) obj.members().get("email")).value())
    .toList();
```

### Error Handling

Handle parsing errors gracefully:

```java
try {
    JsonValue value = Json.parse(userInput);
    // Process valid JSON
} catch (JsonParseException e) {
    // Handle malformed JSON with line/column information
    System.err.println("Invalid JSON at line " + e.getLine() + 
                       ", column " + e.getColumn() + ": " + e.getMessage());
}
```

### Pretty Printing

Format JSON for display:

```java
JsonObject data = JsonObject.of(Map.of(
    "name", JsonString.of("Alice"),
    "scores", JsonArray.of(List.of(
        JsonNumber.of(85),
        JsonNumber.of(90),
        JsonNumber.of(95)
    ))
));

String formatted = Json.toDisplayString(data, 2);
// Output:
// {
//   "name": "Alice",
//   "scores": [
//     85,
//     90,
//     95
//   ]
// }
```
