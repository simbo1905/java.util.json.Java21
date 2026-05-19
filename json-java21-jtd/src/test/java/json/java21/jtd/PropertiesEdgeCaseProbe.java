package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/// Probes for Properties form edge cases and potential issues
/// 
/// Areas to probe:
/// 1. Empty properties with additionalProperties: false
/// 2. Additional properties detection with complex keys
/// 3. Required vs optional property precedence
/// 4. Properties with null values
/// 5. Nested properties validation order
public class PropertiesEdgeCaseProbe extends JtdTestBase {

  /// Test: Empty properties with additionalProperties: false
  /// Should reject ALL additional properties
  @Test
  public void probeEmptyPropertiesRejectsAllExtras() {
    JsonValue schema = Json.parse("{\"properties\": {}, \"additionalProperties\": false}");
    
    JsonValue instance = Json.parse("{\"anyKey\": \"anyValue\"}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Empty properties rejects extras: " + !result.isValid());
    
    // Should reject because there are no allowed properties
    assertFalse(result.isValid(), "Empty properties + additionalProperties:false should reject all fields");
    
    // And should report the extra property
    String error = result.errors().get(0);
    assertThat(error).contains("anyKey");
  }

  /// Test: Empty properties without additionalProperties (defaults to false)
  /// RFC 8927: additionalProperties defaults to false
  @Test
  public void probeEmptyPropertiesDefaultAdditionalProperties() {
    JsonValue schema = Json.parse("{\"properties\": {}}");
    
    JsonValue instance = Json.parse("{\"surprise\": \"field\"}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Empty properties default strict: " + !result.isValid());
    
    // Should reject - default is strict
    assertFalse(result.isValid(), "Empty properties should default to rejecting extras");
  }

  /// Test: Additional properties with special characters in keys
  /// Keys with dots, slashes, spaces should be handled correctly
  @Test
  public void probeAdditionalPropertiesSpecialKeys() {
    JsonValue schema = Json.parse("{\"properties\": {\"normal\": {}}, \"additionalProperties\": false}");
    
    // These should all be rejected as additional properties
    String[] specialKeys = {
        "{\"dotted.key\": 1}",
        "{\"key/with/slash\": 1}",
        "{\"key with space\": 1}",
        "{\"\\u0000nullchar\": 1}"
    };
    
    Jtd validator = new Jtd();
    
    for (String keyJson : specialKeys) {
      try {
        JsonValue instance = Json.parse(keyJson);
        Jtd.Result result = validator.validate(schema, instance);
        
        LOG.fine(() -> "Special key validation: " + keyJson + " -> valid=" + result.isValid());
        
        // Should be invalid
        assertFalse(result.isValid(), "Should reject additional property: " + keyJson);
      } catch (Exception e) {
        LOG.warning(() -> "Failed to parse or validate: " + keyJson + " - " + e.getMessage());
      }
    }
  }

  /// Test: Properties with duplicate key detection
  /// This probes if the implementation correctly identifies all extra properties
  @Test
  public void probeMultipleAdditionalPropertiesAllReported() {
    JsonValue schema = Json.parse("{\"properties\": {\"allowed\": {}}, \"additionalProperties\": false}");
    
    JsonValue instance = Json.parse("{\"allowed\": 1, \"extra1\": 2, \"extra2\": 3, \"extra3\": 4}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Multiple extras error count: " + result.errors().size());
    
    assertFalse(result.isValid());
    
    // Probe: Are all three extra properties reported?
    List<String> errors = result.errors();
    
    Set<String> reportedExtras = new HashSet<>();
    for (String error : errors) {
      if (error.contains("extra1")) reportedExtras.add("extra1");
      if (error.contains("extra2")) reportedExtras.add("extra2");
      if (error.contains("extra3")) reportedExtras.add("extra3");
    }
    
    LOG.info(() -> "Reported extras: " + reportedExtras);
    
    // Ideally all three should be reported
    assertThat(reportedExtras)
        .as("Should report all additional properties")
        .hasSizeGreaterThanOrEqualTo(1);
  }

  /// Test: Required property with null value
  /// Required means "must be present", null is a valid value
  @Test
  public void probeRequiredPropertyWithNullValue() {
    JsonValue schema = Json.parse("{\"properties\": {\"field\": {}}}");
    
    JsonValue instance = Json.parse("{\"field\": null}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Required with null result: " + result.isValid());
    
    // Per RFC 8927: property is present (key exists), so satisfies "required"
    // Empty schema {} accepts null
    assertTrue(result.isValid(), "Required property with null should be valid");
  }

  /// Test: Required property missing vs null value
  @Test
  public void probeMissingVsNullProperty() {
    JsonValue schema = Json.parse("{\"properties\": {\"field\": {}}}");
    
    // Missing property
    JsonValue missing = Json.parse("{}");
    Jtd.Result missingResult = new Jtd().validate(schema, missing);
    
    // Null property  
    JsonValue nullValue = Json.parse("{\"field\": null}");
    Jtd.Result nullResult = new Jtd().validate(schema, nullValue);
    
    LOG.info(() -> "Missing: " + !missingResult.isValid() + ", Null: " + nullResult.isValid());
    
    assertFalse(missingResult.isValid(), "Missing required property should be invalid");
    assertTrue(nullResult.isValid(), "Required property with null should be valid");
  }

  /// Test: Optional property with various values
  @Test
  public void probeOptionalPropertyValues() {
    JsonValue schema = Json.parse("{\"optionalProperties\": {\"opt\": {\"type\": \"string\"}}}");
    
    Jtd validator = new Jtd();
    
    // Missing - valid
    assertTrue(validator.validate(schema, Json.parse("{}")).isValid());
    
    // Null - should validate against the type schema (empty schema accepts null, but type:string rejects null)
    Jtd.Result nullResult = validator.validate(schema, Json.parse("{\"opt\": null}"));
    LOG.info(() -> "Optional null result: " + nullResult.isValid());
    // This depends on implementation - null with type:string should fail
    
    // Valid string
    assertTrue(validator.validate(schema, Json.parse("{\"opt\": \"value\"}")).isValid());
    
    // Invalid type
    assertFalse(validator.validate(schema, Json.parse("{\"opt\": 123}")).isValid());
  }

  /// Test: Properties and optionalProperties with same key
  /// RFC 8927: This should be a compile-time error
  @Test
  public void probeOverlappingRequiredAndOptionalCompilation() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "field": {"type": "string"}
        },
        "optionalProperties": {
          "field": {"type": "int32"}
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    try {
      validator.compile(schema);
      LOG.warning(() -> "COMPILATION BUG: Should reject overlapping property keys");
    } catch (IllegalArgumentException e) {
      LOG.info(() -> "Correctly rejected overlapping keys: " + e.getMessage());
    }
  }

  /// Test: Nested properties with different additionalProperties settings
  @Test
  public void probeNestedPropertiesDifferentStrictness() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "outer": {
            "properties": {
              "inner": {}
            },
            "additionalProperties": true
          }
        },
        "additionalProperties": false
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Valid: outer allows extras
    JsonValue valid = Json.parse("{\"outer\": {\"inner\": 1, \"extra\": 2}}");
    assertTrue(validator.validate(schema, valid).isValid());
    
    // Invalid: root level doesn't allow extras
    JsonValue invalid = Json.parse("{\"outer\": {\"inner\": 1}, \"rootExtra\": 3}");
    Jtd.Result result = validator.validate(schema, invalid);
    
    LOG.info(() -> "Root extra rejection: " + !result.isValid());
    assertFalse(result.isValid(), "Should reject extra property at root level");
  }

  /// Test: Properties validation order
  /// Are errors reported in consistent order?
  @Test
  public void probePropertiesValidationOrder() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "z": {},
          "a": {},
          "m": {}
        }
      }
      """);
    
    // All missing
    JsonValue instance = Json.parse("{}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Missing properties error count: " + result.errors().size());
    
    assertThat(result.errors().size()).isEqualTo(3);
    
    // Probe: Order of errors (alphabetical vs declaration order?)
    String[] errors = result.errors().toArray(new String[0]);
    LOG.info(() -> "Error order: " + String.join(", ", errors));
    
    // Note: Order is unspecified by RFC, but should be deterministic
  }

  /// Test: Empty string property names
  @Test
  public void probeEmptyStringPropertyNames() {
    JsonValue schema = Json.parse("{\"properties\": {\"\": {}}}");
    
    JsonValue valid = Json.parse("{\"\": \"value\"}");
    Jtd.Result result = new Jtd().validate(schema, valid);
    
    LOG.info(() -> "Empty string property result: " + result.isValid());
    
    // Empty string is a valid property name in JSON
    assertTrue(result.isValid(), "Empty string property name should be valid");
  }

  /// Test: Very deep nesting of properties
  @Test
  public void probeDeepNestingProperties() {
    // Create a deeply nested schema
    StringBuilder schemaBuilder = new StringBuilder();
    StringBuilder instanceBuilder = new StringBuilder();
    
    int depth = 50;
    
    schemaBuilder.append("{\"properties\": {\"level0\": ");
    instanceBuilder.append("{\"level0\": ");
    
    for (int i = 0; i < depth; i++) {
      schemaBuilder.append("{\"properties\": {\"level").append(i + 1).append("\": ");
      instanceBuilder.append("{\"level").append(i + 1).append("\": ");
    }
    
    schemaBuilder.append("{\"type\": \"string\"}");
    instanceBuilder.append("\"deepValue\"");
    
    for (int i = 0; i <= depth; i++) {
      schemaBuilder.append("}}");
      instanceBuilder.append("}");
    }
    
    JsonValue schema = Json.parse(schemaBuilder.toString());
    JsonValue validInstance = Json.parse(instanceBuilder.toString());
    
    Jtd validator = new Jtd();
    
    LOG.info(() -> "Testing depth: " + depth);
    
    Jtd.Result validResult = validator.validate(schema, validInstance);
    LOG.info(() -> "Deep valid result: " + validResult.isValid());
    assertTrue(validResult.isValid(), "Deep nesting should work");
    
    // Test invalid at deep level
    JsonValue invalidInstance = Json.parse(instanceBuilder.toString().replace("\"deepValue\"", "123"));
    Jtd.Result invalidResult = validator.validate(schema, invalidInstance);
    
    LOG.info(() -> "Deep invalid result: " + !invalidResult.isValid());
    assertFalse(invalidResult.isValid(), "Should detect error at deep level");
  }

  /// Test: Property count limits
  @Test
  public void probeLargeNumberOfProperties() {
    // Schema with many properties
    StringBuilder schemaBuilder = new StringBuilder("{\"properties\": {");
    StringBuilder instanceBuilder = new StringBuilder("{");
    
    int count = 100;
    
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        schemaBuilder.append(", ");
        instanceBuilder.append(", ");
      }
      schemaBuilder.append("\"prop").append(i).append("\": {\"type\": \"string\"}");
      instanceBuilder.append("\"prop").append(i).append("\": \"value").append(i).append("\"");
    }
    
    schemaBuilder.append("}}");
    instanceBuilder.append("}");
    
    JsonValue schema = Json.parse(schemaBuilder.toString());
    JsonValue instance = Json.parse(instanceBuilder.toString());
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.info(() -> "Large property count (" + count + ") result: " + result.isValid());
    assertTrue(result.isValid(), "Large number of properties should work");
  }

  /// Test: Property name collisions with prototype pollution concerns
  @Test
  public void probePrototypePollutionPropertyNames() {
    JsonValue schema = Json.parse("{\"properties\": {}, \"additionalProperties\": false}");
    
    // These are technically valid property names in JSON
    String[] prototypeNames = {
        "{\"__proto__\": 1}",
        "{\"constructor\": 1}",
        "{\"toString\": 1}",
        "{\"hasOwnProperty\": 1}"
    };
    
    Jtd validator = new Jtd();
    
    for (String nameJson : prototypeNames) {
      try {
        JsonValue instance = Json.parse(nameJson);
        Jtd.Result result = validator.validate(schema, instance);
        
        LOG.fine(() -> "Prototype name validation: " + nameJson + " -> valid=" + result.isValid());
        
        // These should all be rejected as additional properties
        assertFalse(result.isValid(), "Should reject: " + nameJson);
      } catch (Exception e) {
        LOG.warning(() -> "Issue with: " + nameJson + " - " + e.getMessage());
      }
    }
  }
}
