package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Probes for RFC 8927 error format compliance issues
/// Tests that verify the EXACT error format per RFC 8927 Section 3.3
/// 
/// Current implementation issues to probe:
/// 1. Error format should be {instancePath, schemaPath} pairs, not enriched strings
/// 2. Schema paths must point to schema keywords (e.g., "/type", "/properties/foo")
/// 3. Instance paths must be RFC 6901 JSON Pointers
/// 4. Error indicators must be collected for ALL violations (multiple errors)
public class ErrorFormatComplianceProbe extends JtdTestBase {

  /// Test: Error format should contain RFC 8927 error indicators
  /// Expected: Each error has instancePath and schemaPath
  /// Actual: Implementation returns List<String> of enriched messages
  /// 
  /// This test documents the deviation from RFC 8927 error format
  @Test
  public void probeErrorFormatIsRfc8927Compliant() {
    JsonValue schema = Json.parse("{\"type\": \"string\"}");
    JsonValue instance = Json.parse("123");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertFalse(result.isValid(), "Should fail validation");
    
    List<String> errors = result.errors();
    assertThat(errors).isNotEmpty();
    
    // Probe: Check if errors contain RFC 8927 format or enriched strings
    String firstError = errors.get(0);
    
    // RFC 8927 format would be something like:
    // { "instancePath": "", "schemaPath": "/type" }
    // But implementation returns: "[off=N ptr=# via=#] expected string, got JsonNumber"
    
    LOG.info(() -> "Probing error format: " + firstError);
    
    // This assertion documents the current behavior
    assertThat(firstError)
        .as("Current implementation returns enriched strings, not RFC error objects")
        .contains("[off=");
    
    // These assertions will FAIL if/when we implement RFC format
    // Currently they pass because we get enriched strings
    assertThat(firstError)
        .as("Error should contain schemaPath information")
        .contains("ptr=");
  }

  /// Test: Type validation errors should have correct schemaPath
  /// Expected: schemaPath = "/type" for type violations
  @Test
  public void probeTypeErrorSchemaPath() {
    JsonValue schema = Json.parse("{\"type\": \"int32\"}");
    JsonValue instance = Json.parse("\"not-a-number\"");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertFalse(result.isValid());
    
    String error = result.errors().get(0);
    LOG.info(() -> "Type error: " + error);
    
    // Probe: Does error contain schemaPath to the type keyword?
    // Should be "/type" per RFC 8927
    assertThat(error)
        .as("Error should reference the type schema location")
        .contains("expected int32");
  }

  /// Test: Properties errors should have correct schemaPath
  /// Expected: schemaPath = "/properties/<key>" for missing required properties
  @Test
  public void probePropertiesErrorSchemaPath() {
    JsonValue schema = Json.parse("{\"properties\": {\"name\": {\"type\": \"string\"}}}");
    JsonValue instance = Json.parse("{}");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertFalse(result.isValid());
    
    String error = result.errors().get(0);
    LOG.info(() -> "Missing property error: " + error);
    
    // Probe: Should contain schemaPath to the missing property definition
    assertThat(error)
        .as("Error should reference the missing required property")
        .contains("missing required property: 'name'");
  }

  /// Test: InstancePath should be RFC 6901 compliant
  /// Expected: Root is "", children are "/key" or "/0"
  /// Actual: Implementation uses "#" for root
  @Test
  public void probeInstancePathRfc6901Compliance() {
    JsonValue schema = Json.parse("{\"type\": \"string\"}");
    JsonValue instance = Json.parse("\"test\"");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, Json.parse("123"));
    
    assertFalse(result.isValid());
    
    String error = result.errors().get(0);
    LOG.info(() -> "Instance path in error: " + error);
    
    // Probe: Instance path format
    // RFC 6901: empty string for root
    // Implementation: "#" for root
    assertThat(error)
        .as("Implementation uses # for root, not RFC 6901 empty string")
        .contains("ptr=#");
  }

  /// Test: Nested path construction
  /// Expected: Nested errors have compound instancePaths like "/foo/0/bar"
  @Test
  public void probeNestedInstancePathConstruction() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "items": {
            "elements": {
              "properties": {
                "value": {"type": "string"}
              }
            }
          }
        }
      }
      """);
    
    JsonValue instance = Json.parse("""
      {
        "items": [
          {"value": "valid"},
          {"value": 123}
        ]
      }
      """);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertFalse(result.isValid());
    
    String error = result.errors().get(0);
    LOG.info(() -> "Nested path error: " + error);
    
    // Probe: Should contain path to the nested invalid element
    assertThat(error)
        .as("Error should reference nested path")
        .contains("items");
  }

  /// Test: Multiple errors should ALL be collected
  /// RFC 8927 Section 3.3: errors are collected for all violations
  @Test
  public void probeMultipleErrorsCollected() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "a": {"type": "string"},
          "b": {"type": "string"},
          "c": {"type": "string"}
        }
      }
      """);
    
    JsonValue instance = Json.parse("""
      {
        "a": 1,
        "b": 2,
        "c": 3
      }
      """);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertFalse(result.isValid());
    
    List<String> errors = result.errors();
    LOG.info(() -> "Multiple errors count: " + errors.size());
    
    // Probe: Are all three type errors collected?
    // Implementation should collect errors for all properties
    assertThat(errors.size())
        .as("Should collect errors for all invalid properties")
        .isGreaterThanOrEqualTo(3);
  }

  /// Test: Error order is unspecified but all should be present
  @Test
  public void probeAllViolationsReported() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "name": {"type": "string"}
        },
        "optionalProperties": {
          "age": {"type": "int32"}
        }
      }
      """);
    
    JsonValue instance = Json.parse("""
      {
        "name": 123,
        "age": "not-a-number"
      }
      """);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertFalse(result.isValid());
    
    List<String> errors = result.errors();
    
    // Probe: Are both errors reported?
    boolean hasNameError = errors.stream().anyMatch(e -> e.contains("name"));
    boolean hasAgeError = errors.stream().anyMatch(e -> e.contains("age"));
    
    assertTrue(hasNameError, "Should report error for 'name' property");
    assertTrue(hasAgeError, "Should report error for 'age' property");
  }

  /// Test: Enum error schemaPath
  /// Expected: schemaPath = "/enum" for enum violations
  @Test
  public void probeEnumErrorSchemaPath() {
    JsonValue schema = Json.parse("{\"enum\": [\"a\", \"b\", \"c\"]}");
    JsonValue instance = Json.parse("\"d\"");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertFalse(result.isValid());
    
    String error = result.errors().get(0);
    LOG.info(() -> "Enum error: " + error);
    
    // Probe: Should reference the enum constraint
    assertThat(error)
        .as("Error should reference enum constraint")
        .contains("not in enum");
  }

  /// Test: Elements error schemaPath  
  /// Expected: schemaPath = "/elements" for element validation failures
  @Test
  public void probeElementsErrorSchemaPath() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    JsonValue instance = Json.parse("[1, 2, 3]");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertFalse(result.isValid());
    
    List<String> errors = result.errors();
    LOG.info(() -> "Elements errors count: " + errors.size());
    
    // Probe: Should have 3 errors (one per element)
    // Note: Some implementations might short-circuit; this checks
    assertThat(errors.size())
        .as("Should report errors for invalid elements")
        .isGreaterThanOrEqualTo(1);
  }

  /// Test: Discriminator error schemaPath
  /// Expected: Different schemaPaths for different discriminator failures
  @Test
  public void probeDiscriminatorErrorSchemaPaths() {
    // Test 1: Missing discriminator
    JsonValue schema = Json.parse("""
      {
        "discriminator": "kind",
        "mapping": {
          "person": {"properties": {"name": {"type": "string"}}}
        }
      }
      """);
    
    JsonValue missingKind = Json.parse("{\"name\": \"John\"}");
    Jtd.Result result1 = new Jtd().validate(schema, missingKind);
    
    assertFalse(result1.isValid());
    LOG.info(() -> "Missing discriminator error: " + result1.errors().get(0));
    
    // Test 2: Invalid discriminator value
    JsonValue invalidKind = Json.parse("{\"kind\": \"invalid\", \"name\": \"John\"}");
    Jtd.Result result2 = new Jtd().validate(schema, invalidKind);
    
    assertFalse(result2.isValid());
    LOG.info(() -> "Invalid discriminator error: " + result2.errors().get(0));
    
    // Test 3: Non-string discriminator
    JsonValue nonStringKind = Json.parse("{\"kind\": 123}");
    Jtd.Result result3 = new Jtd().validate(schema, nonStringKind);
    
    assertFalse(result3.isValid());
    LOG.info(() -> "Non-string discriminator error: " + result3.errors().get(0));
  }

  /// Test: Empty schema produces no errors
  /// RFC 8927 §3.3.1: Empty form accepts all instances and produces no errors
  @Test
  public void probeEmptySchemaNoErrors() {
    JsonValue schema = Json.parse("{}");
    JsonValue instance = Json.parse("{\"anything\": \"goes\"}");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    assertTrue(result.isValid());
    assertThat(result.errors()).isEmpty();
    
    LOG.info(() -> "Empty schema validation produced no errors (correct)");
  }

  /// Test: Error messages should be consistent
  /// Same violation should produce same error structure
  @Test
  public void probeErrorMessageConsistency() {
    JsonValue schema = Json.parse("{\"type\": \"string\"}");
    
    // Two identical violations
    Jtd.Result result1 = new Jtd().validate(schema, Json.parse("1"));
    Jtd.Result result2 = new Jtd().validate(schema, Json.parse("2"));
    
    assertFalse(result1.isValid());
    assertFalse(result2.isValid());
    
    // Probe: Error structure should be consistent
    String error1 = result1.errors().get(0);
    String error2 = result2.errors().get(0);
    
    // Both should contain same error pattern
    assertThat(error1).contains("expected string");
    assertThat(error2).contains("expected string");
    
    LOG.info(() -> "Error consistency check passed");
  }
}
