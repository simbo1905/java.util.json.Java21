package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/// Probes for Nullable modifier edge cases and potential issues
/// 
/// Areas to probe:
/// 1. Nullable on all form types
/// 2. Nullable false (explicit)
/// 3. Nested nullable
/// 4. Nullable with required properties
/// 5. Nullable ref
public class NullableEdgeCaseProbe extends JtdTestBase {

  /// Test: Nullable type accepts null
  @Test
  public void probeNullableTypeAcceptsNull() {
    JsonValue schema = Json.parse("{\"type\": \"string\", \"nullable\": true}");
    
    Jtd validator = new Jtd();
    
    // Null should be valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // String should also be valid
    assertTrue(validator.validate(schema, Json.parse("\"test\"")).isValid());
    
    // Other types should be invalid
    assertFalse(validator.validate(schema, Json.parse("123")).isValid());
    
    LOG.info(() -> "Nullable type: passed");
  }

  /// Test: Non-nullable type rejects null
  @Test
  public void probeNonNullableTypeRejectsNull() {
    JsonValue schema = Json.parse("{\"type\": \"string\"}");
    
    Jtd validator = new Jtd();
    
    // Null should be rejected
    Jtd.Result result = validator.validate(schema, Json.parse("null"));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Non-nullable type rejects null: " + !result.isValid());
  }

  /// Test: Nullable explicit false
  @Test
  public void probeNullableExplicitFalse() {
    JsonValue schema = Json.parse("{\"type\": \"string\", \"nullable\": false}");
    
    Jtd validator = new Jtd();
    
    // Should behave same as non-nullable
    assertTrue(validator.validate(schema, Json.parse("\"test\"")).isValid());
    assertFalse(validator.validate(schema, Json.parse("null")).isValid());
    
    LOG.info(() -> "Nullable explicit false: passed");
  }

  /// Test: Nullable on empty schema
  @Test
  public void probeNullableEmptySchema() {
    JsonValue schema = Json.parse("{\"nullable\": true}");
    
    Jtd validator = new Jtd();
    
    // Empty schema accepts anything, nullable adds null acceptance
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    assertTrue(validator.validate(schema, Json.parse("\"anything\"")).isValid());
    assertTrue(validator.validate(schema, Json.parse("123")).isValid());
    assertTrue(validator.validate(schema, Json.parse("[]")).isValid());
    assertTrue(validator.validate(schema, Json.parse("{}")).isValid());
    
    LOG.info(() -> "Nullable empty schema: passed");
  }

  /// Test: Nullable on enum
  @Test
  public void probeNullableEnum() {
    JsonValue schema = Json.parse("{\"enum\": [\"a\", \"b\", \"c\"], \"nullable\": true}");
    
    Jtd validator = new Jtd();
    
    // Null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // Enum values are valid
    assertTrue(validator.validate(schema, Json.parse("\"a\"")).isValid());
    
    // Invalid values still rejected
    assertFalse(validator.validate(schema, Json.parse("\"d\"")).isValid());
    assertFalse(validator.validate(schema, Json.parse("123")).isValid());
    
    LOG.info(() -> "Nullable enum: passed");
  }

  /// Test: Nullable on elements
  @Test
  public void probeNullableElements() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}, \"nullable\": true}");
    
    Jtd validator = new Jtd();
    
    // Null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // Array is valid
    assertTrue(validator.validate(schema, Json.parse("[\"a\", \"b\"]")).isValid());
    
    // Invalid element still rejected
    assertFalse(validator.validate(schema, Json.parse("[\"a\", 123]")).isValid());
    
    LOG.info(() -> "Nullable elements: passed");
  }

  /// Test: Nullable on properties
  @Test
  public void probeNullableProperties() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "name": {"type": "string"}
        },
        "nullable": true
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // Object is valid
    assertTrue(validator.validate(schema, Json.parse("{\"name\": \"test\"}")).isValid());
    
    // Missing required property still invalid
    assertFalse(validator.validate(schema, Json.parse("{}")).isValid());
    
    LOG.info(() -> "Nullable properties: passed");
  }

  /// Test: Nullable on values
  @Test
  public void probeNullableValues() {
    JsonValue schema = Json.parse("{\"values\": {\"type\": \"int32\"}, \"nullable\": true}");
    
    Jtd validator = new Jtd();
    
    // Null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // Object is valid
    assertTrue(validator.validate(schema, Json.parse("{\"a\": 1, \"b\": 2}")).isValid());
    
    // Invalid value still rejected
    assertFalse(validator.validate(schema, Json.parse("{\"a\": \"not-int\"}")).isValid());
    
    LOG.info(() -> "Nullable values: passed");
  }

  /// Test: Nullable on discriminator
  @Test
  public void probeNullableDiscriminator() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "type",
        "mapping": {
          "a": {"properties": {"value": {"type": \"string\"}}}
        },
        "nullable": true
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // Valid discriminator object
    assertTrue(validator.validate(schema, Json.parse("{\"type\": \"a\", \"value\": \"test\"}")).isValid());
    
    // Invalid discriminator still rejected
    assertFalse(validator.validate(schema, Json.parse("{\"type\": \"unknown\"}")).isValid());
    
    LOG.info(() -> "Nullable discriminator: passed");
  }

  /// Test: Nullable on ref
  @Test
  public void probeNullableRef() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "item": {"type": \"string\"}
        },
        "ref": "item",
        "nullable": true
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // String is valid
    assertTrue(validator.validate(schema, Json.parse("\"test\"")).isValid());
    
    // Invalid type still rejected
    assertFalse(validator.validate(schema, Json.parse("123")).isValid());
    
    LOG.info(() -> "Nullable ref: passed");
  }

  /// Test: Nested nullable (nullable inside nullable)
  @Test
  public void probeNestedNullable() {
    // This is technically valid JSON but may not make semantic sense
    // Testing if the implementation handles it gracefully
    
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "field": {
            "type": "string",
            "nullable": true
          }
        },
        "nullable": true
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Root null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // Object with null field is valid
    assertTrue(validator.validate(schema, Json.parse("{\"field\": null}")).isValid());
    
    // Object with string field is valid
    assertTrue(validator.validate(schema, Json.parse("{\"field\": \"test\"}")).isValid());
    
    LOG.info(() -> "Nested nullable: passed");
  }

  /// Test: Nullable property value
  @Test
  public void probeNullablePropertyValue() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "name": {"type": "string", "nullable": true}
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Property with null value is valid
    assertTrue(validator.validate(schema, Json.parse("{\"name\": null}")).isValid());
    
    // Property with string value is valid
    assertTrue(validator.validate(schema, Json.parse("{\"name\": \"test\"}")).isValid());
    
    // Missing property (if required) would be invalid, but it's optional here
    LOG.info(() -> "Nullable property value: passed");
  }

  /// Test: Nullable required property
  @Test
  public void probeNullableRequiredProperty() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "id": {"type": "int32", "nullable": true}
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Required property with null is valid (present, and null is allowed)
    assertTrue(validator.validate(schema, Json.parse("{\"id\": null}")).isValid());
    
    // Required property with int is valid
    assertTrue(validator.validate(schema, Json.parse("{\"id\": 123}")).isValid());
    
    // Required property missing is invalid
    assertFalse(validator.validate(schema, Json.parse("{}")).isValid());
    
    LOG.info(() -> "Nullable required property: passed");
  }

  /// Test: Nullable optional property
  @Test
  public void probeNullableOptionalProperty() {
    JsonValue schema = Json.parse("""
      {
        "optionalProperties": {
          "note": {"type": "string", "nullable": true}
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Optional property absent is valid
    assertTrue(validator.validate(schema, Json.parse("{}")).isValid());
    
    // Optional property with null is valid
    assertTrue(validator.validate(schema, Json.parse("{\"note\": null}")).isValid());
    
    // Optional property with string is valid
    assertTrue(validator.validate(schema, Json.parse("{\"note\": \"test\"}")).isValid());
    
    LOG.info(() -> "Nullable optional property: passed");
  }

  /// Test: Nullable array element
  @Test
  public void probeNullableArrayElement() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\", \"nullable\": true}}");
    
    Jtd validator = new Jtd();
    
    // Array with null element
    assertTrue(validator.validate(schema, Json.parse("[\"a\", null, \"c\"]")).isValid());
    
    // Array with all nulls
    assertTrue(validator.validate(schema, Json.parse("[null, null, null]")).isValid());
    
    // Mixed valid and null
    assertTrue(validator.validate(schema, Json.parse("[\"a\", null, \"b\", null]")).isValid());
    
    LOG.info(() -> "Nullable array element: passed");
  }

  /// Test: Nullable values in object
  @Test
  public void probeNullableValuesInObject() {
    JsonValue schema = Json.parse("{\"values\": {\"type\": \"int32\", \"nullable\": true}}");
    
    Jtd validator = new Jtd();
    
    // Object with null values
    assertTrue(validator.validate(schema, Json.parse("{\"a\": null, \"b\": 123}")).isValid());
    
    // Object with all null values
    assertTrue(validator.validate(schema, Json.parse("{\"a\": null, \"b\": null}")).isValid());
    
    LOG.info(() -> "Nullable values in object: passed");
  }

  /// Test: Nullable with integer types
  @Test
  public void probeNullableWithIntegerTypes() {
    String[] intTypes = {"int8", "uint8", "int16", "uint16", "int32", "uint32"};
    
    for (String type : intTypes) {
      JsonValue schema = Json.parse("{\"type\": \"" + type + "\", \"nullable\": true}");
      Jtd validator = new Jtd();
      
      // Null is valid
      assertTrue(validator.validate(schema, Json.parse("null")).isValid(), 
          type + " nullable should accept null");
      
      // Valid integer is valid
      if (type.startsWith("u")) {
        assertTrue(validator.validate(schema, Json.parse("100")).isValid());
      } else {
        assertTrue(validator.validate(schema, Json.parse("-50")).isValid());
      }
    }
    
    LOG.info(() -> "Nullable with integer types: passed");
  }

  /// Test: Nullable with float types
  @Test
  public void probeNullableWithFloatTypes() {
    JsonValue schema32 = Json.parse("{\"type\": \"float32\", \"nullable\": true}");
    JsonValue schema64 = Json.parse("{\"type\": \"float64\", \"nullable\": true}");
    Jtd validator = new Jtd();
    
    // Null valid for both
    assertTrue(validator.validate(schema32, Json.parse("null")).isValid());
    assertTrue(validator.validate(schema64, Json.parse("null")).isValid());
    
    // Floats valid for both
    assertTrue(validator.validate(schema32, Json.parse("3.14")).isValid());
    assertTrue(validator.validate(schema64, Json.parse("3.14")).isValid());
    
    LOG.info(() -> "Nullable with float types: passed");
  }

  /// Test: Nullable with timestamp
  @Test
  public void probeNullableWithTimestamp() {
    JsonValue schema = Json.parse("{\"type\": \"timestamp\", \"nullable\": true}");
    Jtd validator = new Jtd();
    
    // Null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // Valid timestamp is valid
    assertTrue(validator.validate(schema, Json.parse("\"2023-01-01T00:00:00Z\"")).isValid());
    
    // Invalid timestamp still rejected
    assertFalse(validator.validate(schema, Json.parse("\"not-a-timestamp\"")).isValid());
    
    LOG.info(() -> "Nullable with timestamp: passed");
  }

  /// Test: Nullable with boolean
  @Test
  public void probeNullableWithBoolean() {
    JsonValue schema = Json.parse("{\"type\": \"boolean\", \"nullable\": true}");
    Jtd validator = new Jtd();
    
    // Null is valid
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    
    // Booleans are valid
    assertTrue(validator.validate(schema, Json.parse("true")).isValid());
    assertTrue(validator.validate(schema, Json.parse("false")).isValid());
    
    // Non-boolean still rejected
    assertFalse(validator.validate(schema, Json.parse("\"true\"")).isValid());
    
    LOG.info(() -> "Nullable with boolean: passed");
  }

  /// Test: Nullable error messages
  @Test
  public void probeNullableErrorMessages() {
    JsonValue nullableSchema = Json.parse("{\"type\": \"string\", \"nullable\": true}");
    JsonValue nonNullableSchema = Json.parse("{\"type\": \"string\"}");
    
    Jtd validator = new Jtd();
    
    // Non-nullable with null
    Jtd.Result nonNullResult = validator.validate(nonNullableSchema, Json.parse("null"));
    String nonNullError = nonNullResult.errors().get(0);
    
    // Nullable with invalid type
    Jtd.Result nullInvalidResult = validator.validate(nullableSchema, Json.parse("123"));
    String nullInvalidError = nullInvalidResult.errors().get(0);
    
    LOG.info(() -> "Non-nullable null error: " + nonNullError);
    LOG.info(() -> "Nullable invalid type error: " + nullInvalidError);
    
    // Both should have errors
    assertFalse(nonNullResult.isValid());
    assertFalse(nullInvalidResult.isValid());
  }

  /// Test: Nullable must be boolean compilation check
  @Test
  public void probeNullableMustBeBooleanCompilation() {
    // Invalid: nullable is not a boolean
    JsonValue schema = Json.parse("{\"type\": \"string\", \"nullable\": \"yes\"}");
    
    try {
      new Jtd().compile(schema);
      LOG.warning(() -> "COMPILATION BUG: Should reject non-boolean nullable");
    } catch (IllegalArgumentException e) {
      LOG.info(() -> "Correctly rejected non-boolean nullable: " + e.getMessage());
    }
  }

  /// Test: Nullable in definitions
  @Test
  public void probeNullableInDefinitions() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "maybeString": {
            "type": "string",
            "nullable": true
          }
        },
        "properties": {
          "field": {"ref": "maybeString"}
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Field can be null
    assertTrue(validator.validate(schema, Json.parse("{\"field\": null}")).isValid());
    assertTrue(validator.validate(schema, Json.parse("{\"field\": \"test\"}")).isValid());
    
    LOG.info(() -> "Nullable in definitions: passed");
  }

  /// Test: Nullable with complex nested schema
  @Test
  public void probeNullableWithComplexNestedSchema() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "data": {
            "elements": {
              "properties": {
                "value": {"type": "string", "nullable": true}
              }
            },
            "nullable": true
          }
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Null data
    assertTrue(validator.validate(schema, Json.parse("{\"data\": null}")).isValid());
    
    // Array with null values
    assertTrue(validator.validate(schema, Json.parse("{\"data\": [{\"value\": null}]}")).isValid());
    
    // Array with string values
    assertTrue(validator.validate(schema, Json.parse("{\"data\": [{\"value\": \"test\"}]}")).isValid());
    
    LOG.info(() -> "Nullable complex nested: passed");
  }

  /// Test: Nullable discriminator mapping value
  /// RFC 8927: Discriminator mapping values cannot be nullable
  @Test
  public void probeNullableDiscriminatorMappingValue() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "type",
        "mapping": {
          "data": {
            "properties": {
              "value": {"type": "string"}
            },
            "nullable": true
          }
        }
      }
      """);
    
    try {
      new Jtd().compile(schema);
      LOG.warning(() -> "COMPILATION BUG: Should reject nullable discriminator mapping");
    } catch (IllegalArgumentException e) {
      LOG.info(() -> "Correctly rejected nullable discriminator mapping: " + e.getMessage());
    }
  }

  /// Test: Multiple nullable fields
  @Test
  public void probeMultipleNullableFields() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "a": {"type": "string", "nullable": true},
          "b": {"type": "int32", "nullable": true},
          "c": {"type": "boolean", "nullable": true}
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // All null
    assertTrue(validator.validate(schema, Json.parse("{\"a\": null, \"b\": null, \"c\": null}")).isValid());
    
    // All valid
    assertTrue(validator.validate(schema, Json.parse("{\"a\": \"test\", \"b\": 123, \"c\": true}")).isValid());
    
    // Mixed
    assertTrue(validator.validate(schema, Json.parse("{\"a\": null, \"b\": 456, \"c\": null}")).isValid());
    
    LOG.info(() -> "Multiple nullable fields: passed");
  }

  /// Test: Nullable with additionalProperties
  @Test
  public void probeNullableWithAdditionalProperties() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "id": {"type": "int32", "nullable": true}
        },
        "additionalProperties": false
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Valid with null
    assertTrue(validator.validate(schema, Json.parse("{\"id\": null}")).isValid());
    
    // Valid with int
    assertTrue(validator.validate(schema, Json.parse("{\"id\": 123}")).isValid());
    
    // Invalid: extra property
    assertFalse(validator.validate(schema, Json.parse("{\"id\": 123, \"extra\": \"bad\"}")).isValid());
    
    LOG.info(() -> "Nullable with additionalProperties: passed");
  }
}
