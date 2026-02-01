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

### Running the Examples

To try the examples from this README, build the project and run the standalone example class:

```bash
./mvnw package
java -cp ./json-java21/target/java.util.json-*.jar:./json-java21/target/test-classes \
  jdk.sandbox.java.util.json.examples.ReadmeExamples
```

*Replace `*` with the actual version number from the JAR filename.*

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
String name = ((JsonString) obj.members().get("name")).string();
long age = ((JsonNumber) obj.members().get("age")).toLong();
boolean active = ((JsonBoolean) obj.members().get("active")).bool();
```

### Simple Record Mapping

```java
// Define records for structured data
record User(String name, long age, boolean active) {}

// Parse JSON directly to records
String userJson = "{\"name\":\"Bob\",\"age\":25,\"active\":false}";
JsonObject jsonObj = (JsonObject) Json.parse(userJson);

// Map to record
User user = new User(
    ((JsonString) jsonObj.members().get("name")).string(),
    ((JsonNumber) jsonObj.members().get("age")).toLong(),
    ((JsonBoolean) jsonObj.members().get("active")).bool()
);

// Convert records back to JSON using typed factories
JsonValue backToJson = JsonObject.of(Map.of(
    "name", JsonString.of(user.name()),
    "age", JsonNumber.of(user.age()),
    "active", JsonBoolean.of(user.active())
));

// Convert back to a JSON string
String jsonString = backToJson.toString();
```

### Building JSON Programmatically

```java
// Build JSON using typed factory methods
JsonObject data = JsonObject.of(Map.of(
    "name", JsonString.of("John"),
    "age", JsonNumber.of(30),
    "scores", JsonArray.of(List.of(
        JsonNumber.of(85),
        JsonNumber.of(92),
        JsonNumber.of(78)
    ))
));
String json = data.toString();
```

### Extracting Values from JSON

```java
// Extract values from parsed JSON
JsonValue parsed = Json.parse("{\"name\":\"John\",\"age\":30}");
JsonObject obj = (JsonObject) parsed;

// Use the new type-safe accessor methods
String name = obj.get("name").string();      // Returns "John"
long age = obj.get("age").toLong();          // Returns 30L
double ageDouble = obj.get("age").toDouble(); // Returns 30.0
```

The accessor methods on `JsonValue`:
- `string()` - Returns the String value (for JsonString)
- `toLong()` - Returns the long value (for JsonNumber, if representable)
- `toDouble()` - Returns the double value (for JsonNumber, if representable)
- `bool()` - Returns the boolean value (for JsonBoolean)
- `elements()` - Returns List<JsonValue> (for JsonArray)
- `members()` - Returns Map<String, JsonValue> (for JsonObject)
- `get(String name)` - Access JsonObject member by name
- `element(int index)` - Access JsonArray element by index

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

// Convert records to JSON using typed factories
JsonValue teamJson = JsonObject.of(Map.of(
    "teamName", JsonString.of(team.teamName()),
    "members", JsonArray.of(team.members().stream()
        .map(u -> JsonObject.of(Map.of(
            "name", JsonString.of(u.name()),
            "email", JsonString.of(u.email()),
            "active", JsonBoolean.of(u.active())
        )))
        .toList())
));

// Parse JSON back to records
JsonObject parsed = (JsonObject) Json.parse(teamJson.toString());
Team reconstructed = new Team(
    ((JsonString) parsed.members().get("teamName")).string(),
    ((JsonArray) parsed.members().get("members")).elements().stream()
        .map(v -> {
            JsonObject member = (JsonObject) v;
            return new User(
                ((JsonString) member.members().get("name")).string(),
                ((JsonString) member.members().get("email")).string(),
                ((JsonBoolean) member.members().get("active")).bool()
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
List<String> activeUserEmails = users.elements().stream()
    .map(v -> (JsonObject) v)
    .filter(obj -> ((JsonBoolean) obj.members().get("active")).bool())
    .map(obj -> ((JsonString) obj.members().get("email")).string())
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
./mvnw exec:java -pl json-compatibility-suite

# Run JSON output (dogfoods the API)
./mvnw exec:java -pl json-compatibility-suite -Dexec.args="--json"
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

This code (as of 2026-01-25) is derived from the OpenJDK jdk-sandbox repository "json" branch. Key API changes from the previous version include:
- `JsonString.value()` → `JsonString.string()`
- `JsonNumber.toNumber()` → `JsonNumber.toLong()` / `JsonNumber.toDouble()`
- `JsonBoolean.value()` → `JsonBoolean.bool()`
- `JsonArray.values()` → `JsonArray.elements()`
- `Json.fromUntyped()` and `Json.toUntyped()` have been removed
- New accessor methods on `JsonValue`: `get(String)`, `element(int)`, `getOrAbsent(String)`, `valueOrNull()`
- Internal implementation changed from `StableValue` to `LazyConstant`

The original proposal and design rationale can be found in the included PDF: [Towards a JSON API for the JDK.pdf](Towards%20a%20JSON%20API%20for%20the%20JDK.pdf)

The JSON compatibitlity tests in this repo suggest 99% conformance with a leading test suite when in "strict" mode. The two conformance expecatations that fail assume that duplicated keys in a JSON document are okay. The upstream code at this time appear to take a strict stance that it should not siliently ignore duplicate keys in a json object. 

### CI: Upstream API Tracking

A daily workflow runs an API comparison against the OpenJDK sandbox and prints a JSON report. Implication: differences do not currently fail the build or auto‑open issues; check the workflow logs (or adjust the workflow to fail on diffs) if you need notifications.

## Modifications

This is a simplified backport with the following changes from the original:
- Replaced `LazyConstant` with a package-local polyfill using double-checked locking pattern.
- Added `Utils.powExact()` polyfill for `Math.powExact(long, int)` which is not available in Java 21.
- Replaced unnamed variables `_` with `ignored` for Java 21 compatibility.
- Removed `@ValueBased` annotations.
- Removed `@PreviewFeature` annotations.
- Compatible with JDK 21.

### Upstream Bug Fixes

The following fixes have been applied to address bugs in the upstream OpenJDK jdk-sandbox code. These are upstream issues that should be reported to the [core-libs-dev@openjdk.org](mailto:core-libs-dev@openjdk.org) mailing list per OpenJDK process:

- **`JsonNumber.of(double)` offset bug** ([#118](https://github.com/simbo1905/java.util.json.Java21/issues/118)): The upstream implementation hardcodes `decimalOffset=0` and `exponentOffset=0`, causing `toLong()` to fail for integral doubles like `123.0`. Our fix delegates to `JsonNumber.of(String)` which correctly computes offsets via `Json.parse()`.

## Security Considerations

**⚠️ This unstable API historically contained a undocumented security vulnerabilities.** The compatibility test suite (documented below) includes crafted attack vectors that expose these issues:

- **Stack exhaustion attacks**: Deeply nested JSON structures can trigger `StackOverflowError`, potentially leaving applications in an undefined state and enabling denial-of-service attacks
- **API contract violations**: The `Json.parse()` method documentation only declares `JsonParseException` and `NullPointerException`, but malicious inputs can trigger undeclared exceptions

Such vulnerabilities existed at one point in the upstream OpenJDK sandbox implementation and were reported here for transparency. Until the upstream code is stable it is probably better to assume that such issue or similar may be present or may reappear. If you are only going to use this library in small cli programs where the json is configuration you write then you will not parse objects nested to tens of thousands of levels designed crash a parser. Yet you should not at this tiome expose this parser to the internet where someone can choose to attack it in that manner. 

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
./mvnw test -pl json-java21-jtd -Dtest=JtdSpecIT

# Run with detailed logging
./mvnw test -pl json-java21-jtd -Djava.util.logging.ConsoleHandler.level=FINE
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
./mvnw clean package
```

## JsonPath

This repo also includes a JsonPath query engine (module `json-java21-jsonpath`), based on the original Goessner JSONPath article:
https://goessner.net/articles/JsonPath/

```java
import jdk.sandbox.java.util.json.*;
import json.java21.jsonpath.JsonPath;
import json.java21.jsonpath.JsonPathStreams;

JsonValue doc = Json.parse("""
  {"store": {"book": [
    {"author": "Nora Quill", "title": "Signal Lake", "price": 8.95},
    {"author": "Jae Moreno", "title": "Copper Atlas", "price": 12.99},
    {"author": "Marek Ilyin", "title": "Paper Comet", "price": 22.99}
  ]}}
  """);

var authors = JsonPath.parse("$.store.book[*].author")
    .query(doc)
    .stream()
    .map(JsonValue::string)
    .toList();

System.out.println("Authors count: " + authors.size());     // prints '3'
System.out.println("First author: " + authors.getFirst());  // prints 'Nora Quill'
System.out.println("Last author: " + authors.getLast());    // prints 'Marek Ilyin'

var cheapTitles = JsonPath.parse("$.store.book[?(@.price < 10)].title")
    .query(doc)
    .stream()
    .map(JsonValue::string)
    .toList();

var priceStats = JsonPath.parse("$.store.book[*].price")
    .query(doc)
    .stream()
    .filter(JsonPathStreams::isNumber)
    .mapToDouble(JsonPathStreams::asDouble)
    .summaryStatistics();

System.out.println("Total price: " + priceStats.getSum());
System.out.println("Min price: " + priceStats.getMin());
System.out.println("Max price: " + priceStats.getMax());
System.out.println("Avg price: " + priceStats.getAverage());
```

See `json-java21-jsonpath/README.md` for JsonPath operators and more examples.

## Contributing

If you use an AI assistant while contributing, ensure it follows the contributor/agent workflow rules in `AGENTS.md`.

## License

Licensed under the GNU General Public License version 2 with Classpath exception. See [LICENSE](LICENSE) for details.
