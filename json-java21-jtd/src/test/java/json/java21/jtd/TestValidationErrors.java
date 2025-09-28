package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for validation error messages and verbose error mode
/// Validates that error messages are standardized and verbose mode includes JSON values
public class TestValidationErrors extends JtdTestBase {

  /// Test that TypeSchema validation errors are standardized
  @Test
  public void testTypeSchemaErrorMessages() {
    JsonValue booleanSchema = Json.parse("{\"type\": \"boolean\"}");
    JsonValue stringSchema = Json.parse("{\"type\": \"string\"}");
    JsonValue intSchema = Json.parse("{\"type\": \"int32\"}");
    JsonValue floatSchema = Json.parse("{\"type\": \"float32\"}");
    JsonValue timestampSchema = Json.parse("{\"type\": \"timestamp\"}");
    
    JsonValue invalidData = Json.parse("123");
    
    Jtd validator = new Jtd();
    
    // Test boolean type error
    Jtd.Result booleanResult = validator.validate(booleanSchema, invalidData);
    assertThat(booleanResult.isValid()).isFalse();
    assertThat(booleanResult.errors()).hasSize(1);
    assertThat(booleanResult.errors().getFirst()).contains("expected boolean, got JsonNumber");
    
    // Test string type error
    Jtd.Result stringResult = validator.validate(stringSchema, invalidData);
    assertThat(stringResult.isValid()).isFalse();
    assertThat(stringResult.errors()).hasSize(1);
    assertThat(stringResult.errors().getFirst()).contains("expected string, got JsonNumber");
    
    // Test integer type error
    Jtd.Result intResult = validator.validate(intSchema, Json.parse("\"not-a-number\""));
    assertThat(intResult.isValid()).isFalse();
    assertThat(intResult.errors()).hasSize(1);
    assertThat(intResult.errors().getFirst()).contains("expected int32, got JsonString");
    
    // Test float type error
    Jtd.Result floatResult = validator.validate(floatSchema, Json.parse("\"not-a-float\""));
    assertThat(floatResult.isValid()).isFalse();
    assertThat(floatResult.errors()).hasSize(1);
    assertThat(floatResult.errors().getFirst()).contains("expected float32, got JsonString");
    
    // Test timestamp type error
    Jtd.Result timestampResult = validator.validate(timestampSchema, invalidData);
    assertThat(timestampResult.isValid()).isFalse();
    assertThat(timestampResult.errors()).hasSize(1);
    assertThat(timestampResult.errors().getFirst()).contains("expected timestamp (string), got JsonNumber");
    
    LOG.fine(() -> "Type schema error messages test completed successfully");
  }

  /// Test verbose error mode includes actual JSON values
  @Test
  public void testVerboseErrorMode() {
    // Test direct schema validation with verbose errors
    JsonValue schema = Json.parse("{\"type\": \"boolean\"}");
    JsonValue invalidData = Json.parse("{\"key\": \"value\", \"nested\": {\"item\": 123}}");
    
    JtdSchema jtdSchema = new Jtd().compileSchema(schema);
    
    // Test non-verbose mode
    Jtd.Result conciseResult = jtdSchema.validate(invalidData);
    assertThat(conciseResult.isValid()).isFalse();
    assertThat(conciseResult.errors()).hasSize(1);
    String conciseError = conciseResult.errors() .getFirst();
    assertThat(conciseError).doesNotContain("(was:");
    LOG.fine(() -> "Concise error: " + conciseError);
    
    // Test verbose mode
    Jtd.Result verboseResult = jtdSchema.validate(invalidData, true);
    assertThat(verboseResult.isValid()).isFalse();
    assertThat(verboseResult.errors()).hasSize(1);
    String verboseError = verboseResult.errors() .getFirst();
    assertThat(verboseError).contains("(was:");
    assertThat(verboseError).contains("\"key\"");
    assertThat(verboseError).contains("\"value\"");
    assertThat(verboseError).contains("\"nested\"");
    assertThat(verboseError).contains("\"item\"");
    assertThat(verboseError).contains("123");
    LOG.fine(() -> "Verbose error: " + verboseError);
  }

  /// Test enum schema error messages
  @Test
  public void testEnumSchemaErrorMessages() {
    JsonValue enumSchema = Json.parse("{\"enum\": [\"red\", \"green\", \"blue\"]}");
    
    Jtd validator = new Jtd();
    
    // Test invalid enum value
    Jtd.Result invalidValueResult = validator.validate(enumSchema, Json.parse("\"yellow\""));
    assertThat(invalidValueResult.isValid()).isFalse();
    assertThat(invalidValueResult.errors()).hasSize(1);
    assertThat(invalidValueResult.errors().getFirst()).contains("value 'yellow' not in enum: [red, green, blue]");
    
    // Test non-string value for enum
    Jtd.Result nonStringResult = validator.validate(enumSchema, Json.parse("123"));
    assertThat(nonStringResult.isValid()).isFalse();
    assertThat(nonStringResult.errors()).hasSize(1);
    assertThat(nonStringResult.errors().getFirst()).contains("expected string for enum, got JsonNumber");
    
    LOG.fine(() -> "Enum schema error messages test completed successfully");
  }

  /// Test array schema error messages
  @Test
  public void testArraySchemaErrorMessages() {
    JsonValue arraySchema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    
    Jtd validator = new Jtd();
    
    // Test non-array value
    Jtd.Result nonArrayResult = validator.validate(arraySchema, Json.parse("\"not-an-array\""));
    assertThat(nonArrayResult.isValid()).isFalse();
    assertThat(nonArrayResult.errors()).hasSize(1);
    assertThat(nonArrayResult.errors().getFirst()).contains("expected array, got JsonString");
    
    // Test invalid element in array
    Jtd.Result invalidElementResult = validator.validate(arraySchema, Json.parse("[\"valid\", 123, \"also-valid\"]"));
    assertThat(invalidElementResult.isValid()).isFalse();
    assertThat(invalidElementResult.errors()).hasSize(1);
    assertThat(invalidElementResult.errors().getFirst()).contains("expected string, got JsonNumber");
    
    LOG.fine(() -> "Array schema error messages test completed successfully");
  }

  /// Test object schema error messages
  @Test
  public void testObjectSchemaErrorMessages() {
    JsonValue objectSchema = Json.parse("{\"properties\": {\"name\": {\"type\": \"string\"}, \"age\": {\"type\": \"int32\"}}}");
    
    Jtd validator = new Jtd();
    
    // Test non-object value
    Jtd.Result nonObjectResult = validator.validate(objectSchema, Json.parse("\"not-an-object\""));
    assertThat(nonObjectResult.isValid()).isFalse();
    assertThat(nonObjectResult.errors()).hasSize(1);
    assertThat(nonObjectResult.errors().getFirst()).contains("expected object, got JsonString");
    
    // Test missing required property
    Jtd.Result missingPropertyResult = validator.validate(objectSchema, Json.parse("{\"name\": \"John\"}"));
    assertThat(missingPropertyResult.isValid()).isFalse();
    assertThat(missingPropertyResult.errors()).hasSize(1);
    assertThat(missingPropertyResult.errors().getFirst()).contains("missing required property: age");
    
    // Test invalid property value
    Jtd.Result invalidPropertyResult = validator.validate(objectSchema, Json.parse("{\"name\": 123, \"age\": 25}"));
    assertThat(invalidPropertyResult.isValid()).isFalse();
    assertThat(invalidPropertyResult.errors()).hasSize(1);
    assertThat(invalidPropertyResult.errors().getFirst()).contains("expected string, got JsonNumber");
    
    LOG.fine(() -> "Object schema error messages test completed successfully");
  }

  /// Test additional properties error messages
  @Test
  public void testAdditionalPropertiesErrorMessages() {
    JsonValue objectSchema = Json.parse("{\"properties\": {\"name\": {\"type\": \"string\"}}, \"additionalProperties\": false}");
    
    Jtd validator = new Jtd();
    
    // Test additional property not allowed
    Jtd.Result additionalPropResult = validator.validate(objectSchema, Json.parse("{\"name\": \"John\", \"extra\": \"not-allowed\"}"));
    assertThat(additionalPropResult.isValid()).isFalse();
    assertThat(additionalPropResult.errors()).hasSize(1);
    assertThat(additionalPropResult.errors().getFirst()).contains("additional property not allowed: extra");
    
    LOG.fine(() -> "Additional properties error messages test completed successfully");
  }

  /// Test discriminator schema error messages
  @Test
  public void testDiscriminatorSchemaErrorMessages() {
    JsonValue discriminatorSchema = Json.parse("{\"discriminator\": \"type\", \"mapping\": {\"person\": {\"properties\": {\"name\": {\"type\": \"string\"}}}}}");
    
    Jtd validator = new Jtd();
    
    // Test non-object value
    Jtd.Result nonObjectResult = validator.validate(discriminatorSchema, Json.parse("\"not-an-object\""));
    assertThat(nonObjectResult.isValid()).isFalse();
    assertThat(nonObjectResult.errors()).hasSize(1);
    assertThat(nonObjectResult.errors().getFirst()).contains("expected object, got JsonString");
    
    // Test invalid discriminator type
    Jtd.Result invalidDiscriminatorResult = validator.validate(discriminatorSchema, Json.parse("{\"type\": 123}"));
    assertThat(invalidDiscriminatorResult.isValid()).isFalse();
    assertThat(invalidDiscriminatorResult.errors()).hasSize(1);
    assertThat(invalidDiscriminatorResult.errors().getFirst()).contains("discriminator 'type' must be a string");
    
    // Test discriminator value not in mapping
    Jtd.Result unknownDiscriminatorResult = validator.validate(discriminatorSchema, Json.parse("{\"type\": \"unknown\"}"));
    assertThat(unknownDiscriminatorResult.isValid()).isFalse();
    assertThat(unknownDiscriminatorResult.errors()).hasSize(1);
    assertThat(unknownDiscriminatorResult.errors().getFirst()).contains("discriminator value 'unknown' not in mapping");
    
    LOG.fine(() -> "Discriminator schema error messages test completed successfully");
  }

  /// Test unknown type error message
  @Test
  public void testUnknownTypeErrorMessage() {
    JsonValue unknownTypeSchema = Json.parse("{\"type\": \"unknown-type\"}");
    
    Jtd validator = new Jtd();
    
    Jtd.Result result = validator.validate(unknownTypeSchema, Json.parse("\"anything\""));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("unknown type: unknown-type");
    
    LOG.fine(() -> "Unknown type error message test completed successfully");
  }

  /// Test that error messages are consistent across different schema types
  @Test
  public void testErrorMessageConsistency() {
    JsonValue invalidData = Json.parse("123");
    
    Jtd validator = new Jtd();
    
    // Test different schema types that should all expect objects
    JsonValue[] objectSchemas = {
      Json.parse("{\"properties\": {}}"),
      Json.parse("{\"values\": {\"type\": \"string\"}}"),
      Json.parse("{\"discriminator\": \"type\", \"mapping\": {}}"),
      Json.parse("{\"elements\": {\"type\": \"string\"}}") // This one expects array, not object
    };
    
    for (JsonValue schema : objectSchemas) {
      Jtd.Result result = validator.validate(schema, invalidData);
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).hasSize(1);
      String error = result.errors() .getFirst();
      
      if (schema.toString().contains("elements")) {
        // Elements schema expects array
        assertThat(error).contains("expected array, got JsonNumber");
      } else {
        // Others expect object
        assertThat(error).contains("expected object, got JsonNumber");
      }
    }
    
    LOG.fine(() -> "Error message consistency test completed successfully");
  }
}
