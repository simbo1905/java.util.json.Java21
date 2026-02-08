# Probe Tests Summary

This document summarizes all the probe tests added to identify potential issues in the JTD implementation.

## Test Files Added

### 1. `ErrorFormatComplianceProbe.java`
**Purpose**: Verify RFC 8927 error format compliance

**Key Probes**:
- Error format should be `{instancePath, schemaPath}` pairs (not enriched strings)
- Schema paths should point to schema keywords (`/type`, `/properties/foo`, etc.)
- Instance paths should be RFC 6901 JSON Pointers
- Multiple errors should ALL be collected
- Error consistency across different violations

**Expected Issues to Find**:
- Implementation returns enriched strings like `[off=N ptr=# via=#] message`
- No RFC 6901 compliance for instance paths
- Schema paths not tracked separately

### 2. `DiscriminatorEdgeCaseProbe.java`
**Purpose**: Test discriminator schema edge cases

**Key Probes**:
- Missing vs non-string discriminator error distinction
- Discriminator tag exemption from additionalProperties
- Discriminator key redefinition in properties (compile-time check)
- Discriminator key in optionalProperties (compile-time check)
- Empty discriminator mapping
- Discriminator with additionalProperties: true
- Nested discriminator
- Discriminator error short-circuiting
- Discriminator with null/empty string values
- Multiple discriminator values with common properties
- Conflicting property types across variants

**Expected Issues to Find**:
- Missing vs non-string discriminator errors may be conflated
- Discriminator key not properly exempted from additionalProperties
- Variant validation may continue after discriminator error

### 3. `PropertiesEdgeCaseProbe.java`
**Purpose**: Test properties form edge cases

**Key Probes**:
- Empty properties with additionalProperties: false
- Empty properties without additionalProperties (default behavior)
- Special characters in property names (dots, slashes, spaces, null char)
- Multiple additional properties all reported
- Required property with null value vs missing property
- Optional property with various values
- Overlapping required and optional keys (compile-time check)
- Nested properties with different additionalProperties settings
- Property validation order
- Empty string property names
- Very deep nesting (50 levels)
- Large number of properties (100)
- Property name collisions with prototype pollution concerns

**Expected Issues to Find**:
- Empty properties may not properly reject additional properties
- Special characters in property names may not be handled correctly
- Deep nesting performance issues
- Prototype pollution vulnerability

### 4. `TypeValidationEdgeCaseProbe.java`
**Purpose**: Test type validation edge cases

**Key Probes**:
- Integer boundary values (exact min/max for all integer types)
- Integer fractional detection (3.0 is int, 3.1 is not)
- Scientific notation handling
- Float types accept any number
- Timestamp format variations
- Invalid timestamp formats
- Boolean type strictness
- String type strictness
- Very large integers
- Zero values for all integer types
- Negative values for unsigned types
- No type coercion
- BigDecimal values exceeding long precision

**Expected Issues to Find**:
- Integer validation may not properly handle fractional values
- Scientific notation may not be handled correctly
- Timestamp regex may be too strict or too lenient
- Very large numbers may cause precision issues

### 5. `RefEdgeCaseProbe.java`
**Purpose**: Test ref schema edge cases

**Key Probes**:
- Forward reference resolution
- Mutual recursion
- Deeply nested refs (50 levels)
- Ref in elements context
- Ref in values context
- Ref in properties context
- Ref to empty schema
- Ref to discriminator
- Ref to nullable schema
- Ref to elements/values/properties/enum/type
- Unused definitions
- Multiple refs to same definition
- Complex recursive ref (binary tree)
- Ref in optionalProperties
- Multi-level ref resolution

**Expected Issues to Find**:
- Forward references may not resolve correctly
- Deep nesting may cause stack issues
- Circular references may not be handled

### 6. `ElementsEdgeCaseProbe.java`
**Purpose**: Test elements form edge cases

**Key Probes**:
- Empty array validation
- Single element array
- Nested elements (2D/3D arrays)
- Elements with properties schema
- Elements with discriminator
- Elements error collection (all elements validated)
- Elements with strict nested objects
- Large array performance (1000 elements)
- Array with null elements
- Array with nullable elements
- Mixed valid and invalid elements
- Empty schema in elements
- Elements with ref
- Elements error path construction
- Nested elements error path
- Elements with object additional properties
- Multiple arrays in same schema
- Elements with values schema
- Elements with enum
- Sparse array
- Elements with complex nested structure
- Array type guard
- Elements with timestamp/boolean/float types
- Elements with integer boundaries

**Expected Issues to Find**:
- Error path construction for nested arrays
- Large array performance
- Error collection for all invalid elements

### 7. `NullableEdgeCaseProbe.java`
**Purpose**: Test nullable modifier edge cases

**Key Probes**:
- Nullable type accepts null
- Non-nullable type rejects null
- Nullable explicit false
- Nullable on empty schema
- Nullable on enum/elements/properties/values/discriminator/ref
- Nested nullable
- Nullable property value
- Nullable required property
- Nullable optional property
- Nullable array element
- Nullable values in object
- Nullable with all integer types
- Nullable with float types
- Nullable with timestamp/boolean
- Nullable error messages
- Nullable must be boolean compilation check
- Nullable in definitions
- Nullable with complex nested schema
- Nullable discriminator mapping value (compile-time check)
- Multiple nullable fields
- Nullable with additionalProperties

**Expected Issues to Find**:
- Nullable discriminator mapping may not be rejected at compile time
- Nullable compilation may accept non-boolean values

## Running the Tests

```bash
# Run all probe tests
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Dtest="*Probe"

# Run specific probe test
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Dtest=ErrorFormatComplianceProbe

# Run with detailed logging
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jtd -Dtest="*Probe" -Djava.util.logging.ConsoleHandler.level=FINE
```

## Expected Outcomes

These tests are designed to **document current behavior** and **expose deviations** from the specification. They do not fix any code.

Key deviations expected:
1. **Error format**: Returns enriched strings instead of RFC 8927 error indicators
2. **Schema paths**: Not tracked or reported per RFC 8927
3. **Discriminator**: May conflate missing vs non-string discriminator errors
4. **Empty properties**: May not properly reject additional properties by default
5. **Nullable discriminator mapping**: May not be rejected at compile time

## Notes

- All probe tests extend `JtdTestBase` for consistent logging
- Tests use INFO level logging at method start as per AGENTS.md
- Tests document both passing and failing assertions
- Tests include comments explaining the expected vs actual behavior
