# JSON Type Definition (JTD) Validator Architecture

## Overview

This module implements a JSON Type Definition (JTD) validator based on RFC 8927. JTD is a schema language for JSON designed for code generation and portable validation with standardized error indicators. Unlike JSON Schema, JTD uses eight mutually-exclusive forms that make validation simpler and more predictable.

**Key Architectural Principles:**
- **Simpler than JSON Schema**: Eight mutually-exclusive forms vs. complex combinatorial logic
- **Immutable Design**: All schema types are records, validation is pure functions
- **Stack-based Validation**: Explicit validation stack for error path tracking
- **RFC 8927 Compliance**: Strict adherence to the specification
- **Performance Focused**: Minimal allocations, efficient validation paths

## JTD Schema Forms (RFC 8927 Section 2.2)

JTD defines eight mutually-exclusive schema forms:

1. **empty** - Validates any JSON value (RFC 8927 §2.2.1)
2. **ref** - References a definition in the schema (RFC 8927 §2.2.2)
3. **type** - Validates primitive types (RFC 8927 §2.2.3)
4. **enum** - Validates against a set of string values (RFC 8927 §2.2.4)
5. **elements** - Validates homogeneous arrays (RFC 8927 §2.2.5)
6. **properties** - Validates objects with required/optional fields (RFC 8927 §2.2.6)
7. **values** - Validates objects with homogeneous values (RFC 8927 §2.2.7)
8. **discriminator** - Validates tagged unions (RFC 8927 §2.2.8)

### Discriminator Schema Constraints (RFC 8927 §2.2.8)
Discriminator schemas enforce compile-time constraints to ensure predictable validation:
- **Mapping values must be PropertiesSchema**: Cannot use primitive types, enums, or other forms
- **No nullable mappings**: Mapped schemas cannot have `nullable: true`
- **Discriminator key exemption**: The discriminator field is ignored during payload validation
- **No discriminator redefinition**: Mapped schemas cannot define the discriminator key in properties/optionalProperties

These constraints are enforced at compile-time, preventing invalid schemas from reaching validation.

## Architecture Flow

```mermaid
flowchart TD
    A[JSON Document] --> B[Json.parse]
    B --> C[JsonValue]
    C --> D{JTDSchema.compile}
    D --> E[Parse Phase]
    E --> F[Validation Phase]
    F --> G[ValidationResult]
    
    E --> E1[Identify Schema Form]
    E --> E2[Extract Definitions]
    E --> E3[Build Immutable Records]
    
    F --> F1[Stack-based Validation]
    F --> F2[Error Path Tracking]
    F --> F3[Standardized Errors]
```

## Core API Design

Following modern Java patterns, we use a package-private sealed interface with record implementations and a public facade class:

```java
package json.java21.jtd;

import jdk.sandbox.java.util.json.*;

/// Package-private sealed interface for schema types
sealed interface JtdSchema 
    permits JtdSchema.EmptySchema,
            JtdSchema.RefSchema,
            JtdSchema.TypeSchema,
            JtdSchema.EnumSchema,
            JtdSchema.ElementsSchema,
            JtdSchema.PropertiesSchema,
            JtdSchema.ValuesSchema,
            JtdSchema.DiscriminatorSchema,
            JtdSchema.NullableSchema {

    /// Schema type records (package-private)
    record EmptySchema() implements JtdSchema {}
    record RefSchema(String ref, Map<String, JtdSchema> definitions) implements JtdSchema {}
    record TypeSchema(PrimitiveType type) implements JtdSchema {}
    record EnumSchema(Set<String> values) implements JtdSchema {}
    record ElementsSchema(JtdSchema elements) implements JtdSchema {}
    record PropertiesSchema(
        Map<String, JtdSchema> properties,
        Map<String, JtdSchema> optionalProperties,
        boolean additionalProperties
    ) implements JtdSchema {}
    record ValuesSchema(JtdSchema values) implements JtdSchema {}
    record DiscriminatorSchema(
        String discriminator,
        Map<String, JtdSchema> mapping
    ) implements JtdSchema {}
    record NullableSchema(JtdSchema nullable) implements JtdSchema {}
}

/// Public facade class for JTD operations
public class Jtd {
    /// Compile and validate JSON against JTD schema
    public Result validate(JsonValue schema, JsonValue instance) {
        JtdSchema jtdSchema = compileSchema(schema);
        return validateWithStack(jtdSchema, instance);
    }
    
    /// Validation result
    public record Result(boolean isValid, List<String> errors) {}
}
```

## Type System (RFC 8927 Section 2.2.3)

JTD supports these primitive types, each with specific validation rules:

```java
enum PrimitiveType {
    BOOLEAN,
    FLOAT32, FLOAT64,
    INT8, UINT8, INT16, UINT16, INT32, UINT32,
    STRING,
    TIMESTAMP
}
```

**Architectural Impact:**
- **No 64-bit integers** (RFC 8927 §2.2.3.1): Simplifies numeric validation
- **Timestamp format** (RFC 8927 §2.2.3.2): Must be RFC 3339 format
- **Float precision** (RFC 8927 §2.2.3.3): Separate validation for 32-bit vs 64-bit

## Validation Architecture

The JTD validator uses a single stack-based validation engine that enforces RFC 8927 compliance through immutable schema records. All validation flows through one path to prevent behavioral divergence.

### Single Path Validation Principle
- **No per-schema validate() methods**: Schema records are immutable data only
- **Stack-based only**: All validation uses `pushChildFrames()` and explicit stack traversal
- **Compile-time enforcement**: Invalid schemas are rejected during compilation, not validation

```mermaid
sequenceDiagram
    participant User
    participant JTD
    participant ValidationStack
    participant ErrorCollector
    
    User->>JTD: validate(schemaJson, instanceJson)
    JTD->>JTD: compileSchema(schemaJson)
    Note over JTD: Compile-time checks enforce RFC constraints
    JTD->>ValidationStack: push(rootSchema, "#")
    loop While stack not empty
        ValidationStack->>JTD: pop()
        JTD->>JTD: validateCurrent()
        alt Validation fails
            JTD->>ErrorCollector: addError(path, message)
        else Has children
            JTD->>ValidationStack: push(children)
        end
    end
    JTD->>User: ValidationResult
```

## Error Reporting (RFC 8927 Section 3.2)

JTD specifies standardized error format with:
- **instancePath**: JSON Pointer to failing value in instance
- **schemaPath**: JSON Pointer to failing constraint in schema

```java
record ValidationError(
    String instancePath,  // RFC 8927 §3.2.1
    String schemaPath,    // RFC 8927 §3.2.2  
    String message        // Human-readable error description
) {}
```

## Compilation Phase

```mermaid
flowchart TD
    A[JsonValue Schema] --> B{Identify Form}
    B -->|empty| C[EmptySchema]
    B -->|ref| D[RefSchema]
    B -->|type| E[TypeSchema]
    B -->|enum| F[EnumSchema]
    B -->|elements| G[ElementsSchema]
    B -->|properties| H[PropertiesSchema]
    B -->|values| I[ValuesSchema]
    B -->|discriminator| J[DiscriminatorSchema]
    
    C --> K[Immutable Record]
    D --> K
    E --> K
    F --> K
    G --> K
    H --> K
    I --> K
    J --> K
    
    K --> L[JTDSchema Instance]
```

## Definitions Support (RFC 8927 Section 2.1)

JTD allows schema definitions for reuse via `$ref`:

```java
record CompiledSchema(
    JTDSchema root,
    Map<String, JTDSchema> definitions  // RFC 8927 §2.1
) {}
```

**Constraints** (RFC 8927 §2.1.1):
- Definitions cannot be nested
- Only top-level definitions allowed
- References must resolve to defined schemas

## Simplifications vs JSON Schema

| Aspect | JTD (This Module) | JSON Schema |
|--------|-------------------|-------------|
| Schema Forms | 8 mutually exclusive | 40+ combinable keywords |
| References | Simple `$ref` to definitions | Complex `$ref` with URI resolution |
| Validation Logic | Exhaustive switch on sealed types | Complex boolean logic with allOf/anyOf/not |
| Error Paths | Simple instance+schema paths | Complex evaluation paths |
| Remote Schemas | Not supported | Full URI resolution |
| Type System | Fixed primitive set | Extensible validation keywords |

## Implementation Strategy

### Phase 1: Core Types
1. Define sealed interface `JTDSchema` with 8 record implementations
2. Implement `PrimitiveType` enum with validation logic
3. Create `ValidationError` and `ValidationResult` records

### Phase 2: Parser
1. Implement schema form detection (mutually exclusive check)
2. Build immutable record hierarchy from JSON
3. Handle definitions extraction and validation

### Phase 3: Validator  
1. Implement stack-based validation engine
2. Add error path tracking (instance + schema paths)
3. Implement all 8 schema form validators

### Phase 4: Testing
1. Unit tests for each schema form
2. Integration tests with RFC examples
3. Error case validation
4. Performance benchmarks

## Usage Example

```java
import jdk.sandbox.java.util.json.*;
import json.java21.jtd.Jtd;

// Create JTD validator
Jtd jtd = new Jtd();

// Compile JTD schema
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

// Validate JSON
String json = """
{"id": "123", "name": "Alice", "age": 30, "email": "alice@example.com"}
""";

Jtd.Result result = jtd.validate(Json.parse(schemaJson), Json.parse(json));

if (!result.isValid()) {
    for (var error : result.errors()) {
        System.out.println(error);
    }
}
```

## Testing

Run the official JTD Test Suite:

```bash
# Run all JTD spec compliance tests
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Dtest=JtdSpecIT
```

`JtdSpecIT` exercises only the published `validation.json` cases so coverage maps exactly to behaviour that downstream users rely on. Compilation enforcement is handled through dedicated suites:

- `CompilerSpecIT` replays `invalid_schemas.json` and asserts that compilation fails with deterministic exception messages for every illegal schema.
- `CompilerTest` holds incremental unit tests for compiler internals (for example, discriminator guard scenarios) while still extending the JUL logging helper to emit INFO banners per method.

Run the compiler-focused suites when evolving compile-time logic:

```bash
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Dtest=CompilerSpecIT
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Dtest=CompilerTest
```

## Performance Considerations

1. **Immutable Records**: Zero mutation during validation
2. **Stack-based Validation**: Explicit stack vs recursion prevents StackOverflowError
3. **Minimal Allocations**: Reuse validation context objects
4. **Early Exit**: Fail fast on first validation error (when appropriate)
5. **Type-specific Validation**: Optimized paths for each primitive type

## Error Handling

- **Schema Compilation**: `IllegalArgumentException` for invalid schemas
- **Validation**: Never throws, returns `ValidationResult` with errors
- **Definitions**: Validate all definitions exist at compile time
- **Type Checking**: Strict RFC 8927 compliance for all primitive types

## Empty Schema `{}`

- **Form**: `empty = {}`
- **Behavior**: **accepts all instances**; produces no validation errors.
- **RFC 8927 §3.3.1**: "If a schema is of the 'empty' form, then it accepts all instances. A schema of the 'empty' form will never produce any error indicators."
- **Common pitfall**: confusing JTD with non-JTD validators that treat `{}` as an empty-object schema.
- **Implementation**: compile `{}` to `EmptySchema` and validate everything as OK.

## RFC 8927 Compliance

This implementation strictly follows RFC 8927:
- ✅ Eight mutually-exclusive schema forms
- ✅ Standardized error format (instancePath, schemaPath)
- ✅ Primitive type validation (no 64-bit integers)
- ✅ Definition support (non-nested)
- ✅ Timestamp format validation (RFC 3339)
- ✅ No remote schema support (simplification by design)

## Future Extensions

Potential future additions (non-RFC compliant):
- Custom type validators
- Additional format validators  
- Remote definition support
- Performance optimizations for specific use cases