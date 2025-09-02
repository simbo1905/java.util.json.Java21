# JSON Schema Validator

Stack-based JSON Schema validator using sealed interface pattern with inner record types.

## API Design

Single public interface with all schema types as inner records:

```java
package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;

public sealed interface JsonSchema permits JsonSchema.Nothing {
    
    // Prevents external implementations
    enum Nothing implements JsonSchema {
        INSTANCE;
    }
    
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