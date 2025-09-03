# java.util.json Backport for JDK 21

Early access to the unstable `java.util.json` API - taken from OpenJDK sandbox July 2025.

## Quick Start

### Parsing JSON to Maps and Objects

```java
// Parse JSON string to generic structure
String json = "{\"name\":\"Alice\",\"age\":30,\"active\":true}";
JsonValue value = Json.parse(json);

// Access as map-like structure
JsonObject obj = (JsonObject) value;
String name = ((JsonString) obj.members().get("name")).value();
int age = ((JsonNumber) obj.members().get("age")).intValue();
boolean active = ((JsonBoolean) obj.members().get("active")).value();
```

### Record Mapping

```java
// Define records for structured data
record User(String name, int age, boolean active) {}

// Parse JSON directly to records
String userJson = "{\"name\":\"Bob\",\"age\":25,\"active\":false}";
JsonObject jsonObj = (JsonObject) Json.parse(userJson);

// Map to record
User user = new User(
    ((JsonString) jsonObj.members().get("name")).value(),
    ((JsonNumber) jsonObj.members().get("age")).intValue(),
    ((JsonBoolean) jsonObj.members().get("active")).value()
);

// Convert records back to JSON
JsonValue backToJson = Json.fromUntyped(Map.of(
    "name", user.name(),
    "age", user.age(),
    "active", user.active()
));
```

## Back Port Project Goals

- **✅Enable early adoption**: Let developers try the unstable Java JSON patterns today on JDK 21+
- **✅API compatibility over performance**: Focus on matching the emerging "batteries included" API design rather than competing with existing JSON libraries on speed. 
- **✅Track upstream API**: Match emerging API updates to be a potential "unofficial backport" if a final official solution ever lands. 
- **✅Host Examples / Counter Examples**: Only if there is community interest. 

## Non-Goals

- **🛑Performance competition**: This backport is not intended to be the fastest JSON library. The JDK internal annotations that boost performance had to be removed. 
- **🛑Feature additions**: No features beyond what's in the experimental upstream branches. Contributions of example code or internal improvements are welcome. 
- **🛑Production / API stability**: Its an unstable API. It is currently only for educational or experimenal usage. 
- **🛑Advoocacy / Counter Advocacy**: This repo is not an endorsement of the proposed API nor a rejection of other solutions. Please only use the official Java email lists to debate the API or the general topic.

## Current Status

This code (as at July 2025) is derived from the official OpenJDK sandbox repository at commit [d22dc2ba89789041c3908cdaafadc1dcf8882ebf](https://github.com/openjdk/jdk-sandbox/commit/d22dc2ba89789041c3908cdaafadc1dcf8882ebf) (Mid July 2025 "Improve hash code spec wording").

The original proposal and design rationale can be found in the included PDF: [Towards a JSON API for the JDK.pdf](Towards%20a%20JSON%20API%20for%20the%20JDK.pdf)

## Modifications

This is a simplified backport with the following changes from the original:
- Replaced StableValue with double-checked locking pattern.
- Removed value-based class annotations.
- Compatible with JDK 21.

## Security Considerations

**⚠️ This unstable API contains undocumented security vulnerabilities.** The compatibility test suite (documented below) includes crafted attack vectors that expose these issues:

- **Stack exhaustion attacks**: Deeply nested JSON structures can trigger `StackOverflowError`, potentially leaving applications in an undefined state and enabling denial-of-service attacks
- **API contract violations**: The `Json.parse()` method documentation only declares `JsonParseException` and `NullPointerException`, but malicious inputs can trigger undeclared exceptions

These vulnerabilities exist in the upstream OpenJDK sandbox implementation and are reported here for transparency.

## JSON Schema Validator

By including a basic schema validator good enough to validate simple JSON RPC 2.0 for MCP communication, this demonstrates how to build a realistic feature out of the core API. To demonstrate the power of the core API, it follows Data Oriented Programming principles: it parses JSON Schema into an immutable structure of records, then for validation it parses the JSON to the generic structure and uses the thread-safe parsed schema as the model to validate the JSON being checked.

A simple JSON Schema (2020-12 subset) validator is included (module: json-java21-schema).

```java
var schema = io.github.simbo1905.json.schema.JsonSchema.compile(
    jdk.sandbox.java.util.json.Json.parse("""
      {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
    """));
var result = schema.validate(
    jdk.sandbox.java.util.json.Json.parse("{\"name\":\"Alice\"}")
);
// result.valid() => true
```

Compatibility: we run the official JSON Schema Test Suite on verify; in strict mode it currently passes about 71% of applicable cases.

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

A powerful feature is mapping between Java records and JSON:

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

## JSON Test Suite Compatibility

This backport includes a compatibility report tool that tests against the [JSON Test Suite](https://github.com/nst/JSONTestSuite) to track conformance with JSON standards.

### Running the Compatibility Report

First, build the project and download the test suite:

```bash
# Build project and download test suite
mvn clean compile generate-test-resources -pl json-compatibility-suite

# Run human-readable report
mvn exec:java -pl json-compatibility-suite

# Run JSON output (dogfoods the API)
mvn exec:java -pl json-compatibility-suite -Dexec.args="--json"
```

### Current Status

The implementation achieves **99.3% overall conformance** with the JSON Test Suite:

- **Valid JSON**: 97.9% success rate (93/95 files pass)
- **Invalid JSON**: 100% success rate (correctly rejects all invalid JSON)
- **Implementation-defined**: Handles 35 edge cases per implementation choice (27 accepted, 8 rejected)

The 2 failing cases involve duplicate object keys, which this implementation rejects (stricter than required by the JSON specification). This is an implementation choice that prioritizes data integrity over permissiveness.

### Understanding the Results

- **Files skipped**: Currently 0 files skipped due to robust encoding detection that handles various character encodings
- **StackOverflowError**: Security vulnerability exposed by malicious deeply nested structures - can leave applications in undefined state  
- **Duplicate keys**: Implementation choice to reject for data integrity (2 files fail for this reason)

This tool reports status and is not a criticism of the expermeintal API which is not available for direct public use. This aligning with this project's goal of tracking upstream unstable development without advocacy. If you have opinions, good or bad, about anything you see here please use the official Java email lists to discuss. If you see a bug/mistake/improvement with this repo please raise an issue and ideally submit a PR.
