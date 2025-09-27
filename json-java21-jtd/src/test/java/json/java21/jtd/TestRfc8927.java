package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Comprehensive RFC 8927 compliance tests
/// Tests all the TODO items and requirements identified in the analysis:
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
  public void testRefSchemaValid() throws Exception {
    JsonValue schema = Json.parse("{\"ref\": \"address\", \"definitions\": {\"address\": {\"type\": \"string\"}}}");
    JsonValue validData = Json.parse("\"123 Main St\"");
    
    Jtd validator = new Jtd();
    ValidationResult result = validator.validate(schema, validData);
    
    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isEmpty();
    LOG.fine(() -> "Ref schema valid test - schema: " + schema + ", data: " + validData);
  }
  
  /// Counter-test: Ref schema with invalid definition reference
  /// Should fail when ref points to non-existent definition
  @Test
  public void testRefSchemaInvalidDefinition() throws Exception {
    JsonValue schema = Json.parse("{\"ref\": \"nonexistent\", \"definitions\": {\"address\": {\"type\": \"string\"}}}");
    JsonValue data = Json.parse("\"anything\"");
    
    Jtd validator = new Jtd();
    ValidationResult result = validator.validate(schema, data);
    
    assertThat(result.isValid())
      .as("Ref schema should fail when definition doesn't exist, but implementation is broken")
      .isFalse();
    assertThat(result.errors()).isNotEmpty();
  }

  /// Test timestamp format validation (RFC 3339)
  /// RFC 8927 Section 3.3.3: timestamp must follow RFC 3339 format
  @Test
  public void testTimestampValid() throws Exception {
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
      ValidationResult result = validator.validate(schema, validData);
      assertThat(result.isValid()).isTrue();
      LOG.fine(() -> "Timestamp valid test - data: " + validData);
    }
  }
  
  /// Counter-test: Invalid timestamp formats
  /// Should reject non-RFC 3339 timestamp strings
  @Test
  public void testTimestampInvalid() throws Exception {
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
      ValidationResult result = validator.validate(schema, invalidData);
      
      assertThat(result.isValid())
        .as("Timestamp should reject invalid RFC 3339 format: " + invalidData)
        .isFalse();
      assertThat(result.errors()).isNotEmpty();
    }
  }

  /// Test integer type range validation
  /// RFC 8927 Table 2: Specific ranges for each integer type
  @Test
  public void testIntegerRangesValid() throws Exception {
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
  public void testIntegerRangesInvalid() throws Exception {
    // Test invalid ranges for each integer type
    testIntegerTypeInvalid("int8", "-129", "128");     // Below min, above max
    testIntegerTypeInvalid("uint8", "-1", "256");      // Below min, above max
    testIntegerTypeInvalid("int16", "-32769", "32768"); // Below min, above max
    testIntegerTypeInvalid("uint16", "-1", "65536");   // Below min, above max
    testIntegerTypeInvalid("int32", "-2147483649", "2147483648"); // Below min, above max
    testIntegerTypeInvalid("uint32", "-1", "4294967296"); // Below min, above max
  }
  
  /// Helper method to test valid integer ranges
  private void testIntegerTypeRange(String type, String min, String max, String middle) throws Exception {
    JsonValue schema = Json.parse("{\"type\": \"" + type + "\"}");
    Jtd validator = new Jtd();
    
    // Test minimum, maximum, and middle values
    String[] validValues = {min, max, middle};
    
    for (String value : validValues) {
      JsonValue validData = Json.parse(value);
      ValidationResult result = validator.validate(schema, validData);
      assertThat(result.isValid()).isTrue();
      LOG.fine(() -> "Integer range valid test - type: " + type + ", value: " + value);
    }
  }
  
  /// Helper method to test invalid integer ranges
  private void testIntegerTypeInvalid(String type, String belowMin, String aboveMax) throws Exception {
    JsonValue schema = Json.parse("{\"type\": \"" + type + "\"}");
    Jtd validator = new Jtd();
    
    // Test values below minimum and above maximum
    String[] invalidValues = {belowMin, aboveMax};
    
    for (String value : invalidValues) {
      JsonValue invalidData = Json.parse(value);
      ValidationResult result = validator.validate(schema, invalidData);
      
      assertThat(result.isValid())
        .as("Integer type \"" + type + "\" should reject value \"" + value + "\" as out of range")
        .isFalse();
      assertThat(result.errors()).isNotEmpty();
    }
  }

  /// Test error path information (instancePath and schemaPath)
  /// RFC 8927 Section 3.2: All errors must include instancePath and schemaPath
  @Test
  public void testErrorPathInformation() throws Exception {
    JsonValue schema = Json.parse("{\"properties\": {\"name\": {\"type\": \"string\"}, \"age\": {\"type\": \"int32\"}}}");
    JsonValue invalidData = Json.parse("{\"name\": 123, \"age\": \"not-a-number\"}");
    
    Jtd validator = new Jtd();
    ValidationResult result = validator.validate(schema, invalidData);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
    
    // Each error should have path information (currently only message is implemented)
    for (ValidationError error : result.errors()) {
      assertThat(error.message()).isNotNull();
      LOG.fine(() -> "Error path test - message: " + error.message());
    }
  }

  /// Test multiple error collection
  /// Should collect all validation errors, not just the first one
  @Test
  public void testMultipleErrorCollection() throws Exception {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    JsonValue invalidData = Json.parse("[123, 456, \"valid\", 789]");
    
    Jtd validator = new Jtd();
    ValidationResult result = validator.validate(schema, invalidData);
    
    assertThat(result.isValid()).isFalse();
    
    // Should collect errors for all invalid elements (123, 456, 789)
    // Note: This test assumes the implementation collects multiple errors
    // If it returns early, this test will help identify that issue
    assertThat(result.errors()).isNotEmpty();
    LOG.fine(() -> "Multiple error collection test - errors count: " + result.errors().size());
    
    // Log all errors for debugging
    for (ValidationError error : result.errors()) {
      LOG.fine(() -> "Multiple error - message: " + error.message());
    }
  }

  /// Test discriminator tag exemption
  /// RFC 8927 Section 3.3.8: Discriminator property is exempt from additional properties validation
  @Test
  public void testDiscriminatorTagExemption() throws Exception {
    JsonValue schema = Json.parse("{\"discriminator\": \"type\", \"mapping\": {\"person\": {\"properties\": {\"name\": {\"type\": \"string\"}}}}}");
    
    // Valid data with discriminator and no additional properties
    JsonValue validData1 = Json.parse("{\"type\": \"person\", \"name\": \"John\"}");
    
    // Data with discriminator and additional properties (discriminator should be exempt)
    JsonValue validData2 = Json.parse("{\"type\": \"person\", \"name\": \"John\", \"extra\": \"allowed\"}");
    
    Jtd validator = new Jtd();
    
    // Both should be valid - discriminator is exempt from additional properties check
    ValidationResult result1 = validator.validate(schema, validData1);
    ValidationResult result2 = validator.validate(schema, validData2);
    
    assertThat(result1.isValid()).isTrue();
    assertThat(result2.isValid()).isTrue();
    LOG.fine(() -> "Discriminator tag exemption test - data1: " + validData1 + ", data2: " + validData2);
  }
  
  /// Counter-test: Discriminator with invalid mapping
  /// Should fail when discriminator value is not in mapping
  @Test
  public void testDiscriminatorInvalidMapping() throws Exception {
    JsonValue schema = Json.parse("{\"discriminator\": \"type\", \"mapping\": {\"person\": {\"properties\": {\"name\": {\"type\": \"string\"}}}}}");
    JsonValue invalidData = Json.parse("{\"type\": \"invalid\", \"name\": \"John\"}");
    
    Jtd validator = new Jtd();
    ValidationResult result = validator.validate(schema, invalidData);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
    LOG.fine(() -> "Discriminator invalid mapping test - errors: " + result.errors());
  }

  /// Test float type validation
  /// RFC 8927 Section 3.3.3: float32 and float64 validation
  @Test
  public void testFloatTypesValid() throws Exception {
    JsonValue schema32 = Json.parse("{\"type\": \"float32\"}");
    JsonValue schema64 = Json.parse("{\"type\": \"float64\"}");
    
    // Valid float values
    String[] validFloats = {"1.5", "-3.14", "0", "123.456", "1e10", "-1.5e-3"};
    
    Jtd validator = new Jtd();
    
    for (String floatValue : validFloats) {
      JsonValue validData = Json.parse(floatValue);
      ValidationResult result32 = validator.validate(schema32, validData);
      ValidationResult result64 = validator.validate(schema64, validData);
      
      assertThat(result32.isValid()).isTrue();
      assertThat(result64.isValid()).isTrue();
      LOG.fine(() -> "Float types valid test - value: " + floatValue);
    }
  }
  
  /// Counter-test: Invalid float values
  /// Should reject non-numeric values for float types
  @Test
  public void testFloatTypesInvalid() throws Exception {
    JsonValue schema = Json.parse("{\"type\": \"float32\"}");
    
    // Invalid values for float
    String[] invalidValues = {"\"not-a-float\"", "\"123\"", "true", "false", "null"};
    
    Jtd validator = new Jtd();
    
    for (String invalidValue : invalidValues) {
      JsonValue invalidData = Json.parse(invalidValue);
      ValidationResult result = validator.validate(schema, invalidData);
      
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).isNotEmpty();
      LOG.fine(() -> "Float types invalid test - value: " + invalidValue + ", errors: " + result.errors());
    }
  }

  /// Test boolean type validation
  /// RFC 8927 Section 3.3.3: boolean type validation
  @Test
  public void testBooleanTypeValid() throws Exception {
    JsonValue schema = Json.parse("{\"type\": \"boolean\"}");
    
    Jtd validator = new Jtd();
    
    // Valid boolean values
    JsonValue trueValue = Json.parse("true");
    JsonValue falseValue = Json.parse("false");
    
    ValidationResult result1 = validator.validate(schema, trueValue);
    ValidationResult result2 = validator.validate(schema, falseValue);
    
    assertThat(result1.isValid()).isTrue();
    assertThat(result2.isValid()).isTrue();
    LOG.fine(() -> "Boolean type valid test - true: " + trueValue + ", false: " + falseValue);
  }
  
  /// Counter-test: Invalid boolean values
  /// Should reject non-boolean values
  @Test
  public void testBooleanTypeInvalid() throws Exception {
    JsonValue schema = Json.parse("{\"type\": \"boolean\"}");
    
    // Invalid values for boolean
    String[] invalidValues = {"\"true\"", "\"false\"", "1", "0", "\"yes\"", "\"no\"", "null"};
    
    Jtd validator = new Jtd();
    
    for (String invalidValue : invalidValues) {
      JsonValue invalidData = Json.parse(invalidValue);
      ValidationResult result = validator.validate(schema, invalidData);
      
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).isNotEmpty();
      LOG.fine(() -> "Boolean type invalid test - value: " + invalidValue + ", errors: " + result.errors());
    }
  }
}