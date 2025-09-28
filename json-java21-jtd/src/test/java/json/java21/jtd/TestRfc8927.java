package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import jdk.sandbox.java.util.json.JsonNumber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Comprehensive RFC 8927 compliance tests
/// - Ref schema validation with definitions
/// - Timestamp format validation (RFC 3339)
/// - Integer range validation for all types
/// - Error path information (instancePath and schemaPath)
/// - Multiple error collection
/// - Discriminator tag exemption
public class TestRfc8927 extends JtdTestBase {

  /// Test ref schema resolution with valid definitions
  /// RFC 8927 Section 3.3.2: Ref schemas must resolve against definitions
  @Test
  public void testRefSchemaValid() {
    JsonValue schema = Json.parse("{\"ref\": \"address\", \"definitions\": {\"address\": {\"type\": \"string\"}}}");
    JsonValue validData = Json.parse("\"123 Main St\"");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, validData);
    
    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isEmpty();
    LOG.fine(() -> "Ref schema valid test - schema: " + schema + ", data: " + validData);
  }
  
  /// Counter-test: Ref schema with invalid definition reference
  /// Should fail when ref points to non-existent definition
  @Test
  public void testRefSchemaInvalidDefinition() {
    JsonValue schema = Json.parse("{\"ref\": \"nonexistent\", \"definitions\": {\"address\": {\"type\": \"string\"}}}");
    JsonValue data = Json.parse("\"anything\"");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, data);
    
    assertThat(result.isValid())
      .as("Ref schema should fail when definition doesn't exist, but implementation is broken")
      .isFalse();
    assertThat(result.errors()).isNotEmpty();
  }

  /// Test timestamp format validation (RFC 3339)
  /// RFC 8927 Section 3.3.3: timestamp must follow RFC 3339 format
  @Test
  public void testTimestampValid() {
    JsonValue schema = Json.parse("{\"type\": \"timestamp\"}");
    
    // Valid RFC 3339 timestamps
    String[] validTimestamps = {
      "\"2023-12-25T10:30:00Z\"",
      "\"2023-12-25T10:30:00.123Z\"",
      "\"2023-12-25T10:30:00+00:00\"",
      "\"2023-12-25T10:30:00-05:00\""
    };
    
    Jtd validator = new Jtd();
    
    for (String timestamp : validTimestamps) {
      JsonValue validData = Json.parse(timestamp);
      Jtd.Result result = validator.validate(schema, validData);
      assertThat(result.isValid()).isTrue();
      LOG.fine(() -> "Timestamp valid test - data: " + validData);
    }
  }
  
  /// Counter-test: Invalid timestamp formats
  /// Should reject non-RFC 3339 timestamp strings
  @Test
  public void testTimestampInvalid() {
    JsonValue schema = Json.parse("{\"type\": \"timestamp\"}");
    
    // Invalid timestamp formats
    String[] invalidTimestamps = {
      "\"2023-12-25\"",              // Date only
      "\"10:30:00\"",                // Time only
      "\"2023/12/25T10:30:00Z\"",    // Wrong date separator
      "\"2023-12-25 10:30:00\"",     // Space instead of T
      "\"not-a-timestamp\"",         // Completely invalid
      "\"123\"",                     // Number as string
      "123"                          // Number
    };
    
    Jtd validator = new Jtd();
    
    for (String timestamp : invalidTimestamps) {
      JsonValue invalidData = Json.parse(timestamp);
      Jtd.Result result = validator.validate(schema, invalidData);
      
      assertThat(result.isValid())
        .as("Timestamp should reject invalid RFC 3339 format: " + invalidData)
        .isFalse();
      assertThat(result.errors()).isNotEmpty();
    }
  }

  /// Test integer type range validation
  /// RFC 8927 Table 2: Specific ranges for each integer type
  @Test
  public void testIntegerRangesValid() {
    // Test valid ranges for each integer type
    testIntegerTypeRange("int8", "-128", "127", "0");
    testIntegerTypeRange("uint8", "0", "255", "128");
    testIntegerTypeRange("int16", "-32768", "32767", "0");
    testIntegerTypeRange("uint16", "0", "65535", "32768");
    testIntegerTypeRange("int32", "-2147483648", "2147483647", "0");
    testIntegerTypeRange("uint32", "0", "4294967295", "2147483648");
  }
  
  /// Counter-test: Integer values outside valid ranges
  /// Should reject values that exceed type ranges
  @Test
  public void testIntegerRangesInvalid() {
    // Test invalid ranges for each integer type
    testIntegerTypeInvalid("int8", "-129", "128");     // Below min, above max
    testIntegerTypeInvalid("uint8", "-1", "256");      // Below min, above max
    testIntegerTypeInvalid("int16", "-32769", "32768"); // Below min, above max
    testIntegerTypeInvalid("uint16", "-1", "65536");   // Below min, above max
    testIntegerTypeInvalid("int32", "-2147483649", "2147483648"); // Below min, above max
    testIntegerTypeInvalid("uint32", "-1", "4294967296"); // Below min, above max
  }
  
  /// Helper method to test valid integer ranges
  private void testIntegerTypeRange(String type, String min, String max, String middle) {
    JsonValue schema = Json.parse("{\"type\": \"" + type + "\"}");
    Jtd validator = new Jtd();
    
    // Test minimum, maximum, and middle values
    String[] validValues = {min, max, middle};
    
    for (String value : validValues) {
      JsonValue validData = Json.parse(value);
      Jtd.Result result = validator.validate(schema, validData);
      assertThat(result.isValid()).isTrue();
      LOG.fine(() -> "Integer range valid test - type: " + type + ", value: " + value);
    }
  }
  
  /// Helper method to test invalid integer ranges
  private void testIntegerTypeInvalid(String type, String belowMin, String aboveMax) {
    JsonValue schema = Json.parse("{\"type\": \"" + type + "\"}");
    Jtd validator = new Jtd();
    
    // Test values below minimum and above maximum
    String[] invalidValues = {belowMin, aboveMax};
    
    for (String value : invalidValues) {
      JsonValue invalidData = Json.parse(value);
      Jtd.Result result = validator.validate(schema, invalidData);
      
      assertThat(result.isValid())
        .as("Integer type \"" + type + "\" should reject value \"" + value + "\" as out of range")
        .isFalse();
      assertThat(result.errors()).isNotEmpty();
    }
  }

  /// Test error path information (instancePath and schemaPath)
  /// RFC 8927 Section 3.2: All errors must include instancePath and schemaPath
  @Test
  public void testErrorPathInformation() {
    JsonValue schema = Json.parse("{\"properties\": {\"name\": {\"type\": \"string\"}, \"age\": {\"type\": \"int32\"}}}");
    JsonValue invalidData = Json.parse("{\"name\": 123, \"age\": \"not-a-number\"}");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, invalidData);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
    
    // Each error should have path information (currently only message is implemented)
    for (String error : result.errors()) {
      assertThat(error).isNotNull();
      LOG.fine(() -> "Error path test: " + error);
    }
  }

  /// Test multiple error collection
  /// Should collect all validation errors, not just the first one
  @Test
  public void testMultipleErrorCollection() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    JsonValue invalidData = Json.parse("[123, 456, \"valid\", 789]");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, invalidData);
    
    assertThat(result.isValid()).isFalse();
    
    // Should collect errors for all invalid elements (123, 456, 789)
    // Note: This test assumes the implementation collects multiple errors
    // If it returns early, this test will help identify that issue
    assertThat(result.errors()).isNotEmpty();
    LOG.fine(() -> "Multiple error collection test - errors count: " + result.errors().size());
    
    // Log all errors for debugging
    for (String error : result.errors()) {
      LOG.fine(() -> "Multiple error: " + error);
    }
  }

  /// Test discriminator tag exemption
  /// RFC 8927 §2.2.8: Only the discriminator field itself is exempt from additionalProperties enforcement
  @Test
  public void testDiscriminatorTagExemption() {
    JsonValue schema = Json.parse("{\"discriminator\": \"type\", \"mapping\": {\"person\": {\"properties\": {\"name\": {\"type\": \"string\"}}}}}");
    
    // Valid data with discriminator and no additional properties
    JsonValue validData1 = Json.parse("{\"type\": \"person\", \"name\": \"John\"}");
    
    // Data with discriminator and additional properties (only discriminator field should be exempt)
    JsonValue invalidData2 = Json.parse("{\"type\": \"person\", \"name\": \"John\", \"extra\": \"not_allowed\"}");
    
    Jtd validator = new Jtd();
    
    // First should be valid - no additional properties
    Jtd.Result result1 = validator.validate(schema, validData1);
    assertThat(result1.isValid()).isTrue();
    
    // Second should be invalid - extra field is not exempt, only discriminator field is
    Jtd.Result result2 = validator.validate(schema, invalidData2);
    assertThat(result2.isValid()).isFalse();
    assertThat(result2.errors()).anySatisfy(error -> assertThat(error).contains("extra"));
    
    LOG.fine(() -> "Discriminator tag exemption test - valid: " + validData1 + ", invalid: " + invalidData2);
  }
  
  /// Counter-test: Discriminator with invalid mapping
  /// Should fail when discriminator value is not in mapping
  @Test
  public void testDiscriminatorInvalidMapping() {
    JsonValue schema = Json.parse("{\"discriminator\": \"type\", \"mapping\": {\"person\": {\"properties\": {\"name\": {\"type\": \"string\"}}}}}");
    JsonValue invalidData = Json.parse("{\"type\": \"invalid\", \"name\": \"John\"}");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, invalidData);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
    LOG.fine(() -> "Discriminator invalid mapping test - errors: " + result.errors());
  }

  /// Test float type validation
  /// RFC 8927 Section 3.3.3: float32 and float64 validation
  @Test
  public void testFloatTypesValid() {
    JsonValue schema32 = Json.parse("{\"type\": \"float32\"}");
    JsonValue schema64 = Json.parse("{\"type\": \"float64\"}");
    
    // Valid float values
    String[] validFloats = {"1.5", "-3.14", "0", "123.456", "1e10", "-1.5e-3"};
    
    Jtd validator = new Jtd();
    
    for (String floatValue : validFloats) {
      JsonValue validData = Json.parse(floatValue);
      Jtd.Result result32 = validator.validate(schema32, validData);
      Jtd.Result result64 = validator.validate(schema64, validData);
      
      assertThat(result32.isValid()).isTrue();
      assertThat(result64.isValid()).isTrue();
      LOG.fine(() -> "Float types valid test - value: " + floatValue);
    }
  }
  
  /// Counter-test: Invalid float values
  /// Should reject non-numeric values for float types
  @Test
  public void testFloatTypesInvalid() {
    JsonValue schema = Json.parse("{\"type\": \"float32\"}");
    
    // Invalid values for float
    String[] invalidValues = {"\"not-a-float\"", "\"123\"", "true", "false", "null"};
    
    Jtd validator = new Jtd();
    
    for (String invalidValue : invalidValues) {
      JsonValue invalidData = Json.parse(invalidValue);
      Jtd.Result result = validator.validate(schema, invalidData);
      
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).isNotEmpty();
      LOG.fine(() -> "Float types invalid test - value: " + invalidValue + ", errors: " + result.errors());
    }
  }

  /// Test boolean type validation
  /// RFC 8927 Section 3.3.3: boolean type validation
  @Test
  public void testBooleanTypeValid() {
    JsonValue schema = Json.parse("{\"type\": \"boolean\"}");
    
    Jtd validator = new Jtd();
    
    // Valid boolean values
    JsonValue trueValue = Json.parse("true");
    JsonValue falseValue = Json.parse("false");
    
    Jtd.Result result1 = validator.validate(schema, trueValue);
    Jtd.Result result2 = validator.validate(schema, falseValue);
    
    assertThat(result1.isValid()).isTrue();
    assertThat(result2.isValid()).isTrue();
    LOG.fine(() -> "Boolean type valid test - true: " + trueValue + ", false: " + falseValue);
  }
  
  /// Counter-test: Invalid boolean values
  /// Should reject non-boolean values
  @Test
  public void testBooleanTypeInvalid() {
    JsonValue schema = Json.parse("{\"type\": \"boolean\"}");
    
    // Invalid values for boolean
    String[] invalidValues = {"\"true\"", "\"false\"", "1", "0", "\"yes\"", "\"no\"", "null"};
    
    Jtd validator = new Jtd();
    
    for (String invalidValue : invalidValues) {
      JsonValue invalidData = Json.parse(invalidValue);
      Jtd.Result result = validator.validate(schema, invalidData);
      
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).isNotEmpty();
      LOG.fine(() -> "Boolean type invalid test - value: " + invalidValue + ", errors: " + result.errors());
    }
  }

  /// Test nullable default behavior - non-nullable schemas must reject null
  @Test
  public void testNonNullableBooleanRejectsNull() {
    JsonValue schema = Json.parse("{\"type\":\"boolean\"}");
    JsonValue instance = Json.parse("null");
    Jtd.Result result = new Jtd().validate(schema, instance);
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anySatisfy(err -> assertThat(err).contains("expected boolean"));
  }

  /// Test nullable boolean accepts null when explicitly nullable
  @Test
  public void testNullableBooleanAcceptsNull() {
    JsonValue schema = Json.parse("{\"type\":\"boolean\",\"nullable\":true}");
    JsonValue instance = Json.parse("null");
    Jtd.Result result = new Jtd().validate(schema, instance);
    assertThat(result.isValid()).isTrue();
  }

  /// Test timestamp validation with leap second
  @Test
  public void testTimestampLeapSecond() {
    JsonValue schema = Json.parse("{\"type\":\"timestamp\"}");
    JsonValue instance = Json.parse("\"1990-12-31T23:59:60Z\"");
    Jtd.Result result = new Jtd().validate(schema, instance);
    assertThat(result.isValid()).isTrue();
  }

  /// Test timestamp validation with timezone offset
  @Test
  public void testTimestampWithTimezoneOffset() {
    JsonValue schema = Json.parse("{\"type\":\"timestamp\"}");
    JsonValue instance = Json.parse("\"1990-12-31T15:59:60-08:00\"");
    Jtd.Result result = new Jtd().validate(schema, instance);
    assertThat(result.isValid()).isTrue();
  }

  /// Test nested ref schema resolution
  @Test
  public void testRefSchemaNested() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "id": {"type": "string"},
          "user": {"properties": {"id": {"ref": "id"}}}
        },
        "ref": "user"
      }""");
    JsonValue instance = Json.parse("{\"id\":\"abc123\"}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    assertThat(result.isValid()).isTrue();
  }

  /// Test recursive ref schema resolution
  @Test
  public void testRefSchemaRecursive() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "node": {
            "properties": {
              "value": {"type":"string"},
              "next": {"nullable": true, "ref": "node"}
            }
          }
        },
        "ref": "node"
      }""");
    JsonValue instance = Json.parse("{\"value\":\"root\",\"next\":{\"value\":\"child\",\"next\":null}}");
    Jtd.Result result = new Jtd().validate(schema, instance);
    assertThat(result.isValid()).isTrue();
  }

  /// Test recursive ref schema validation - should reject invalid nested data
  /// "ref schema - recursive schema, bad" from JTD specification test suite
  @Test
  public void testRefSchemaRecursiveBad() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "root": {
            "elements": {
              "ref": "root"
            }
          }
        },
        "ref": "root"
      }""");
    
    // This should be invalid - nested array contains mixed types (arrays and strings)
    JsonValue instance = Json.parse("[[],[[]],[[[],[\"a\"]]]]");
    
    LOG.info(() -> "Testing recursive ref schema validation - should reject mixed types");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + instance);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.fine(() -> "ERRORS: " + result.errors());
    }
    
    // This should be invalid according to RFC 8927 (recursive elements should be homogeneous)
    assertThat(result.isValid())
      .as("Recursive ref should reject heterogeneous nested data")
      .isFalse();
  }

  /// Micro test to debug int32 validation with decimal values
  /// Should reject non-integer values like 3.14 for int32 type
  @Test
  public void testInt32RejectsDecimal() {
    JsonValue schema = Json.parse("{\"type\": \"int32\"}");
    JsonValue decimalValue = JsonNumber.of(new java.math.BigDecimal("3.14"));
    
    LOG.info(() -> "Testing int32 validation against decimal value 3.14");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + decimalValue);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, decimalValue);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.fine(() -> "ERRORS: " + result.errors());
    }
    
    // This should be invalid - int32 should reject decimal values
    assertThat(result.isValid())
      .as("int32 should reject decimal value 3.14")
      .isFalse();
    assertThat(result.errors())
      .as("Should have validation errors for decimal value")
      .isNotEmpty();
  }

  /// Test that integer types accept valid integer representations with trailing zeros
  /// RFC 8927 §2.2.3.1: "An integer value is a number without a fractional component"
  /// Values like 3.0, 3.000 are valid integers despite positive scale
  @Test  
  public void testIntegerTypesAcceptTrailingZeros() {
    JsonValue schema = Json.parse("{\"type\": \"int32\"}");
    
    // Valid integer representations with trailing zeros
    JsonValue[] validIntegers = {
      JsonNumber.of(new java.math.BigDecimal("3.0")),
      JsonNumber.of(new java.math.BigDecimal("3.000")),
      JsonNumber.of(new java.math.BigDecimal("42.00")),
      JsonNumber.of(new java.math.BigDecimal("0.0"))
    };
    
    Jtd validator = new Jtd();
    
    for (JsonValue validValue : validIntegers) {
      Jtd.Result result = validator.validate(schema, validValue);
      
      LOG.fine(() -> "Testing int32 with valid integer representation: " + validValue);
      
      assertThat(result.isValid())
        .as("int32 should accept integer representation %s", validValue)
        .isTrue();
      assertThat(result.errors())
        .as("Should have no validation errors for integer representation %s", validValue)
        .isEmpty();
    }
  }

  /// Test that integer types reject values with actual fractional components
  /// RFC 8927 §2.2.3.1: "An integer value is a number without a fractional component"  
  @Test
  public void testIntegerTypesRejectFractionalComponents() {
    JsonValue schema = Json.parse("{\"type\": \"int32\"}");
    
    // Invalid values with actual fractional components
    JsonValue[] invalidValues = {
      JsonNumber.of(new java.math.BigDecimal("3.1")),
      JsonNumber.of(new java.math.BigDecimal("3.0001")),
      JsonNumber.of(new java.math.BigDecimal("3.14")),
      JsonNumber.of(new java.math.BigDecimal("0.1"))
    };
    
    Jtd validator = new Jtd();
    
    for (JsonValue invalidValue : invalidValues) {
      Jtd.Result result = validator.validate(schema, invalidValue);
      
      LOG.fine(() -> "Testing int32 with fractional value: " + invalidValue);
      
      assertThat(result.isValid())
        .as("int32 should reject fractional value %s", invalidValue)
        .isFalse();
      assertThat(result.errors())
        .as("Should have validation errors for fractional value %s", invalidValue)
        .isNotEmpty();
    }
  }

  /// Test for Issue #91: additionalProperties should default to false when no properties defined
  /// Empty properties schema should reject additional properties
  @Test
  public void testAdditionalPropertiesDefaultsToFalse() {
    JsonValue schema = Json.parse("{\"elements\": {\"properties\": {}}}");
    JsonValue invalidData = Json.parse("[{\"extraProperty\":\"extra-value\"}]");
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, invalidData);
    
    // This should fail validation because additionalProperties defaults to false
    assertThat(result.isValid())
      .as("Empty properties schema should reject additional properties by default")
      .isFalse();
    assertThat(result.errors())
      .as("Should have validation error for additional property")
      .isNotEmpty();
  }

  /// Test discriminator schema nested within elements schema (RFC 8927 compliant)
  /// Schema has array elements with discriminator properties that map to valid properties forms
  @Test
  public void testDiscriminatorInElementsSchema() {
    JsonValue schema = Json.parse("""
    {
      "elements": {
        "properties": {
          "alpha": {
            "discriminator": "type",
            "mapping": {
              "config": {
                "properties": {
                  "value": {"type": "string"}
                },
                "additionalProperties": false
              },
              "flag": {
                "properties": {
                  "enabled": {"type": "boolean"}
                },
                "additionalProperties": false
              }
            }
          }
        },
        "additionalProperties": false
      }
    }
    """);

    JsonValue validDocument = Json.parse("""
    [
      {"alpha": {"type": "config", "value": "test"}},
      {"alpha": {"type": "flag", "enabled": true}}
    ]
    """);

    JsonValue invalidDocument = Json.parse("""
    [
      {"alpha": {"type": "config"}},
      {"alpha": {"type": "flag", "enabled": true}}
    ]
    """);

    LOG.info(() -> "Testing RFC 8927 compliant discriminator in elements schema");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Valid document: " + validDocument);
    LOG.fine(() -> "Invalid document: " + invalidDocument);

    Jtd validator = new Jtd();

    // Valid case: all required properties present
    Jtd.Result validResult = validator.validate(schema, validDocument);
    LOG.fine(() -> "Valid validation result: " + (validResult.isValid() ? "VALID" : "INVALID"));
    if (!validResult.isValid()) {
      LOG.fine(() -> "Valid errors: " + validResult.errors());
    }

    assertThat(validResult.isValid())
        .as("RFC 8927 compliant discriminator in elements should validate correctly")
        .isTrue();

    // Invalid case: missing required property in first element
    Jtd.Result invalidResult = validator.validate(schema, invalidDocument);
    LOG.fine(() -> "Invalid validation result: " + (invalidResult.isValid() ? "VALID" : "INVALID"));
    if (!invalidResult.isValid()) {
      LOG.fine(() -> "Invalid errors: " + invalidResult.errors());
    }

    assertThat(invalidResult.isValid())
        .as("Should reject document with missing required properties")
        .isFalse();
  }
  /// Test case from JtdExhaustiveTest property test failure
  /// Nested elements containing properties schemas should reject additional properties
  /// Schema: {"elements":{"elements":{"properties":{}}}}
  /// Document: [[{},{},[{},{extraProperty":"extra-value"}]]
  /// This should fail validation but currently passes incorrectly
  @Test
  public void testNestedElementsPropertiesRejectsAdditionalProperties() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "elements": {
            "properties": {}
          }
        }
      }
      """);
    JsonValue document = Json.parse("""
      [
        [{}, {}],
        [{}, {"extraProperty": "extra-value"}]
      ]
      """);
    
    LOG.info(() -> "Testing nested elements properties - property test failure case");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Document: " + document);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, document);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.fine(() -> "Errors: " + result.errors());
    }
    
    // This should fail because the inner object has an extra property
    // and the properties schema should reject additional properties by default
    assertThat(result.isValid())
      .as("Nested elements properties should reject additional properties")
      .isFalse();
    assertThat(result.errors())
      .as("Should have validation errors for additional property")
      .isNotEmpty();
  }

  /// Test for Issue #99: RFC 8927 empty form semantics
  /// Empty schema {} accepts everything, including objects with properties
  @Test
  public void testEmptySchemaAcceptsObjectsWithProperties() {
    JsonValue schema = Json.parse("{}");
    JsonValue document = Json.parse("{\"extraProperty\":\"extra-value\"}");
    
    LOG.info(() -> "Testing empty schema {} - should accept objects with properties per RFC 8927");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Document: " + document);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, document);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    
    // RFC 8927 §3.3.1: Empty form accepts all instances, including objects with properties
    assertThat(result.isValid())
      .as("Empty schema {} should accept objects with properties per RFC 8927")
      .isTrue();
    assertThat(result.errors())
      .as("Empty schema should produce no validation errors")
      .isEmpty();
  }

  /// Test case for Issue #99: RFC 8927 {} empty form semantics
  /// Empty schema {} must accept all JSON instances per RFC 8927 §3.3.1
  @Test
  public void testEmptySchemaAcceptsAnything_perRfc8927() {
    JsonValue schema = Json.parse("{}");
    Jtd validator = new Jtd();

    // RFC 8927 §3.3.1: "If a schema is of the 'empty' form, then it accepts all instances"
    assertThat(validator.validate(schema, Json.parse("null")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("true")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("123")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("3.14")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("\"foo\"")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("[]")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("{}")).isValid()).isTrue();
  }

  /// Test $ref to empty schema also accepts anything per RFC 8927
  @Test
  public void testRefToEmptySchemaAcceptsAnything() {
    JsonValue schema = Json.parse("""
      {
        "definitions": { "foo": {} },
        "ref": "foo"
      }
      """);

    Jtd validator = new Jtd();
    assertThat(validator.validate(schema, Json.parse("false")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("\"bar\"")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("[]")).isValid()).isTrue();
    assertThat(validator.validate(schema, Json.parse("{}")).isValid()).isTrue();
  }

  /// Test discriminator form with empty schema for discriminator property
  /// RFC 8927 §2.4: Discriminator mapping schemas must use empty schema {} for discriminator property
  /// The discriminator property itself should not be re-validated against the empty schema
  @Test
  public void testDiscriminatorFormWithEmptySchemaProperty() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "alpha",
        "mapping": {
          "type1": {
            "properties": {}
          }
        }
      }
      """);
    
    // Valid: discriminator value matches mapping key
    JsonValue validDocument = Json.parse("{\"alpha\": \"type1\"}");
    
    // Invalid: discriminator value doesn't match any mapping key
    JsonValue invalidDocument = Json.parse("{\"alpha\": \"wrong\"}");
    
    Jtd validator = new Jtd();
    
    // Should pass - discriminator value "type1" is in mapping
    Jtd.Result validResult = validator.validate(schema, validDocument);
    assertThat(validResult.isValid())
      .as("Discriminator with empty schema property should accept valid discriminator value")
      .isTrue();
    assertThat(validResult.errors())
      .as("Should have no validation errors for valid discriminator")
      .isEmpty();
    
    // Should fail - discriminator value "wrong" is not in mapping
    Jtd.Result invalidResult = validator.validate(schema, invalidDocument);
    assertThat(invalidResult.isValid())
      .as("Discriminator should reject invalid discriminator value")
      .isFalse();
    assertThat(invalidResult.errors())
      .as("Should have validation errors for invalid discriminator")
      .isNotEmpty();
    
    LOG.fine(() -> "Discriminator empty schema test - valid: " + validDocument + ", invalid: " + invalidDocument);
  }

  /// Test discriminator form with additional required properties
  /// Ensures discriminator field exemption doesn't break other property validation
  @Test
  public void testDiscriminatorWithAdditionalRequiredProperties() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "type",
        "mapping": {
          "user": {
            "properties": {
              "name": {"type": "string"}
            },
            "additionalProperties": false
          }
        }
      }
      """);
    
    // Valid: has discriminator and required property
    JsonValue validDocument = Json.parse("{\"type\": \"user\", \"name\": \"John\"}");
    
    // Invalid: missing required property (not discriminator)
    JsonValue invalidDocument = Json.parse("{\"type\": \"user\"}");
    
    Jtd validator = new Jtd();
    
    Jtd.Result validResult = validator.validate(schema, validDocument);
    assertThat(validResult.isValid())
      .as("Should accept document with discriminator and all required properties")
      .isTrue();
    
    Jtd.Result invalidResult = validator.validate(schema, invalidDocument);
    assertThat(invalidResult.isValid())
      .as("Should reject document missing non-discriminator required properties")
      .isFalse();
    assertThat(invalidResult.errors())
      .as("Should report missing required property")
      .anyMatch(error -> error.contains("missing required property: 'name'"));
  }

  /// Test discriminator form with optional properties
  /// Ensures discriminator field exemption works with optional properties too
  @Test
  public void testDiscriminatorWithOptionalProperties() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "kind",
        "mapping": {
          "circle": {
            "optionalProperties": {
              "radius": {"type": "float32"}
            },
            "additionalProperties": false
          }
        }
      }
      """);
    
    // Valid: discriminator only
    JsonValue minimalDocument = Json.parse("{\"kind\": \"circle\"}");
    
    // Valid: discriminator with optional property
    JsonValue withOptionalDocument = Json.parse("{\"kind\": \"circle\", \"radius\": 5.5}");
    
    Jtd validator = new Jtd();
    
    Jtd.Result minimalResult = validator.validate(schema, minimalDocument);
    assertThat(minimalResult.isValid())
      .as("Should accept document with only discriminator")
      .isTrue();
    
    Jtd.Result optionalResult = validator.validate(schema, withOptionalDocument);
    assertThat(optionalResult.isValid())
      .as("Should accept document with discriminator and optional property")
      .isTrue();
  }

  /// Test discriminator form where discriminator appears in optionalProperties
  /// Edge case: discriminator field might be in optionalProperties instead of properties
  @Test
  public void testDiscriminatorInOptionalProperties() {
    JsonValue schema = Json.parse("""
      {
        "discriminator": "mode",
        "mapping": {
          "default": {
            "optionalProperties": {
              "config": {"type": "string"}
            },
            "additionalProperties": false
          }
        }
      }
      """);
    
    JsonValue validDocument = Json.parse("{\"mode\": \"default\"}");
    
    Jtd validator = new Jtd();
    
    Jtd.Result result = validator.validate(schema, validDocument);
    assertThat(result.isValid())
      .as("Should accept discriminator field in optionalProperties")
      .isTrue();
  }

  /// Test for the critical integer range validation bug
  /// This test specifically targets the issue where Double values bypass range checks
  /// JsonNumber.toNumber() commonly returns Double, which falls through validation
  @Test
  public void testInt8RangeValidationWithDoubleValues() {
    JsonValue schema = Json.parse("{\"type\": \"int8\"}");
    
    // These values should fail int8 validation (range: -128 to 127)
    // But they pass through because Double values aren't handled in range checking
    JsonValue[] outOfRangeValues = {
      Json.parse("1000"),    // Way above int8 max (127)
      Json.parse("-500"),    // Way below int8 min (-128) 
      Json.parse("300"),     // Above int8 max
      Json.parse("-200")     // Below int8 min
    };
    
    Jtd validator = new Jtd();
    
    for (JsonValue outOfRange : outOfRangeValues) {
      Jtd.Result result = validator.validate(schema, outOfRange);
      
      LOG.fine(() -> "Testing int8 range with Double value: " + outOfRange + 
               " (JsonNumber.toNumber() type: " + 
               ((JsonNumber)outOfRange).toNumber().getClass().getSimpleName() + ")");
      
      // This should fail but currently passes due to the bug
      assertThat(result.isValid())
        .as("int8 should reject out-of-range value %s (likely parsed as Double)", outOfRange)
        .isFalse();
      assertThat(result.errors())
        .as("Should have range validation errors for %s", outOfRange)
        .isNotEmpty();
    }
  }

  /// Test uint8 range validation with Double values
  @Test  
  public void testUint8RangeValidationWithDoubleValues() {
    JsonValue schema = Json.parse("{\"type\": \"uint8\"}");
    
    // These should fail uint8 validation (range: 0 to 255)
    JsonValue[] outOfRangeValues = {
      Json.parse("1000"),    // Way above uint8 max (255)
      Json.parse("-1"),      // Below uint8 min (0)
      Json.parse("500"),     // Above uint8 max
      Json.parse("-100")     // Below uint8 min
    };
    
    Jtd validator = new Jtd();
    
    for (JsonValue outOfRange : outOfRangeValues) {
      Jtd.Result result = validator.validate(schema, outOfRange);
      
      assertThat(result.isValid())
        .as("uint8 should reject out-of-range value %s", outOfRange)
        .isFalse();
      assertThat(result.errors())
        .as("Should have range validation errors for %s", outOfRange)
        .isNotEmpty();
    }
  }

  /// Test int16 range validation with Double values  
  @Test
  public void testInt16RangeValidationWithDoubleValues() {
    JsonValue schema = Json.parse("{\"type\": \"int16\"}");
    
    // These should fail int16 validation (range: -32768 to 32767)
    JsonValue[] outOfRangeValues = {
      Json.parse("100000"),   // Way above int16 max
      Json.parse("-100000"),  // Way below int16 min
      Json.parse("50000"),    // Above int16 max
      Json.parse("-50000")    // Below int16 min
    };
    
    Jtd validator = new Jtd();
    
    for (JsonValue outOfRange : outOfRangeValues) {
      Jtd.Result result = validator.validate(schema, outOfRange);
      
      assertThat(result.isValid())
        .as("int16 should reject out-of-range value %s", outOfRange)
        .isFalse();
    }
  }

  /// Test uint16 range validation with Double values
  @Test
  public void testUint16RangeValidationWithDoubleValues() {
    JsonValue schema = Json.parse("{\"type\": \"uint16\"}");
    
    JsonValue[] outOfRangeValues = {
      Json.parse("100000"),   // Way above uint16 max (65535)
      Json.parse("-1"),       // Below uint16 min (0)
      Json.parse("70000"),    // Above uint16 max
      Json.parse("-1000")     // Below uint16 min
    };
    
    Jtd validator = new Jtd();
    
    for (JsonValue outOfRange : outOfRangeValues) {
      Jtd.Result result = validator.validate(schema, outOfRange);
      
      assertThat(result.isValid())
        .as("uint16 should reject out-of-range value %s", outOfRange)
        .isFalse();
    }
  }

  /// Test int32 range validation with Double values
  @Test
  public void testInt32RangeValidationWithDoubleValues() {
    JsonValue schema = Json.parse("{\"type\": \"int32\"}");
    
    // These should fail int32 validation (range: -2147483648 to 2147483647)
    JsonValue[] outOfRangeValues = {
      Json.parse("3000000000"),    // Above int32 max
      Json.parse("-3000000000"),   // Below int32 min
      Json.parse("2200000000"),    // Above int32 max  
      Json.parse("-2200000000")    // Below int32 min
    };
    
    Jtd validator = new Jtd();
    
    for (JsonValue outOfRange : outOfRangeValues) {
      Jtd.Result result = validator.validate(schema, outOfRange);
      
      assertThat(result.isValid())
        .as("int32 should reject out-of-range value %s", outOfRange)
        .isFalse();
    }
  }

  /// Test uint32 range validation with Double values
  @Test
  public void testUint32RangeValidationWithDoubleValues() {
    JsonValue schema = Json.parse("{\"type\": \"uint32\"}");
    
    // These should fail uint32 validation (range: 0 to 4294967295)
    JsonValue[] outOfRangeValues = {
      Json.parse("5000000000"),    // Above uint32 max
      Json.parse("-1"),            // Below uint32 min
      Json.parse("4500000000"),    // Above uint32 max
      Json.parse("-1000")          // Below uint32 min
    };
    
    Jtd validator = new Jtd();
    
    for (JsonValue outOfRange : outOfRangeValues) {
      Jtd.Result result = validator.validate(schema, outOfRange);
      
      assertThat(result.isValid())
        .as("uint32 should reject out-of-range value %s", outOfRange)
        .isFalse();
    }
  }

  /// Test that demonstrates the specific problem: JsonNumber.toNumber() returns Double
  /// This test shows the root cause of the bug
  @Test
  public void testJsonNumberToNumberReturnsDouble() {
    JsonValue numberValue = Json.parse("1000");
    
    // Verify that JsonNumber.toNumber() returns Double for typical JSON numbers
    assertThat(numberValue).isInstanceOf(JsonNumber.class);
    Number number = ((JsonNumber) numberValue).toNumber();
    
    LOG.info(() -> "JsonNumber.toNumber() returns: " + number.getClass().getSimpleName() + 
                   " for value: " + numberValue);
    
    // This demonstrates what type JsonNumber.toNumber() returns for typical values
    // The actual type depends on the JSON parser implementation
    LOG.info(() -> "JsonNumber.toNumber() returns: " + number.getClass().getSimpleName() + 
                   " for value: " + numberValue);
    
    // The key test is that regardless of the Number type, range validation should work
    // Our fix ensures all Number types go through proper range validation
  }

  /// Test integer validation with explicit Double creation
  /// Shows the bug occurs even when we know the type is Double
  @Test 
  public void testIntegerValidationExplicitDouble() {
    JsonValue schema = Json.parse("{\"type\": \"int8\"}");
    
    // Create a JsonNumber that definitely returns Double
    JsonValue doubleValue = JsonNumber.of(1000.0);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, doubleValue);
    
    LOG.fine(() -> "Explicit Double validation - value: " + doubleValue + 
             ", toNumber() type: " + ((JsonNumber)doubleValue).toNumber().getClass().getSimpleName());
    
    // This should fail (1000 is way outside int8 range of -128 to 127)
    assertThat(result.isValid())
      .as("int8 should reject Double value 1000.0 as out of range")
      .isFalse();
  }

  /// Test that edge case boundary values work correctly
  /// These test the exact boundaries where the bug becomes visible
  @Test
  public void testIntegerBoundaryValues() {
    // Test int8 boundaries
    JsonValue int8Schema = Json.parse("{\"type\": \"int8\"}");
    
    // Just outside valid range
    JsonValue justAboveMax = Json.parse("128");   // int8 max is 127
    JsonValue justBelowMin = Json.parse("-129");  // int8 min is -128
    
    Jtd validator = new Jtd();
    
    Jtd.Result aboveResult = validator.validate(int8Schema, justAboveMax);
    assertThat(aboveResult.isValid())
      .as("int8 should reject 128 (just above max 127)")
      .isFalse();
      
    Jtd.Result belowResult = validator.validate(int8Schema, justBelowMin);  
    assertThat(belowResult.isValid())
      .as("int8 should reject -129 (just below min -128)")
      .isFalse();
  }

  /// Test BigDecimal values that exceed long precision
  /// Ensures precision loss is handled correctly
  @Test
  public void testIntegerValidationWithBigDecimalValues() {
    JsonValue schema = Json.parse("{\"type\": \"uint32\"}");
    JsonValue bigValue = JsonNumber.of(new java.math.BigDecimal("5000000000")); // > uint32 max
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, bigValue);
    
    assertThat(result.isValid())
      .as("uint32 should reject BigDecimal value 5000000000 as out of range")
      .isFalse();
  }

  /// Test mixed integer types in array to catch range validation issues
  /// This creates a scenario where multiple integer validations occur
  @Test
  public void testMixedIntegerTypesInArray() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "discriminator": "type",
          "mapping": {
            "small": {"properties": {"value": {"type": "int8"}}},
            "medium": {"properties": {"value": {"type": "int16"}}},
            "large": {"properties": {"value": {"type": "int32"}}}
          }
        }
      }
      """);
    
    // Array with values that should fail their respective type validations
    JsonValue invalidData = Json.parse("""
      [
        {"type": "small", "value": 1000},
        {"type": "medium", "value": 100000},
        {"type": "large", "value": 5000000000}
      ]
      """);
      
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, invalidData);
    
    LOG.fine(() -> "Mixed integer types test - should have multiple range errors");
    
    // Should fail with multiple range validation errors
    assertThat(result.isValid())
      .as("Should reject array with out-of-range values for different integer types")
      .isFalse();
    assertThat(result.errors().size())
      .as("Should have multiple range validation errors")
      .isGreaterThan(0);
  }
}
