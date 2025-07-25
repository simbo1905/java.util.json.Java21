# README Examples Plan

## Goal
Show practical, real-world usage patterns that demonstrate the unique value of this JSON API, focusing on:
1. Type-safe record mapping (the killer feature for modern Java)
2. Pattern matching with sealed types
3. Efficient conversion between Java types and JSON

## Proposed Examples Structure

### 1. Quick Start (Keep existing basic example)
```java
JsonValue value = Json.parse(jsonString);
```

### 2. Pattern Matching with JSON Types
Show how the sealed type hierarchy enables elegant pattern matching:
```java
// Processing unknown JSON structure
JsonValue value = Json.parse(response);
String result = switch (value) {
    case JsonObject obj -> processUser(obj);
    case JsonArray arr -> processUsers(arr);
    case JsonString str -> "Message: " + str.value();
    case JsonNumber num -> "Count: " + num.toNumber();
    case JsonBoolean bool -> bool.value() ? "Active" : "Inactive";
    case JsonNull n -> "No data";
};
```

### 3. Record Mapping Pattern
The most valuable use case - mapping JSON to/from Java records:

```java
// Domain model using records
record User(String name, String email, boolean active) {}
record Team(String teamName, List<User> members) {}

// Convert records to JSON
Team team = new Team("Engineering", List.of(
    new User("Alice", "alice@example.com", true),
    new User("Bob", "bob@example.com", false)
));

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

### 4. Builder Pattern for Complex JSON
Show how to build complex JSON structures programmatically:
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

### 5. Streaming/Processing Pattern
Show how to process large JSON arrays efficiently:
```java
// Process a large array of records
JsonArray items = (JsonArray) Json.parse(largeJsonArray);
List<String> activeUserEmails = items.values().stream()
    .map(v -> (JsonObject) v)
    .filter(obj -> ((JsonBoolean) obj.members().get("active")).value())
    .map(obj -> ((JsonString) obj.members().get("email")).value())
    .toList();
```

### 6. Error Handling
Show how to handle parsing errors and invalid data:
```java
try {
    JsonValue value = Json.parse(userInput);
    // Process valid JSON
} catch (JsonParseException e) {
    // Handle malformed JSON
    System.err.println("Invalid JSON at line " + e.getLine() + ": " + e.getMessage());
}
```

## What NOT to Include
- Basic JSON syntax explanation
- Full coverage of JSON spec
- Overly simple examples that don't show real value
- Complex examples that obscure the API's simplicity

## Focus Areas
1. **Record mapping** - This is what modern Java developers want
2. **Pattern matching** - Leverages Java 21+ features
3. **Type safety** - Show how compile-time safety prevents runtime errors
4. **Real-world patterns** - REST API responses, configuration parsing, data transformation