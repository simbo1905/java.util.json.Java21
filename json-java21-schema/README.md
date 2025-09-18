# JSON Schema Validator

Stack-based JSON Schema validator using sealed interface pattern with inner record types.

- Draft 2020-12 subset: object/array/string/number/boolean/null, allOf/anyOf/not, if/then/else, const, format (11 validators), $defs and local $ref (including root "#")
- Thread-safe compiled schemas; immutable results with error paths/messages
- **Novel Architecture**: This module uses an innovative immutable "compile many documents (possibly just one) into an immutable set of roots using a work stack" compile-time architecture for high-performance schema compilation and validation. See `AGENTS.md` for detailed design documentation.

Quick usage

```java
import jdk.sandbox.java.util.json.Json;
import io.github.simbo1905.json.schema.JsonSchema;

var schema = JsonSchema.compile(Json.parse("""
  {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
"""));
var result = schema.validate(Json.parse("{\"name\":\"Alice\"}"));
// result.valid() == true
```

Compatibility and verify

- The module runs the official JSON Schema Test Suite during Maven verify.
- Default mode is lenient: unsupported groups/tests are skipped to avoid build breaks while still logging.
- Strict mode: enable with -Djson.schema.strict=true to enforce full assertions.
- **Measured compatibility**: 54.4% (992 of 1,822 tests pass in lenient mode)
- **Test coverage**: 420 test groups, 1,628 validation attempts, 73 unsupported schema groups, 0 test exceptions, 638 lenient mismatches
- Detailed metrics available via `-Djson.schema.metrics=json|csv`

How to run

```bash
# Run unit + integration tests (includes official suite)
mvn -pl json-java21-schema -am verify

# Strict mode
mvn -Djson.schema.strict=true -pl json-java21-schema -am verify
```

OpenRPC validation

- Additional integration test validates OpenRPC documents using a minimal, self‑contained schema:
  - Test: `src/test/java/io/github/simbo1905/json/schema/OpenRPCSchemaValidationIT.java`
  - Resources: `src/test/resources/openrpc/` (schema and examples)
  - Thanks to OpenRPC meta-schema and examples (Apache-2.0): https://github.com/open-rpc/meta-schema and https://github.com/open-rpc/examples

## API Design

Single public interface with all schema types as inner records:

```java
package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;

public sealed interface JsonSchema permits JsonSchema.Nothing {
    
    // Factory method to create schema from JSON
    static JsonSchema compile(JsonValue schemaJson) {
        // Parses JSON Schema document into immutable record hierarchy
        // Throws IllegalArgumentException if schema is invalid
    }
    
    // Validation method
    default ValidationResult validate(JsonValue json) {
        // Stack-based validation using inner schema records
    }
    
    // Schema type records
    record ObjectSchema(
        Map<String, JsonSchema> properties,
        Set<String> required,
        JsonSchema additionalProperties,
        Integer minProperties,
        Integer maxProperties
    ) implements JsonSchema {}
    
    record ArraySchema(
        JsonSchema items,
        Integer minItems,
        Integer maxItems,
        Boolean uniqueItems
    ) implements JsonSchema {}
    
    record StringSchema(
        Integer minLength,
        Integer maxLength,
        Pattern pattern,
        Set<String> enumValues
    ) implements JsonSchema {}
    
    record NumberSchema(
        BigDecimal minimum,
        BigDecimal maximum,
        BigDecimal multipleOf,
        Boolean exclusiveMinimum,
        Boolean exclusiveMaximum
    ) implements JsonSchema {}
    
    record BooleanSchema() implements JsonSchema {}
    record NullSchema() implements JsonSchema {}
    record AnySchema() implements JsonSchema {}
    
    record RefSchema(String ref) implements JsonSchema {}
    
    record AllOfSchema(List<JsonSchema> schemas) implements JsonSchema {}
    record AnyOfSchema(List<JsonSchema> schemas) implements JsonSchema {}
    record OneOfSchema(List<JsonSchema> schemas) implements JsonSchema {}
    record NotSchema(JsonSchema schema) implements JsonSchema {}
    
    // Validation result types
    record ValidationResult(boolean valid, List<ValidationError> errors) {
        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }
        public static ValidationResult invalid(List<ValidationError> errors) {
            return new ValidationResult(false, errors);
        }
    }
    
    record ValidationError(String path, String message) {}
}
```

## Usage

```java
import jdk.sandbox.java.util.json.*;
import io.github.simbo1905.json.schema.JsonSchema;

// Compile schema once (thread-safe, reusable)
String schemaDoc = """
    {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "age": {"type": "number", "minimum": 0}
        },
        "required": ["name"]
    }
    """;

JsonSchema schema = JsonSchema.compile(Json.parse(schemaDoc));

// Validate JSON documents
String jsonDoc = """
    {"name": "Alice", "age": 30}
    """;

JsonSchema.ValidationResult result = schema.validate(Json.parse(jsonDoc));

if (!result.valid()) {
    for (var error : result.errors()) {
        System.out.println(error.path() + ": " + error.message());
    }
}
```

### Format Validation

The validator supports JSON Schema 2020-12 format validation with opt-in assertion mode:

- **Built-in formats**: uuid, email, ipv4, ipv6, uri, uri-reference, hostname, date, time, date-time, regex
- **Annotation by default**: Format validation is annotation-only (always passes) unless format assertion is enabled
- **Opt-in assertion**: Enable format validation via:
  - `JsonSchema.Options(true)` parameter in `compile()`
  - System property: `-Djsonschema.format.assertion=true`
  - Root schema flag: `"formatAssertion": true`
- **Unknown formats**: Gracefully handled with logged warnings (no validation errors)

```java
// Format validation disabled (default) - always passes
var schema = JsonSchema.compile(Json.parse("""
  {"type": "string", "format": "email"}
"""));
schema.validate(Json.parse("\"invalid-email\"")); // passes

// Format validation enabled - validates format
var schema = JsonSchema.compile(Json.parse("""
  {"type": "string", "format": "email"}
"""), new JsonSchema.Options(true));
schema.validate(Json.parse("\"invalid-email\"")); // fails
schema.validate(Json.parse("\"user@example.com\"")); // passes
```
