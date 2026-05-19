package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/// Probes for Type validation edge cases and potential issues
/// 
/// Areas to probe:
/// 1. Integer range boundary values
/// 2. Integer vs float detection (3.0 is int, 3.1 is not)
/// 3. Scientific notation handling
/// 4. Very large numbers
/// 5. Float vs double distinctions
public class TypeValidationEdgeCaseProbe extends JtdTestBase {

  /// Test: Integer type boundary values (exact min/max)
  @Test
  public void probeIntegerBoundaryValues() {
    // int8 boundaries
    testIntegerBoundary("int8", -128, 127);
    
    // uint8 boundaries  
    testIntegerBoundary("uint8", 0, 255);
    
    // int16 boundaries
    testIntegerBoundary("int16", -32768, 32767);
    
    // uint16 boundaries
    testIntegerBoundary("uint16", 0, 65535);
    
    // int32 boundaries
    testIntegerBoundary("int32", -2147483648, 2147483647);
    
    // uint32 boundaries
    testIntegerBoundary("uint32", 0, 4294967295L);
  }
  
  private void testIntegerBoundary(String type, long min, long max) {
    JsonValue schema = Json.parse("{\"type\": \"" + type + "\"}");
    Jtd validator = new Jtd();
    
    // Min boundary - should be valid
    JsonValue minValue = Json.parse(String.valueOf(min));
    Jtd.Result minResult = validator.validate(schema, minValue);
    assertTrue(minResult.isValid(), type + " should accept min value " + min);
    
    // Max boundary - should be valid
    JsonValue maxValue = Json.parse(String.valueOf(max));
    Jtd.Result maxResult = validator.validate(schema, maxValue);
    assertTrue(maxResult.isValid(), type + " should accept max value " + max);
    
    // Min-1 - should be invalid
    JsonValue belowMin = Json.parse(String.valueOf(min - 1));
    Jtd.Result belowResult = validator.validate(schema, belowMin);
    assertFalse(belowResult.isValid(), type + " should reject " + (min - 1));
    
    // Max+1 - should be invalid
    JsonValue aboveMax = Json.parse(String.valueOf(max + 1));
    Jtd.Result aboveResult = validator.validate(schema, aboveMax);
    assertFalse(aboveResult.isValid(), type + " should reject " + (max + 1));
    
    LOG.fine(() -> type + " boundary check passed");
  }

  /// Test: Integer detection with fractional values
  /// RFC 8927: "An integer value is a number without a fractional component"
  @Test
  public void probeIntegerFractionalDetection() {
    JsonValue schema = Json.parse("{\"type\": \"int32\"}");
    Jtd validator = new Jtd();
    
    // These should be valid (zero fractional part)
    String[] validIntegers = {"3.0", "3.00", "3.000", "0.0", "-5.0"};
    for (String value : validIntegers) {
      JsonValue instance = Json.parse(value);
      Jtd.Result result = validator.validate(schema, instance);
      assertTrue(result.isValid(), "int32 should accept integer representation: " + value);
    }
    
    // These should be invalid (non-zero fractional part)
    String[] invalidIntegers = {"3.1", "3.01", "3.0001", "0.1", "-5.5"};
    for (String value : invalidIntegers) {
      JsonValue instance = Json.parse(value);
      Jtd.Result result = validator.validate(schema, instance);
      assertFalse(result.isValid(), "int32 should reject fractional value: " + value);
    }
    
    LOG.info(() -> "Integer fractional detection probe passed");
  }

  /// Test: Scientific notation handling
  /// Scientific notation that represents integers should be valid
  @Test
  public void probeScientificNotationIntegers() {
    JsonValue schema = Json.parse("{\"type\": \"int32\"}");
    Jtd validator = new Jtd();
    
    // Valid scientific notation integers
    String[] validScientific = {"1e2", "1E2", "1e+2", "1e-2", "-5e1"};
    // 1e2 = 100, 1e-2 = 0.01 (not integer!)
    
    // These represent integers
    JsonValue int1 = Json.parse("1e2"); // 100
    Jtd.Result r1 = validator.validate(schema, int1);
    LOG.info(() -> "1e2 (should be 100): valid=" + r1.isValid());
    
    // These do NOT represent integers
    JsonValue notInt = Json.parse("1.5e0"); // 1.5
    Jtd.Result r2 = validator.validate(schema, notInt);
    assertFalse(r2.isValid(), "1.5 should not be a valid int32");
    
    JsonValue alsoNotInt = Json.parse("1e-1"); // 0.1
    Jtd.Result r3 = validator.validate(schema, alsoNotInt);
    LOG.info(() -> "1e-1 (0.1): valid=" + r3.isValid());
  }

  /// Test: Float types accept any number
  /// RFC 8927: float32 and float64 accept any JSON number
  @Test
  public void probeFloatTypesAcceptAnyNumber() {
    Jtd validator = new Jtd();
    
    JsonValue schema32 = Json.parse("{\"type\": \"float32\"}");
    JsonValue schema64 = Json.parse("{\"type\": \"float64\"}");
    
    // Various number formats
    String[] numbers = {
        "1", "-1", "0", 
        "1.5", "-3.14159", "0.0001",
        "1e10", "1e-10", "-5E+5",
        "1.7976931348623157e308", // Near double max
        "-1.7976931348623157e308"
    };
    
    for (String num : numbers) {
      try {
        JsonValue instance = Json.parse(num);
        Jtd.Result r32 = validator.validate(schema32, instance);
        Jtd.Result r64 = validator.validate(schema64, instance);
        
        LOG.fine(() -> num + " -> float32:" + r32.isValid() + " float64:" + r64.isValid());
        
        assertTrue(r32.isValid(), "float32 should accept: " + num);
        assertTrue(r64.isValid(), "float64 should accept: " + num);
      } catch (Exception e) {
        LOG.warning(() -> "Failed to parse: " + num + " - " + e.getMessage());
      }
    }
  }

  /// Test: Non-numbers rejected by float types
  @Test
  public void probeFloatTypesRejectNonNumbers() {
    Jtd validator = new Jtd();
    
    JsonValue schema = Json.parse("{\"type\": \"float32\"}");
    
    // Non-number values
    String[] nonNumbers = {"\"string\"", "true", "false", "null", "[]", "{}"};
    
    for (String value : nonNumbers) {
      JsonValue instance = Json.parse(value);
      Jtd.Result result = validator.validate(schema, instance);
      
      assertFalse(result.isValid(), "float32 should reject: " + value);
    }
  }

  /// Test: Timestamp format variations
  /// RFC 3339 has many valid formats
  @Test
  public void probeTimestampFormatVariations() {
    JsonValue schema = Json.parse("{\"type\": \"timestamp\"}");
    Jtd validator = new Jtd();
    
    // Valid RFC 3339 formats
    String[] validTimestamps = {
        "\"2023-01-01T00:00:00Z\"",
        "\"2023-01-01T00:00:00.000Z\"",
        "\"2023-01-01T00:00:00+00:00\"",
        "\"2023-01-01T00:00:00-00:00\"",
        "\"2023-01-01T00:00:00+05:30\"",
        "\"2023-01-01T00:00:00-08:00\"",
        "\"2023-12-31T23:59:60Z\"", // Leap second
        "\"2020-02-29T12:00:00Z\"", // Leap year
    };
    
    for (String ts : validTimestamps) {
      try {
        JsonValue instance = Json.parse(ts);
        Jtd.Result result = validator.validate(schema, instance);
        
        LOG.fine(() -> ts + " -> valid=" + result.isValid());
        
        if (!result.isValid()) {
          LOG.warning(() -> "Timestamp rejected: " + ts);
        }
      } catch (Exception e) {
        LOG.warning(() -> "Failed: " + ts + " - " + e.getMessage());
      }
    }
  }

  /// Test: Invalid timestamp formats
  @Test
  public void probeInvalidTimestampFormats() {
    JsonValue schema = Json.parse("{\"type\": \"timestamp\"}");
    Jtd validator = new Jtd();
    
    // Invalid formats
    String[] invalidTimestamps = {
        "\"2023-01-01\"",              // Date only
        "\"12:00:00\"",                // Time only
        "\"2023/01/01T12:00:00Z\"",    // Wrong date separator
        "\"2023-01-01 12:00:00Z\"",    // Space instead of T
        "\"2023-1-1T12:00:00Z\"",      // Single digit month/day
        "\"2023-01-01T12:00Z\"",       // Missing seconds
        "\"2023-01-01T25:00:00Z\"",    // Invalid hour
        "\"2023-01-01T12:61:00Z\"",    // Invalid minute
        "\"2023-01-01T12:00:61Z\"",    // Invalid second (not leap second)
        "\"not-a-timestamp\"",
        "123",
        "null"
    };
    
    for (String ts : invalidTimestamps) {
      try {
        JsonValue instance = Json.parse(ts);
        Jtd.Result result = validator.validate(schema, instance);
        
        assertFalse(result.isValid(), "Should reject invalid timestamp: " + ts);
      } catch (Exception e) {
        LOG.warning(() -> "Parse/validation failed for: " + ts);
      }
    }
  }

  /// Test: Boolean type rejects all non-booleans
  @Test
  public void probeBooleanTypeStrictness() {
    JsonValue schema = Json.parse("{\"type\": \"boolean\"}");
    Jtd validator = new Jtd();
    
    // Valid booleans
    assertTrue(validator.validate(schema, Json.parse("true")).isValid());
    assertTrue(validator.validate(schema, Json.parse("false")).isValid());
    
    // Invalid values
    String[] invalid = {"\"true\"", "\"false\"", "1", "0", "null", "[]", "{}"};
    for (String value : invalid) {
      JsonValue instance = Json.parse(value);
      Jtd.Result result = validator.validate(schema, instance);
      assertFalse(result.isValid(), "Boolean should reject: " + value);
    }
  }

  /// Test: String type rejects non-strings
  @Test
  public void probeStringTypeStrictness() {
    JsonValue schema = Json.parse("{\"type\": \"string\"}");
    Jtd validator = new Jtd();
    
    // Valid strings
    assertTrue(validator.validate(schema, Json.parse("\"hello\"")).isValid());
    assertTrue(validator.validate(schema, Json.parse("\"\"")).isValid()); // Empty string
    assertTrue(validator.validate(schema, Json.parse("\"with spaces\"")).isValid());
    
    // Invalid values
    String[] invalid = {"123", "true", "null", "[]", "{}"};
    for (String value : invalid) {
      JsonValue instance = Json.parse(value);
      Jtd.Result result = validator.validate(schema, instance);
      assertFalse(result.isValid(), "String should reject: " + value);
    }
  }

  /// Test: Very large integers
  /// Numbers larger than 64-bit should be handled gracefully
  @Test
  public void probeVeryLargeIntegers() {
    JsonValue schema = Json.parse("{\"type\": \"uint32\"}");
    Jtd validator = new Jtd();
    
    // Very large number (bigger than uint32 max)
    JsonValue huge = Json.parse("999999999999999999999999999999");
    Jtd.Result result = validator.validate(schema, huge);
    
    LOG.info(() -> "Very large number result: " + result.isValid());
    
    // Should be invalid (out of range)
    assertFalse(result.isValid(), "Should reject huge number");
  }

  /// Test: Zero values for all integer types
  @Test
  public void probeZeroValuesForAllIntegerTypes() {
    String[] types = {"int8", "uint8", "int16", "uint16", "int32", "uint32"};
    
    for (String type : types) {
      JsonValue schema = Json.parse("{\"type\": \"" + type + "\"}");
      Jtd.Result result = new Jtd().validate(schema, Json.parse("0"));
      
      boolean expectValid = !type.startsWith("u") || true; // 0 is valid for all
      
      LOG.fine(() -> type + " with 0: " + result.isValid() + " (expected: " + expectValid + ")");
      
      if (expectValid) {
        assertTrue(result.isValid(), type + " should accept 0");
      }
    }
  }

  /// Test: Negative values for unsigned types
  @Test
  public void probeNegativeValuesForUnsignedTypes() {
    String[] unsignedTypes = {"uint8", "uint16", "uint32"};
    
    for (String type : unsignedTypes) {
      JsonValue schema = Json.parse("{\"type\": \"" + type + "\"}");
      Jtd.Result result = new Jtd().validate(schema, Json.parse("-1"));
      
      LOG.fine(() -> type + " with -1: valid=" + result.isValid());
      
      assertFalse(result.isValid(), type + " should reject negative values");
    }
  }

  /// Test: Type coercion edge cases
  /// Ensure no implicit type coercion happens
  @Test
  public void probeNoTypeCoercion() {
    JsonValue intSchema = Json.parse("{\"type\": \"int32\"}");
    Jtd validator = new Jtd();
    
    // String representation of number should NOT be accepted
    JsonValue stringNumber = Json.parse("\"42\"");
    Jtd.Result result = validator.validate(intSchema, stringNumber);
    
    assertFalse(result.isValid(), "String \"42\" should not be accepted as int32");
    
    // Boolean should NOT be accepted
    JsonValue bool = Json.parse("true");
    Jtd.Result boolResult = validator.validate(intSchema, bool);
    
    assertFalse(boolResult.isValid(), "Boolean should not be accepted as int32");
  }
}
