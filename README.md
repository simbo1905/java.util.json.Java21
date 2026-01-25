# java.util.json – Backport from the OpenJDK sandbox

This is a backport of the `java.util.json` API from the OpenJDK jdk‑sandbox “json” branch for use on Java 21 and above. 

References:
- OpenJDK sandbox “json” branch: https://github.com/openjdk/jdk-sandbox/tree/json
- Design paper: [Towards a JSON API for the JDK.pdf](Towards%20a%20JSON%20API%20for%20the%20JDK.pdf)

This project is not an official release; APIs and behaviour may change as upstream evolves. 
You can find this code on [Maven Central](https://central.sonatype.com/artifact/io.github.simbo1905.json/java.util.json). 

To kick the tyres on the New JSON API this repo includes a JSON Type Definition (JTD) Validator implementing RFC 8927, released on Maven Central as part of this project.

We welcome contributions to the JTD Validator incubating within this repo. 

## Usage Examples

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

### Parsing JSON to Maps and Objects

```java
// Parse JSON string to generic structure
String json = "{\"name\":\"Alice\",\"age\":30,\"active\":true}";
JsonValue value = Json.parse(json);

// Access as map-like structure
JsonObject obj = (JsonObject) value;
String name = ((JsonString) obj.members().get("name")).value();
int age = ((JsonNumber) obj.members().get("age")).toNumber().intValue();
boolean active = ((JsonBoolean) obj.members().get("active")).value();
```

### Simple Record Mapping

```java
// Define records for structured data
record User(String name, int age, boolean active) {}

// Parse JSON directly to records
String userJson = "{\"name\":\"Bob\",\"age\":25,\"active\":false}";
JsonObject jsonObj = (JsonObject) Json.parse(userJson);

// Map to record
User user = new User(
    ((JsonString) jsonObj.members().get("name")).value(),
    ((JsonNumber) jsonObj.members().get("age")).toNumber().intValue(),
    ((JsonBoolean) jsonObj.members().get("active")).value()
);

// Convert records back to JSON
JsonValue backToJson = Json.fromUntyped(Map.of(
    "name", user.name(),
    "age", user.age(),
    "active", user.active()
));

// Covert back to a JSON string
String jsonString = backToJson.toString();
```

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

### Realistic Record Mapping

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

The test data is bundled as ZIP files and extracted automatically at runtime:

```bash
# Run human-readable report
mvn exec:java -pl json-compatibility-suite

# Run JSON output (dogfoods the API)
mvn exec:java -pl json-compatibility-suite -Dexec.args="--json"
```


## Backport Project Goals

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

This code (as of 2026-01-25) is derived from the OpenJDK jdk-sandbox repository "json" branch at commit [4de0eb4f0c867df2d420501cf6741e50dee142d9](https://github.com/openjdk/jdk-sandbox/commit/4de0eb4f0c867df2d420501cf6741e50dee142d9) ("initial integration into the JDK repository", 2024-10-10 UTC).

The original proposal and design rationale can be found in the included PDF: [Towards a JSON API for the JDK.pdf](Towards%20a%20JSON%20API%20for%20the%20JDK.pdf)

The JSON compatibility tests in this repo suggest 99.3% conformance with a leading test suite. The two conformance expectations that fail assume that duplicate keys in a JSON document are okay. The upstream code takes a strict stance that it should not silently ignore duplicate keys in a JSON object.

### CI: Upstream API Tracking

A daily workflow runs an API comparison against the OpenJDK sandbox and prints a JSON report. API drift is automatically detected and issues are created when differences are found, with fingerprint deduplication to avoid duplicate issues for the same drift.

## Modifications

This is a simplified backport with the following changes from the original:
- Replaced `StableValue.of()` with double-checked locking pattern.
- Removed `@ValueBased` annotations.
- Compatible with JDK 21.

## Security Considerations

**⚠️ This unstable API contains known security considerations.** The parser uses recursion internally which means:

- **Stack exhaustion attacks**: Deeply nested JSON structures can trigger `StackOverflowError`, potentially leaving applications in an undefined state and enabling denial-of-service attacks
- **API contract violations**: The `Json.parse()` method documentation only declares `JsonParseException` and `NullPointerException`, but malicious inputs can trigger undeclared exceptions

The upstream OpenJDK sandbox implementation uses a recursive descent parser. Until the upstream code is stable it is probably better to assume that such issues or similar may be present or may reappear. If you are only going to use this library in small CLI programs where the JSON is configuration you write, then you will not parse objects nested to tens of thousands of levels designed to crash a parser. However, you should not at this time expose this parser to the internet where someone can choose to attack it in that manner. 

## JSON Type Definition (JTD) Validator

This repo contains an incubating JTD validator that has the core JSON API as its only dependency. This sub-project demonstrates how to build realistic JSON heavy logic using the API. It follows Data Oriented Programming principles: it compiles JTD schemas into an immutable structure of records. For validation it parses the JSON document to the generic structure and uses the thread-safe parsed schema and a stack to visit and validate the parsed JSON.

A complete JSON Type Definition validator is included (module: json-java21-jtd).

### Empty Schema `{}` Semantics (RFC 8927)

Per **RFC 8927 (JSON Typedef)**, the empty schema `{}` is the **empty form** and
**accepts all JSON instances** (null, boolean, numbers, strings, arrays, objects).

> RFC 8927 §2.2 "Forms":  
> `schema = empty / ref / type / enum / elements / properties / values / discriminator / definitions`  
> `empty = {}`  
> **Empty form:** A schema in the empty form accepts all JSON values and produces no errors.

⚠️ Note: Some tools or in-house validators mistakenly interpret `{}` as "object with no
properties allowed." **That is not JTD.** This implementation follows RFC 8927 strictly.

```java
import json.java21.jtd.Jtd;
import jdk.sandbox.java.util.json.*;

// Compile JTD schema
JsonValue schema = Json.parse("""
  {
    "properties": {
      "name": {"type": "string"},
      "age": {"type": "int32"}
    }
  }
""");

// Validate JSON
JsonValue data = Json.parse("{\"name\":\"Alice\",\"age\":30}");
Jtd validator = new Jtd();
Jtd.Result result = validator.validate(schema, data);
// result.isValid() => true
```

### JTD RFC 8927 Compliance

The validator provides full RFC 8927 compliance with comprehensive test coverage:

```bash
# Run all JTD compliance tests
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Dtest=JtdSpecIT

# Run with detailed logging
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Djava.util.logging.ConsoleHandler.level=FINE
```

Features:
- ✅ Eight mutually-exclusive schema forms (RFC 8927 §2.2)
- ✅ Standardized error format with instance and schema paths
- ✅ Primitive type validation with proper ranges
- ✅ Definition support with reference resolution
- ✅ Timestamp format validation (RFC 3339 with leap seconds)
- ✅ Discriminator tag exemption from additional properties
- ✅ Stack-based validation preventing StackOverflowError

## Building

Requires JDK 21 or later. Build with Maven:

```bash
mvn clean compile
mvn package
```

Please see AGENTS.md for more guidance such as how to enable logging when running the JTD Validator. 

## Augmented Intelligence (AI) Welcomed

AI as **Augmented Intelligence** is most welcome here. Contributions that enhance *human + agent collaboration* are encouraged. If you want to suggest new agent‑workflows, prompt patterns, or improvements in tooling / validation / introspection, please submit amendments to **AGENTS.md** via standalone PRs. Your ideas make the difference.

When submitting Issues or PRs, please use a "deep research" tool to sanity check your proposal. Then **before** submission un your submission through a strong model with a prompt such as:

> "Please review the AGENTS.md and README.md along with this draft PR/Issue and check that it does not have any gaps and why it might be insufficient, incomplete, lacking a concrete example, duplicating prior issues or PRs, or not be aligned with the project goals or non‑goals."

Please attach the output of that model’s review to your Issue or PR.

## License

Licensed under the GNU General Public License version 2 with Classpath exception. See [LICENSE](LICENSE) for details.
