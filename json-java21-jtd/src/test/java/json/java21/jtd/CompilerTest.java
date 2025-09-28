package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// Unit tests for JTD schema compilation focusing on discriminator constraints and compile-time validation.
///
/// These tests verify that the compiler correctly rejects invalid schemas according to RFC 8927 §2.2.8
/// while allowing valid discriminator schemas to be compiled successfully.
public class CompilerTest extends JtdTestBase {

  @Test
  void discriminatorMappingMustBePropertiesSchema() {
    LOG.info(() -> "EXECUTING: discriminatorMappingMustBePropertiesSchema");
    
    // Invalid: mapping value is a primitive type schema instead of properties schema
    String invalidSchema = """
      {
        "discriminator": "kind",
        "mapping": {
          "bool": {"type": "boolean"}
        }
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for discriminator with non-properties mapping"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("mapping"), 
               "Error message should mention mapping constraint");
  }

  @Test
  void discriminatorMappingCannotBeNullable() {
    LOG.info(() -> "EXECUTING: discriminatorMappingCannotBeNullable");
    
    // Invalid: mapping value has nullable: true
    String invalidSchema = """
      {
        "discriminator": "kind",
        "mapping": {
          "person": {
            "properties": {
              "name": {"type": "string"}
            },
            "nullable": true
          }
        }
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for discriminator with nullable mapping"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("nullable"), 
               "Error message should mention nullable constraint");
  }

  @Test
  void discriminatorMappingCannotRedefineDiscriminatorKey() {
    LOG.info(() -> "EXECUTING: discriminatorMappingCannotRedefineDiscriminatorKey");
    
    // Invalid: mapping schema defines the discriminator key in properties
    String invalidSchema = """
      {
        "discriminator": "kind",
        "mapping": {
          "person": {
            "properties": {
              "kind": {"type": "string"},
              "name": {"type": "string"}
            }
          }
        }
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for discriminator with redefined key"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("discriminator") || exception.getMessage().contains("kind"), 
               "Error message should mention discriminator key conflict");
  }

  @Test
  void discriminatorMappingCannotRedefineDiscriminatorKeyInOptional() {
    LOG.info(() -> "EXECUTING: discriminatorMappingCannotRedefineDiscriminatorKeyInOptional");
    
    // Invalid: mapping schema defines the discriminator key in optionalProperties
    String invalidSchema = """
      {
        "discriminator": "type",
        "mapping": {
          "person": {
            "optionalProperties": {
              "type": {"type": "string"},
              "name": {"type": "string"}
            }
          }
        }
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for discriminator with redefined key in optional"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("discriminator") || exception.getMessage().contains("type"), 
               "Error message should mention discriminator key conflict");
  }

  @Test
  void validDiscriminatorSchemaCompilesSuccessfully() {
    LOG.info(() -> "EXECUTING: validDiscriminatorSchemaCompilesSuccessfully");
    
    // Valid: discriminator with proper properties mapping
    String validSchema = """
      {
        "discriminator": "kind",
        "mapping": {
          "person": {
            "properties": {
              "name": {"type": "string"},
              "age": {"type": "int32"}
            }
          },
          "company": {
            "properties": {
              "name": {"type": "string"},
              "employees": {"type": "int32"}
            }
          }
        }
      }
      """;
    
    JsonValue schema = Json.parse(validSchema);
    JsonValue validInstance = Json.parse("{\"kind\":\"person\",\"name\":\"Alice\",\"age\":30}");
    JsonValue validCompanyInstance = Json.parse("{\"kind\":\"company\",\"name\":\"Acme\",\"employees\":100}");
    
    Jtd validator = new Jtd();
    
    // Should compile and validate successfully
    Jtd.Result result1 = validator.validate(schema, validInstance);
    Jtd.Result result2 = validator.validate(schema, validCompanyInstance);
    
    assertTrue(result1.isValid(), "Valid person instance should pass validation");
    assertTrue(result2.isValid(), "Valid company instance should pass validation");
    
    LOG.fine(() -> "Valid discriminator schema compiled and validated successfully");
  }

  @Test
  void discriminatorWithAdditionalPropertiesValidation() {
    LOG.info(() -> "EXECUTING: discriminatorWithAdditionalPropertiesValidation");
    
    // Valid: discriminator field should be exempt from additionalProperties validation
    String validSchema = """
      {
        "discriminator": "type",
        "mapping": {
          "data": {
            "properties": {
              "value": {"type": "string"}
            },
            "additionalProperties": false
          }
        }
      }
      """;
    
    JsonValue schema = Json.parse(validSchema);
    JsonValue validInstance = Json.parse("{\"type\":\"data\",\"value\":\"test\"}");
    JsonValue invalidInstance = Json.parse("{\"type\":\"data\",\"value\":\"test\",\"extra\":\"field\"}");
    
    Jtd validator = new Jtd();
    
    // Valid instance should pass
    Jtd.Result validResult = validator.validate(schema, validInstance);
    assertTrue(validResult.isValid(), "Instance with only discriminator and defined properties should be valid");
    
    // Invalid instance should fail due to extra field (not discriminator)
    Jtd.Result invalidResult = validator.validate(schema, invalidInstance);
    assertFalse(invalidResult.isValid(), "Instance with extra field should fail validation");
    
    LOG.fine(() -> "Discriminator exemption from additionalProperties works correctly");
  }

  @Test
  void emptySchemaCompilesAndAcceptsAllValues() {
    LOG.info(() -> "EXECUTING: emptySchemaCompilesAndAcceptsAllValues");
    
    // Valid: empty schema {} should accept any JSON value
    String emptySchema = "{}";
    JsonValue schema = Json.parse(emptySchema);
    
    Jtd validator = new Jtd();
    
    // Should accept various JSON values
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    assertTrue(validator.validate(schema, Json.parse("true")).isValid());
    assertTrue(validator.validate(schema, Json.parse("42")).isValid());
    assertTrue(validator.validate(schema, Json.parse("\"hello\"")).isValid());
    assertTrue(validator.validate(schema, Json.parse("[]")).isValid());
    assertTrue(validator.validate(schema, Json.parse("{}")).isValid());
    
    LOG.fine(() -> "Empty schema correctly accepts all JSON values");
  }

  @Test
  void nullableMustBeBoolean() {
    LOG.info(() -> "EXECUTING: nullableMustBeBoolean");
    
    // Invalid: nullable value is not a boolean
    String invalidSchema = """
      {
        "type": "string",
        "nullable": 123
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing nullable with non-boolean value: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for nullable with non-boolean value"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("nullable") && exception.getMessage().contains("boolean"), 
               "Error message should mention nullable must be boolean");
  }

  @Test
  void definitionsMustBeObject() {
    LOG.info(() -> "EXECUTING: definitionsMustBeObject");
    
    // Invalid: definitions is not an object
    String invalidSchema = """
      {
        "definitions": 123
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing definitions with non-object value: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for definitions with non-object value"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("definitions") && exception.getMessage().contains("object"), 
               "Error message should mention definitions must be object");
  }

  @Test
  void refWithoutDefinitionsShouldFail() {
    LOG.info(() -> "EXECUTING: refWithoutDefinitionsShouldFail");
    
    // Invalid: ref points to definition but no definitions object exists
    String invalidSchema = """
      {
        "ref": "foo"
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing ref without definitions: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for ref without definitions"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("ref") || exception.getMessage().contains("definition"), 
               "Error message should mention ref or definition issues");
  }

  @Test
  void refToNonExistentDefinitionShouldFail() {
    LOG.info(() -> "EXECUTING: refToNonExistentDefinitionShouldFail");
    
    // Invalid: ref points to non-existent definition
    String invalidSchema = """
      {
        "definitions": {},
        "ref": "foo"
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing ref to non-existent definition: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for ref to non-existent definition"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("ref") || exception.getMessage().contains("definition"), 
               "Error message should mention ref or definition issues");
  }

  @Test
  void subSchemaRefToNonExistentDefinitionShouldFail() {
    LOG.info(() -> "EXECUTING: subSchemaRefToNonExistentDefinitionShouldFail");
    
    // Invalid: sub-schema ref points to non-existent definition
    String invalidSchema = """
      {
        "definitions": {},
        "elements": {
          "ref": "foo"
        }
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing sub-schema ref to non-existent definition: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for sub-schema ref to non-existent definition"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("ref") || exception.getMessage().contains("definition"), 
               "Error message should mention ref or definition issues");
  }

  @Test
  void invalidTypeStringValueShouldFail() {
    LOG.info(() -> "EXECUTING: invalidTypeStringValueShouldFail");
    
    // Invalid: type value is not a valid primitive type
    String invalidSchema = """
      {
        "type": "foo"
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing invalid type string value: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for invalid type string value"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("type") || exception.getMessage().contains("unknown"), 
               "Error message should mention invalid type");
  }

  @Test
  void enumWithDuplicatesShouldFail() {
    LOG.info(() -> "EXECUTING: enumWithDuplicatesShouldFail");
    
    // Invalid: enum contains duplicate values
    String invalidSchema = """
      {
        "enum": ["foo", "bar", "foo"]
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing enum with duplicates: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for enum with duplicates"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("enum") || exception.getMessage().contains("duplicate"), 
               "Error message should mention enum duplicates");
  }

  @Test
  void elementsWithAdditionalPropertiesShouldFail() {
    LOG.info(() -> "EXECUTING: elementsWithAdditionalPropertiesShouldFail");
    
    // Invalid: elements form with additionalProperties (form-specific property mixing)
    String invalidSchema = """
      {
        "elements": {},
        "additionalProperties": true
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing elements with additionalProperties: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for elements with additionalProperties"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("form") || exception.getMessage().contains("additionalProperties"), 
               "Error message should mention form mixing or additionalProperties");
  }

  @Test
  void unknownSchemaKeysCauseCompilationFailure() {
    
    // Invalid: schema contains unknown keys
    String invalidSchema = """
      {
        "type": "string",
        "unknownKey": "value"
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for schema with unknown keys"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("unknown"), 
               "Error message should mention unknown keys");
  }

  @Test
  void propertiesAndOptionalPropertiesKeyOverlapShouldFail() {
    LOG.info(() -> "EXECUTING: propertiesAndOptionalPropertiesKeyOverlapShouldFail");
    
    // Invalid: same key defined in both properties and optionalProperties
    String invalidSchema = """
      {
        "properties": {
          "name": {"type": "string"}
        },
        "optionalProperties": {
          "name": {"type": "string"}
        }
      }
      """;
    
    JsonValue schema = Json.parse(invalidSchema);
    Jtd validator = new Jtd();
    
    LOG.fine(() -> "Testing properties and optionalProperties key overlap: " + schema);
    
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> validator.compile(schema),
      "Expected compilation to fail for overlapping keys"
    );
    
    LOG.fine(() -> "Compilation failed as expected: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("Key") && exception.getMessage().contains("name"), 
               "Error message should mention overlapping key 'name'");
  }

  @Test
  void elementsWithNestedDefinitionsShouldWork() {
    LOG.info(() -> "EXECUTING: elementsWithNestedDefinitionsShouldWork");
    
    // Valid: elements schema with nested definitions
    String validSchema = """
      {
        "definitions": {
          "item": {"type": "string"}
        },
        "elements": {
          "ref": "item"
        }
      }
      """;
    
    JsonValue schema = Json.parse(validSchema);
    JsonValue validInstance = Json.parse("[\"hello\", \"world\"]");
    
    Jtd validator = new Jtd();
    
    // Should compile and validate successfully
    Jtd.Result result = validator.validate(schema, validInstance);
    
    assertTrue(result.isValid(), "Elements with nested definitions should validate successfully");
    LOG.fine(() -> "Elements with nested definitions compiled and validated successfully");
  }

  @Test
  void propertiesWithNestedDefinitionsShouldWork() {
    LOG.info(() -> "EXECUTING: propertiesWithNestedDefinitionsShouldWork");
    
    // Valid: properties schema with nested definitions
    String validSchema = """
      {
        "definitions": {
          "name": {"type": "string"}
        },
        "properties": {
          "firstName": {"ref": "name"},
          "lastName": {"ref": "name"}
        }
      }
      """;
    
    JsonValue schema = Json.parse(validSchema);
    JsonValue validInstance = Json.parse("{\"firstName\":\"John\",\"lastName\":\"Doe\"}");
    
    Jtd validator = new Jtd();
    
    // Should compile and validate successfully
    Jtd.Result result = validator.validate(schema, validInstance);
    
    assertTrue(result.isValid(), "Properties with nested definitions should validate successfully");
    LOG.fine(() -> "Properties with nested definitions compiled and validated successfully");
  }

  @Test
  void optionalPropertiesWithNestedDefinitionsShouldWork() {
    LOG.info(() -> "EXECUTING: optionalPropertiesWithNestedDefinitionsShouldWork");
    
    // Valid: optionalProperties schema with nested definitions
    String validSchema = """
      {
        "definitions": {
          "address": {
            "properties": {
              "street": {"type": "string"},
              "city": {"type": "string"}
            }
          }
        },
        "optionalProperties": {
          "homeAddress": {"ref": "address"},
          "workAddress": {"ref": "address"}
        }
      }
      """;
    
    JsonValue schema = Json.parse(validSchema);
    JsonValue validInstance = Json.parse("{\"homeAddress\":{\"street\":\"123 Main St\",\"city\":\"Anytown\"}}");
    
    Jtd validator = new Jtd();
    
    // Should compile and validate successfully
    Jtd.Result result = validator.validate(schema, validInstance);
    
    assertTrue(result.isValid(), "OptionalProperties with nested definitions should validate successfully");
    LOG.fine(() -> "OptionalProperties with nested definitions compiled and validated successfully");
  }

  @Test
  void valuesWithNestedDefinitionsShouldWork() {
    LOG.info(() -> "EXECUTING: valuesWithNestedDefinitionsShouldWork");
    
    // Valid: values schema with nested definitions
    String validSchema = """
      {
        "definitions": {
          "user": {
            "properties": {
              "name": {"type": "string"}
            }
          }
        },
        "values": {
          "ref": "user"
        }
      }
      """;
    
    JsonValue schema = Json.parse(validSchema);
    JsonValue validInstance = Json.parse("{\"user1\":{\"name\":\"Alice\"},\"user2\":{\"name\":\"Bob\"}}");
    
    Jtd validator = new Jtd();
    
    // Should compile and validate successfully
    Jtd.Result result = validator.validate(schema, validInstance);
    
    assertTrue(result.isValid(), "Values with nested definitions should validate successfully");
    LOG.fine(() -> "Values with nested definitions compiled and validated successfully");
  }

  @Test
  void discriminatorMappingWithNestedDefinitionsShouldWork() {
    LOG.info(() -> "EXECUTING: discriminatorMappingWithNestedDefinitionsShouldWork");
    
    // Valid: discriminator mapping with nested definitions
    String validSchema = """
      {
        "definitions": {
          "personName": {"type": "string"}
        },
        "discriminator": "kind",
        "mapping": {
          "person": {
            "properties": {
              "name": {"ref": "personName"}
            }
          }
        }
      }
      """;
    
    JsonValue schema = Json.parse(validSchema);
    JsonValue validInstance = Json.parse("{\"kind\":\"person\",\"name\":\"Alice\"}");
    
    Jtd validator = new Jtd();
    
    // Should compile and validate successfully
    Jtd.Result result = validator.validate(schema, validInstance);
    
    assertTrue(result.isValid(), "Discriminator mapping with nested definitions should validate successfully");
    LOG.fine(() -> "Discriminator mapping with nested definitions compiled and validated successfully");
  }
}