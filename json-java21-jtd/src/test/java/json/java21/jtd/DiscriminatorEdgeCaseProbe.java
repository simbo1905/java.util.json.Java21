package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/// Probes for Discriminator schema edge cases and potential issues
/// 
/// Areas to probe:
/// 1. Discriminator field handling when present vs missing
/// 2. Discriminator field exemption from additionalProperties
/// 3. Discriminator in optionalProperties vs properties
/// 4. Empty discriminator object validation
/// 5. Discriminator with empty mapping value
public class DiscriminatorEdgeCaseProbe extends JtdTestBase {

  /// Test: Missing discriminator field should produce correct error
  /// RFC 8927 §3.3.8: Step 2 - check if discriminator property exists
  /// This should be distinguishable from "discriminator present but not string"
  @Test
  public void probeMissingVsNonStringDiscriminatorErrors() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "kind",
        "mapping": {
          "type1": {"properties": {"value": {"type": "string"}}}
        }
      }
      """);
    
    // Case 1: Missing discriminator
    JsonValue missing = Json.parse("{\"value\": \"test\"}");
    Jtd.Result missingResult = new Jtd().validate(schema, missing);
    
    assertFalse(missingResult.isValid(), "Should fail when discriminator missing");
    String missingError = missingResult.errors().get(0);
    LOG.info(() -> "Missing discriminator error: " + missingError);
    
    // Case 2: Discriminator present but not string
    JsonValue nonString = Json.parse("{\"kind\": 123}");
    Jtd.Result nonStringResult = new Jtd().validate(schema, nonString);
    
    assertFalse(nonStringResult.isValid(), "Should fail when discriminator not string");
    String nonStringError = nonStringResult.errors().get(0);
    LOG.info(() -> "Non-string discriminator error: " + nonStringError);
    
    // Probe: Are these errors distinguishable?
    // RFC says they should have different schemaPaths
    // Missing: schema at discriminator form level
    // Non-string: schemaPath "/discriminator"
    
    // Implementation currently conflates these (both say "must be a string")
    // This test documents that behavior
  }

  /// Test: Discriminator field should be exempt from additionalProperties only once
  /// RFC 8927 §2.2.8: "The discriminator tag is exempt from additionalProperties enforcement"
  @Test
  public void probeDiscriminatorExemptionFromAdditionalProperties() {
    JsonValue schema = Json.parse("""
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
      """);
    
    // Valid: discriminator + defined property
    JsonValue valid = Json.parse("{\"type\": \"data\", \"value\": \"test\"}");
    Jtd.Result validResult = new Jtd().validate(schema, valid);
    assertTrue(validResult.isValid(), "Should accept discriminator + defined properties");
    
    // Invalid: discriminator + extra property
    JsonValue invalid = Json.parse("{\"type\": \"data\", \"value\": \"test\", \"extra\": \"bad\"}");
    Jtd.Result invalidResult = new Jtd().validate(schema, invalid);
    
    assertFalse(invalidResult.isValid(), "Should reject extra properties beyond discriminator");
    LOG.info(() -> "Additional property error: " + invalidResult.errors().get(0));
    
    // Probe: Error should mention 'extra', not 'type'
    String error = invalidResult.errors().get(0);
    assertThat(error).as("Error should mention the extra property, not discriminator")
        .contains("extra");
    assertThat(error).as("Error should NOT mention the discriminator field")
        .doesNotContain("type");
  }

  /// Test: Discriminator field defined in mapping's properties
  /// RFC 8927 §2.2.8: Discriminator key cannot be redefined in properties
  @Test
  public void probeDiscriminatorKeyRedefinitionCompilation() {
    // This should FAIL compilation per RFC 8927
    JsonValue schema = Json.parse("""
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
      """);
    
    Jtd validator = new Jtd();
    
    // Probe: Does compilation reject this?
    // Per RFC: "Mapped schemas cannot define the discriminator key in properties"
    try {
      validator.compile(schema);
      LOG.warning(() -> "COMPILATION BUG: Should have rejected discriminator key redefinition");
      // If we get here, compilation allowed an invalid schema
    } catch (IllegalArgumentException e) {
      LOG.info(() -> "Correctly rejected discriminator key redefinition: " + e.getMessage());
    }
  }

  /// Test: Discriminator field defined in mapping's optionalProperties
  /// RFC 8927 §2.2.8: Discriminator key cannot be redefined in optionalProperties either
  @Test
  public void probeDiscriminatorKeyInOptionalPropertiesCompilation() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "type",
        "mapping": {
          "data": {
            "optionalProperties": {
              "type": {"type": "string"}
            }
          }
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Probe: Does compilation reject this?
    try {
      validator.compile(schema);
      LOG.warning(() -> "COMPILATION BUG: Should have rejected discriminator key in optionalProperties");
    } catch (IllegalArgumentException e) {
      LOG.info(() -> "Correctly rejected discriminator key in optionalProperties: " + e.getMessage());
    }
  }

  /// Test: Discriminator with empty properties mapping
  /// Valid per RFC 8927: mapping value can be {"properties": {}}
  @Test
  public void probeDiscriminatorWithEmptyPropertiesMapping() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "kind",
        "mapping": {
          "empty": {"properties": {}}
        }
      }
      """);
    
    JsonValue instance = Json.parse("{\"kind\": \"empty\"}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Empty properties mapping result: " + result.isValid());
    
    // Per RFC 8927: Empty properties should accept any properties except
    // additional ones (if additionalProperties is false, default is false)
    // But discriminator field IS the only allowed field
    
    // Probe: Is this valid?
    // Should be valid because discriminator is allowed and there are no required properties
    if (!result.isValid()) {
      LOG.warning(() -> "VALIDATION BUG: Empty properties with discriminator should be valid, got: " + result.errors());
    }
  }

  /// Test: Discriminator with additionalProperties: true
  @Test
  public void probeDiscriminatorWithAdditionalPropertiesTrue() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "type",
        "mapping": {
          "open": {
            "properties": {},
            "additionalProperties": true
          }
        }
      }
      """);
    
    // Should accept any properties including extras
    JsonValue instance = Json.parse("{\"type\": \"open\", \"anything\": \"goes\"}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "AdditionalProperties: true result: " + result.isValid());
    
    // Probe: This should definitely be valid
    assertTrue(result.isValid(), "With additionalProperties: true, extra fields should be allowed");
  }

  /// Test: Nested discriminator in properties
  /// Discriminator inside a properties schema that is itself nested
  @Test
  public void probeNestedDiscriminator() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "data": {
            "discriminator": "kind",
            "mapping": {
              "a": {"properties": {"value": {"type": "string"}}},
              "b": {"properties": {"count": {"type": "int32"}}}
            }
          }
        }
      }
      """);
    
    JsonValue valid = Json.parse("{\"data\": {\"kind\": \"a\", \"value\": \"test\"}}");
    Jtd.Result result = new Jtd().validate(schema, valid);
    
    LOG.info(() -> "Nested discriminator result: " + result.isValid());
    assertTrue(result.isValid(), "Nested discriminator should work");
  }

  /// Test: Discriminator validation stops on first discriminator error
  /// RFC says if discriminator fails, we shouldn't validate variant schema
  @Test
  public void probeDiscriminatorErrorShortCircuitsVariantValidation() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "type",
        "mapping": {
          "data": {
            "properties": {
              "requiredField": {"type": "string"}
            }
          }
        }
      }
      """);
    
    // Invalid discriminator + missing required field
    JsonValue instance = Json.parse("{\"type\": \"unknown\", \"other\": \"value\"}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Errors for unknown discriminator: " + result.errors().size());
    
    // Should only report discriminator error, not missing required field error
    // because we never reached variant validation
    assertFalse(result.isValid());
    
    // Probe: Are there multiple errors or just the discriminator error?
    if (result.errors().size() > 1) {
      LOG.info(() -> "Implementation validates variant even with invalid discriminator");
    } else {
      LOG.info(() -> "Implementation correctly short-circuits on discriminator error");
    }
  }

  /// Test: Discriminator with null value
  @Test
  public void probeDiscriminatorWithNullValue() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "kind",
        "mapping": {
          "data": {"properties": {}}
        }
      }
      """);
    
    JsonValue instance = Json.parse("{\"kind\": null}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Discriminator with null result: " + result.isValid());
    
    // Null is not a string, so should fail
    assertFalse(result.isValid(), "Discriminator with null value should be invalid");
  }

  /// Test: Discriminator with empty string value
  @Test
  public void probeDiscriminatorWithEmptyString() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "kind",
        "mapping": {
          "": {"properties": {}}
        }
      }
      """);
    
    JsonValue instance = Json.parse("{\"kind\": \"\"}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Discriminator with empty string result: " + result.isValid());
    
    // Empty string is a valid key in the mapping, so this should be valid
    assertTrue(result.isValid(), "Empty string discriminator should work if in mapping");
  }

  /// Test: Multiple discriminator values with same required property name
  @Test
  public void probeDiscriminatorVariantsWithCommonProperties() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "type",
        "mapping": {
          "person": {
            "properties": {
              "name": {"type": "string"}
            }
          },
          "company": {
            "properties": {
              "name": {"type": "string"}
            }
          }
        }
      }
      """);
    
    // Both variants have a "name" property
    JsonValue person = Json.parse("{\"type\": \"person\", \"name\": \"Alice\"}");
    JsonValue company = Json.parse("{\"type\": \"company\", \"name\": \"Acme\"}");
    
    Jtd validator = new Jtd();
    
    Jtd.Result personResult = validator.validate(schema, person);
    Jtd.Result companyResult = validator.validate(schema, company);
    
    LOG.info(() -> "Person result: " + personResult.isValid());
    LOG.info(() -> "Company result: " + companyResult.isValid());
    
    assertTrue(personResult.isValid(), "Person variant should validate");
    assertTrue(companyResult.isValid(), "Company variant should validate");
  }

  /// Test: Discriminator with properties that conflict across variants
  /// Same property name but different types in different variants
  @Test
  public void probeDiscriminatorWithConflictingPropertyTypes() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "type",
        "mapping": {
          "version1": {
            "properties": {
              "value": {"type": "string"}
            }
          },
          "version2": {
            "properties": {
              "value": {"type": "int32"}
            }
          }
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    JsonValue v1Valid = Json.parse("{\"type\": \"version1\", \"value\": \"text\"}");
    JsonValue v1Invalid = Json.parse("{\"type\": \"version1\", \"value\": 123}");
    JsonValue v2Valid = Json.parse("{\"type\": \"version2\", \"value\": 456}");
    JsonValue v2Invalid = Json.parse("{\"type\": \"version2\", \"value\": \"text\"}");
    
    assertTrue(validator.validate(schema, v1Valid).isValid(), "v1 string should be valid");
    assertFalse(validator.validate(schema, v1Invalid).isValid(), "v1 int should be invalid");
    assertTrue(validator.validate(schema, v2Valid).isValid(), "v2 int should be valid");
    assertFalse(validator.validate(schema, v2Invalid).isValid(), "v2 string should be invalid");
  }
}
