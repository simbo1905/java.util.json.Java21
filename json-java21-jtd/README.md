# JSON Type Definition (JTD) Validator

A Java implementation of the JSON Type Definition (JTD) specification (RFC 8927). JTD is a schema language for JSON that provides simple, predictable validation with eight mutually-exclusive schema forms.

## Features

- **RFC 8927 Compliant**: Full implementation of the JSON Type Definition specification
- **Eight Schema Forms**: Empty, Ref, Type, Enum, Elements, Properties, Values, Discriminator
- **Stack-based Validation**: Efficient iterative validation with comprehensive error reporting
- **Immutable Design**: All schema types are records, validation uses pure functions
- **Rich Error Messages**: Standardized error format with instance and schema paths
- **Comprehensive Testing**: Includes official JTD Test Suite for RFC compliance

## Quick Start

```java
import json.java21.jtd.Jtd;
import jdk.sandbox.java.util.json.*;

// Create a JTD schema
String schemaJson = """
{
  "properties": {
    "id": { "type": "string" },
    "name": { "type": "string" },
    "age": { "type": "int32" }
  },
  "optionalProperties": {
    "email": { "type": "string" }
  }
}
""";

// Parse and validate
JsonValue schema = Json.parse(schemaJson);
JsonValue data = Json.parse("{\"id\": \"123\", \"name\": \"Alice\", \"age\": 30}");

Jtd validator = new Jtd();
Jtd.Result result = validator.validate(schema, data);

if (result.isValid()) {
    System.out.println("Valid!");
} else {
    result.errors().forEach(System.out::println);
}
```

## Schema Forms

JTD defines eight mutually-exclusive schema forms:

### 1. Empty Schema
Accepts any JSON value:
```json
{}
```

### 2. Ref Schema
References a definition:
```json
{"ref": "address"}
```

### 3. Type Schema
Validates primitive types:
```json
{"type": "string"}
```

Supported types: `boolean`, `string`, `timestamp`, `int8`, `uint8`, `int16`, `uint16`, `int32`, `uint32`, `float32`, `float64`

#### Integer Type Validation
Integer types (`int8`, `uint8`, `int16`, `uint16`, `int32`, `uint32`) validate based on **numeric value**, not textual representation:

- **Valid integers**: `3`, `3.0`, `3.000`, `42.00` (mathematically integers)
- **Invalid integers**: `3.1`, `3.14`, `3.0001` (have fractional components)

This follows RFC 8927 §2.2.3.1: "An integer value is a number without a fractional component."

### 4. Enum Schema
Validates against string values:
```json
{"enum": ["red", "green", "blue"]}
```

### 5. Elements Schema
Validates homogeneous arrays:
```json
{"elements": {"type": "string"}}
```

### 6. Properties Schema
Validates objects with required/optional fields:
```json
{
  "properties": {
    "id": {"type": "string"},
    "name": {"type": "string"}
  },
  "optionalProperties": {
    "email": {"type": "string"}
  }
}
```

### 7. Values Schema
Validates objects with homogeneous values:
```json
{"values": {"type": "string"}}
```

### 8. Discriminator Schema
Validates tagged unions:
```json
{
  "discriminator": "type",
  "mapping": {
    "person": {"properties": {"name": {"type": "string"}}},
    "company": {"properties": {"name": {"type": "string"}}}
  }
}
```

## Nullable Schemas

Any schema can be made nullable by adding `"nullable": true`:

```json
{"type": "string", "nullable": true}
```

## Definitions

Schemas can define reusable components:

```json
{
  "definitions": {
    "address": {
      "properties": {
        "street": {"type": "string"},
        "city": {"type": "string"}
      }
    }
  },
  "properties": {
    "home": {"ref": "address"},
    "work": {"ref": "address"}
  }
}
```

## Error Reporting

Validation errors include standardized information:

```
[off=45 ptr=/age via=#→field:age] expected int32, got string
```

- **off**: Character offset in the JSON document
- **ptr**: JSON Pointer to the failing value
- **via**: Human-readable path to the error location

## Building and Testing

```bash
# Build the module
$(command -v mvnd || command -v mvn || command -v ./mvnw) compile -pl json-java21-jtd

# Run tests
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd

# Run RFC compliance tests
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Dtest=JtdSpecIT

# Run with detailed logging
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Djava.util.logging.ConsoleHandler.level=FINE
```

## Architecture

The validator uses a stack-based approach for efficient validation:

- **Immutable Records**: All schema types are immutable records
- **Stack-based Validation**: Iterative validation prevents stack overflow
- **Lazy Resolution**: References resolved only when needed
- **Comprehensive Testing**: Full RFC 8927 compliance test suite

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed implementation information.

## RFC 8927 Compliance

This implementation is fully compliant with RFC 8927:

- ✅ Eight mutually-exclusive schema forms
- ✅ Standardized error format with instance and schema paths  
- ✅ Primitive type validation with proper ranges
- ✅ Definition support with reference resolution
- ✅ Timestamp format validation (RFC 3339 with leap seconds)
- ✅ Discriminator tag exemption from additional properties

## Performance

- **Zero allocations** during validation of simple types
- **Stack-based validation** prevents StackOverflowError
- **Early exit** on first validation error
- **Immutable design** enables safe concurrent use

## License

This project is part of the OpenJDK JSON API implementation and follows the same licensing terms.