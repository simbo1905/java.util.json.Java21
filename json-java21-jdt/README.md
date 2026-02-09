# JSON Document Transforms (JDT)

A Java implementation of JSON Document Transforms, inspired by [Microsoft's JSON Document Transforms](https://github.com/Microsoft/json-document-transforms) specification. JDT transforms a source JSON document using a declarative transform specification document with `@jdt.*` directives.

## Features

- **Declarative Transforms**: Transform documents mirror source structure with `@jdt.*` directives
- **Four Transform Verbs**: Rename, Remove, Merge, Replace
- **Fixed Execution Order**: Rename -> Remove -> Merge -> Replace (predictable, composable)
- **Recursive Default Merge**: Objects merge deeply, arrays append, primitives replace
- **Path-Based Operations**: Target specific nodes using JSONPath via `@jdt.path`
- **Immutable Design**: All operations return new values; source documents are never mutated
- **Microsoft Fixture Compatibility**: Validates against vendored Microsoft JDT test fixtures

## Quick Start

```java
import jdk.sandbox.java.util.json.*;
import json.java21.jdt.Jdt;

JsonValue source = Json.parse("""
    {
        "ConnectionStrings": {
            "Default": "Server=dev;Database=mydb"
        },
        "Logging": {
            "Level": "Debug"
        }
    }
    """);

JsonValue transform = Json.parse("""
    {
        "ConnectionStrings": {
            "Default": "Server=prod;Database=mydb"
        },
        "Logging": {
            "Level": "Warning"
        }
    }
    """);

JsonValue result = Jdt.transform(source, transform);
// Result: ConnectionStrings.Default updated, Logging.Level updated
```

## Default Behavior (No Directives)

When the transform document does not contain `@jdt.*` directives, the default behavior applies:

- **Object to Object**: Deep recursive merge. Existing keys are updated, new keys are added, missing keys are preserved.
- **Array to Array**: Arrays are appended (concatenated), not replaced.
- **Primitive to Primitive**: The transform value replaces the source value.
- **Non-object transform**: Replaces the source wholesale.

```java
// Deep merge
JsonValue source = Json.parse("""
    {"Settings": {"A": 1, "B": 2}}
    """);
JsonValue transform = Json.parse("""
    {"Settings": {"A": 10, "C": 3}}
    """);
JsonValue result = Jdt.transform(source, transform);
// {"Settings": {"A": 10, "B": 2, "C": 3}}
```

## Transform Verbs

### `@jdt.rename`

Renames keys without altering their values.

```json
// Transform: rename "oldName" to "newName"
{
    "@jdt.rename": {"oldName": "newName"}
}
```

The value can be:
- **Object**: `{"oldKey": "newKey", ...}` for direct key mapping
- **Array**: `[{"a": "b"}, {"c": "d"}]` for sequential renames

### `@jdt.remove`

Removes keys from the current node.

```json
// Remove a single key
{"@jdt.remove": "keyToRemove"}

// Remove multiple keys
{"@jdt.remove": ["key1", "key2"]}

// Remove all keys (set to null)
{"@jdt.remove": true}
```

The value can be:
- **String**: Single key name to remove
- **Array**: List of key names to remove
- **Boolean `true`**: Remove all keys (returns `null`)
- **Object with `@jdt.path`**: Path-based removal using JSONPath

### `@jdt.merge`

Explicitly deep-merges an object (same semantics as default merge, but stated explicitly).

```json
// Explicit merge
{
    "Settings": {
        "@jdt.merge": {"newSetting": "value"}
    }
}
```

### `@jdt.replace`

Wholesale replaces the current node.

```json
// Replace with a primitive
{"@jdt.replace": 42}

// Replace with an object
{"@jdt.replace": {"completely": "new"}}

// Replace with an array (double-bracket syntax)
{"@jdt.replace": [[1, 2, 3]]}
```

The double-bracket syntax `[[...]]` disambiguates "replace with this array" from "apply sequential operations".

## Execution Order

Within any single node, directives are applied in this fixed order:

**Rename -> Remove -> Merge -> Replace**

This means:
1. Renames happen first, so subsequent operations reference the new names
2. Remove runs on the already-renamed object
3. Merge adds/updates keys on the pruned object
4. Replace is last and can override everything

```java
// Combined: rename then remove
JsonValue transform = Json.parse("""
    {
        "@jdt.rename": {"A": "Alpha"},
        "@jdt.remove": "B"
    }
    """);
```

## Path-Based Operations

Use `@jdt.path` with JSONPath syntax to target specific nodes within the document.

```json
{
    "@jdt.remove": {
        "@jdt.path": "$.items[?(@.active == false)]"
    }
}
```

```json
{
    "@jdt.replace": {
        "@jdt.path": "$.settings.timeout",
        "@jdt.value": 30
    }
}
```

Path-based operations are supported for remove, merge, and replace. Path-based rename is a work in progress.

## Error Handling

Invalid transform specifications throw `JdtException`:

```java
try {
    Jdt.transform(source, transform);
} catch (JdtException e) {
    System.err.println("Transform error: " + e.getMessage());
}
```

Null inputs throw `NullPointerException` with descriptive messages.

## Building and Testing

```bash
# Build the module
./mvnw compile -pl json-java21-jdt -am

# Run all tests
./mvnw test -pl json-java21-jdt -am -Djava.util.logging.ConsoleHandler.level=INFO

# Run unit tests only
./mvnw test -pl json-java21-jdt -am -Dtest=JdtTest -Djava.util.logging.ConsoleHandler.level=FINE

# Run Microsoft fixture tests
./mvnw test -pl json-java21-jdt -am -Dtest=MicrosoftJdtFixturesTest -Djava.util.logging.ConsoleHandler.level=FINE
```

## Architecture

The engine is a single static utility class (`Jdt`) that walks the transform document recursively:

- **Immutable Functional Core**: Every method takes `JsonValue` inputs and returns new `JsonValue` outputs
- **Recursive Walker**: The transform document is interpreted on-the-fly as it is walked
- **JSONPath Integration**: Path-based operations use the `json-java21-jsonpath` module for node selection
- **Reference Identity Matching**: Path-matched nodes are found in the source tree by reference identity

### Source Files

File | Role
---|---
`Jdt.java` | Core transform engine (public API: `Jdt.transform`)
`JdtException.java` | Exception for invalid transform specifications

### Test Files

File | Role
---|---
`JdtTest.java` | Unit tests for each verb and default behavior (18 tests)
`MicrosoftJdtFixturesTest.java` | Parameterized tests using vendored Microsoft JDT fixtures (15 tests)
`JdtLoggingConfig.java` | JUL logging configuration base class

## Microsoft JDT Fixture Status

Category | Active | Skipped (path-based)
---|---|---
Default | 10 | 0
Remove | 3 | 8
Rename | 3 | 7
Replace | 4 | 3
Merge | 5 | 3

Fixtures in `Skipped/` subdirectories require path-based operations that are planned for future implementation.

## License

This project is part of the OpenJDK JSON API implementation and follows the same licensing terms.
