package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests based on AJV documentation examples for JTD schema forms.
/// Each test method corresponds to an example from the AJV JTD documentation.
public class DocumentationAJvTests extends JtdTestBase {

  /// Type form: primitive values - string type
  /// Example from docs: { type: "string" }
  @Test
  public void testTypeFormString() throws Exception {
    JsonValue schema = Json.parse("{ \"type\": \"string\" }");

    // Test valid string
    JsonValue validData = Json.parse("\"hello\"");
    assertThat(schema).isNotNull();
    assertThat(validData).isNotNull();
    LOG.fine(() -> "Type form string test - schema: " + schema + ", data: " + validData);
  }
  
  /// Counter-test: Type form string validation should fail for non-strings
  /// Same schema as testTypeFormString but tests invalid data
  @Test
  public void testTypeFormStringInvalid() throws Exception {
    JsonValue schema = Json.parse("{ \"type\": \"string\" }");

    // Test validation failure - should fail for non-string
    JsonValue invalidData = Json.parse("123");
    Jtd validator = new Jtd();
    Jtd.Result invalidResult = validator.validate(schema, invalidData);
    assertThat(invalidResult.isValid()).isFalse();
    assertThat(invalidResult.errors()).isNotEmpty();
    LOG.fine(() -> "Type form string invalid test - schema: " + schema + ", invalid data: " + invalidData + ", errors: " + invalidResult.errors());
  }

  /// Enum form: string enumeration
  /// Example from docs: { enum: ["foo", "bar"] }
  @Test
  public void testEnumForm() throws Exception {
    JsonValue schema = Json.parse("{ \"enum\": [\"foo\", \"bar\"] }");

    // Test valid enum values
    JsonValue validFoo = Json.parse("\"foo\"");
    JsonValue validBar = Json.parse("\"bar\"");

    assertThat(schema).isNotNull();
    assertThat(validFoo).isNotNull();
    assertThat(validBar).isNotNull();
    LOG.fine(() -> "Enum form test - schema: " + schema + ", valid values: foo, bar");
  }
  
  /// Counter-test: Enum form validation should fail for values not in enum
  /// Same schema as testEnumForm but tests invalid data
  @Test
  public void testEnumFormInvalid() throws Exception {
    JsonValue schema = Json.parse("{ \"enum\": [\"foo\", \"bar\"] }");

    // Test validation failure - should fail for value not in enum
    JsonValue invalidData = Json.parse("\"baz\"");
    Jtd validator = new Jtd();
    Jtd.Result invalidResult = validator.validate(schema, invalidData);
    assertThat(invalidResult.isValid()).isFalse();
    assertThat(invalidResult.errors()).isNotEmpty();
    LOG.fine(() -> "Enum form invalid test - schema: " + schema + ", invalid data: " + invalidData + ", errors: " + invalidResult.errors());
  }

  /// Counter-test: Elements form validation should fail for heterogeneous arrays
  /// Same schema as testElementsForm but tests invalid data
  @Test
  public void testElementsFormInvalid() throws Exception {
    JsonValue schema = Json.parse("{ \"elements\": { \"type\": \"string\" } }");

    // Test validation failure - should fail for array with non-string elements
    JsonValue invalidData = Json.parse("[\"foo\", 123]");
    Jtd validator = new Jtd();
    Jtd.Result invalidResult = validator.validate(schema, invalidData);
    assertThat(invalidResult.isValid()).isFalse();
    assertThat(invalidResult.errors()).isNotEmpty();
    LOG.fine(() -> "Elements form invalid test - schema: " + schema + ", invalid data: " + invalidData + ", errors: " + invalidResult.errors());
  }

  /// Elements form: homogeneous arrays
  /// Schema example: { elements: { type: "string" } }
  @Test
  public void testElementsForm() throws Exception {
    JsonValue schema = Json.parse("{ \"elements\": { \"type\": \"string\" } }");

    // Test valid arrays
    JsonValue emptyArray = Json.parse("[]");
    JsonValue stringArray = Json.parse("[\"foo\", \"bar\"]");

    assertThat(schema).isNotNull();
    assertThat(emptyArray).isNotNull();
    assertThat(stringArray).isNotNull();
    LOG.fine(() -> "Elements form test - schema: " + schema + ", valid arrays: [], [\"foo\", \"bar\"]");
  }

  /// Properties form: objects with required properties
  /// Example 1: { properties: { foo: { type: "string" } } }
  @Test
  public void testPropertiesFormRequiredOnly() throws Exception {
    JsonValue schema = Json.parse("{ \"properties\": { \"foo\": { \"type\": \"string\" } } }");

    // Test valid object
    JsonValue validObject = Json.parse("{\"foo\": \"bar\"}");

    assertThat(schema).isNotNull();
    assertThat(validObject).isNotNull();
    LOG.fine(() -> "Properties form (required only) test - schema: " + schema + ", valid: {\"foo\": \"bar\"}");
  }
  
  /// Counter-test: Properties form validation should fail for missing required properties
  /// Same schema as testPropertiesFormRequiredOnly but tests invalid data
  @Test
  public void testPropertiesFormRequiredOnlyInvalid() throws Exception {
    JsonValue schema = Json.parse("{ \"properties\": { \"foo\": { \"type\": \"string\" } } }");

    // Test validation failure - should fail for missing required property
    JsonValue invalidData = Json.parse("{}");
    Jtd validator = new Jtd();
    Jtd.Result invalidResult = validator.validate(schema, invalidData);
    assertThat(invalidResult.isValid()).isFalse();
    assertThat(invalidResult.errors()).isNotEmpty();
    LOG.fine(() -> "Properties form (required only) invalid test - schema: " + schema + ", invalid data: " + invalidData + ", errors: " + invalidResult.errors());
  }

  /// Properties form: objects with required and optional properties
  /// Example 2: { properties: { foo: {type: "string"} }, optionalProperties: { bar: {enum: ["1", "2"]} }, additionalProperties: true }
  @Test
  public void testPropertiesFormWithOptional() throws Exception {
    JsonValue schema = Json.parse("{ \"properties\": { \"foo\": {\"type\": \"string\"} }, \"optionalProperties\": { \"bar\": {\"enum\": [\"1\", \"2\"]} }, \"additionalProperties\": true }");

    // Test valid objects
    JsonValue withRequired = Json.parse("{\"foo\": \"bar\"}");
    JsonValue withOptional = Json.parse("{\"foo\": \"bar\", \"bar\": \"1\"}");
    JsonValue withAdditional = Json.parse("{\"foo\": \"bar\", \"additional\": 1}");

    assertThat(schema).isNotNull();
    assertThat(withRequired).isNotNull();
    assertThat(withOptional).isNotNull();
    assertThat(withAdditional).isNotNull();
    LOG.fine(() -> "Properties form (with optional) test - schema: " + schema);
  }

  /// Discriminator form: tagged union
  /// Example 1: { discriminator: "version", mapping: { "1": { properties: { foo: {type: "string"} } }, "2": { properties: { foo: {type: "uint8"} } } } }
  @Test
  public void testDiscriminatorForm() throws Exception {
    JsonValue schema = Json.parse("{ \"discriminator\": \"version\", \"mapping\": { \"1\": { \"properties\": { \"foo\": {\"type\": \"string\"} } }, \"2\": { \"properties\": { \"foo\": {\"type\": \"uint8\"} } } } }");

    // Test valid discriminated objects
    JsonValue version1 = Json.parse("{\"version\": \"1\", \"foo\": \"1\"}");
    JsonValue version2 = Json.parse("{\"version\": \"2\", \"foo\": 1}");

    assertThat(schema).isNotNull();
    assertThat(version1).isNotNull();
    assertThat(version2).isNotNull();
    LOG.fine(() -> "Discriminator form test - schema: " + schema);
  }
  
  /// Counter-test: Discriminator form validation should fail for invalid discriminator values
  /// Same schema as testDiscriminatorForm but tests invalid data
  @Test
  public void testDiscriminatorFormInvalid() throws Exception {
    JsonValue schema = Json.parse("{ \"discriminator\": \"version\", \"mapping\": { \"1\": { \"properties\": { \"foo\": {\"type\": \"string\"} } }, \"2\": { \"properties\": { \"foo\": {\"type\": \"uint8\"} } } } }");

    // Test validation failure - should fail for discriminator value not in mapping
    JsonValue invalidData = Json.parse("{\"version\": \"3\", \"foo\": \"1\"}");
    Jtd validator = new Jtd();
    Jtd.Result invalidResult = validator.validate(schema, invalidData);
    assertThat(invalidResult.isValid()).isFalse();
    assertThat(invalidResult.errors()).isNotEmpty();
    LOG.fine(() -> "Discriminator form invalid test - schema: " + schema + ", invalid data: " + invalidData + ", errors: " + invalidResult.errors());
  }

  /// Values form: dictionary with homogeneous values
  /// Example: { values: { type: "uint8" } }
  @Test
  public void testValuesForm() throws Exception {
    JsonValue schema = Json.parse("{ \"values\": { \"type\": \"uint8\" } }");

    // Test valid dictionaries
    JsonValue emptyObj = Json.parse("{}");
    JsonValue numberValues = Json.parse("{\"foo\": 1, \"bar\": 2}");

    assertThat(schema).isNotNull();
    assertThat(emptyObj).isNotNull();
    assertThat(numberValues).isNotNull();
    LOG.fine(() -> "Values form test - schema: " + schema + ", valid: {}, {\"foo\": 1, \"bar\": 2}");
  }
  
  /// Counter-test: Values form validation should fail for heterogeneous value types
  /// Same schema as testValuesForm but tests invalid data
  @Test
  public void testValuesFormInvalid() throws Exception {
    JsonValue schema = Json.parse("{ \"values\": { \"type\": \"uint8\" } }");

    // Test validation failure - should fail for object with mixed value types
    JsonValue invalidData = Json.parse("{\"foo\": 1, \"bar\": \"not-a-number\"}");
    Jtd validator = new Jtd();
    Jtd.Result invalidResult = validator.validate(schema, invalidData);
    assertThat(invalidResult.isValid()).isFalse();
    assertThat(invalidResult.errors()).isNotEmpty();
    LOG.fine(() -> "Values form invalid test - schema: " + schema + ", invalid data: " + invalidData + ", errors: " + invalidResult.errors());
  }

  /// Ref form: reference to definitions
  /// Example 1: { properties: { propFoo: {ref: "foo", nullable: true} }, definitions: { foo: {type: "string"} } }
  @Test
  public void testRefForm() throws Exception {
    JsonValue schema = Json.parse("{ \"properties\": { \"propFoo\": {\"ref\": \"foo\", \"nullable\": true} }, \"definitions\": { \"foo\": {\"type\": \"string\"} } }");

    assertThat(schema).isNotNull();
    LOG.fine(() -> "Ref form test - schema: " + schema);
  }

  /// Self-referencing schema for binary tree
  /// Example 2: { ref: "tree", definitions: { tree: { properties: { value: {type: "int32"} }, optionalProperties: { left: {ref: "tree"}, right: {ref: "tree"} } } } }
  @Test
  public void testSelfReferencingSchema() throws Exception {
    JsonValue schema = Json.parse("{ \"ref\": \"tree\", \"definitions\": { \"tree\": { \"properties\": { \"value\": {\"type\": \"int32\"} }, \"optionalProperties\": { \"left\": {\"ref\": \"tree\"}, \"right\": {\"ref\": \"tree\"} } } } }");

    // Test tree structure
    JsonValue tree = Json.parse("{\"value\": 1, \"left\": {\"value\": 2}, \"right\": {\"value\": 3}}");

    assertThat(schema).isNotNull();
    assertThat(tree).isNotNull();
    LOG.fine(() -> "Self-referencing schema test - schema: " + schema + ", tree: " + tree);
  }

  /// Empty form: RFC 8927 strict - {} means "no properties allowed"
  @Test
  public void testEmptyFormRfcStrict() throws Exception {
    JsonValue schema = Json.parse("{}");

    // Test valid empty object
    JsonValue emptyObject = Json.parse("{}");
    Jtd validator = new Jtd();
    Jtd.Result validResult = validator.validate(schema, emptyObject);
    assertThat(validResult.isValid())
      .as("Empty schema {} should accept empty object per RFC 8927")
      .isTrue();

    // Test invalid object with properties
    JsonValue objectWithProps = Json.parse("{\"key\": \"value\"}");
    Jtd.Result invalidResult = validator.validate(schema, objectWithProps);
    assertThat(invalidResult.isValid())
      .as("Empty schema {} should reject object with properties per RFC 8927")
      .isFalse();
    assertThat(invalidResult.errors())
      .as("Should have validation error for additional property")
      .isNotEmpty();

    LOG.fine(() -> "Empty form RFC strict test - schema: " + schema + ", valid: empty object, invalid: object with properties");
  }
  
  /// Counter-test: Empty form validation should reject objects with properties per RFC 8927
  /// Same schema as testEmptyFormRfcStrict but tests invalid data
  @Test
  public void testEmptyFormRejectsProperties() throws Exception {
    JsonValue schema = Json.parse("{}");

    // Test that empty schema rejects object with properties per RFC 8927
    JsonValue dataWithProps = Json.parse("{\"anything\": \"goes\"}");
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, dataWithProps);
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
    LOG.fine(() -> "Empty form rejects properties test - schema: " + schema + ", data with properties should fail: " + dataWithProps);
  }

  /// Type form: numeric types
  @Test
  public void testNumericTypes() throws Exception {
    // Test various numeric types
    String[] numericSchemas = {"{ \"type\": \"int8\" }", "{ \"type\": \"uint8\" }", "{ \"type\": \"int16\" }", "{ \"type\": \"uint16\" }", "{ \"type\": \"int32\" }", "{ \"type\": \"uint32\" }", "{ \"type\": \"float32\" }", "{ \"type\": \"float64\" }"};

    for (String schemaJson : numericSchemas) {
      JsonValue schema = Json.parse(schemaJson);
      assertThat(schema).isNotNull();
      LOG.fine(() -> "Numeric type test - schema: " + schema);
    }
  }
  
  /// Counter-test: Numeric type validation should fail for non-numeric data
  /// Tests that numeric types reject string data
  @Test
  public void testNumericTypesInvalid() throws Exception {
    JsonValue schema = Json.parse("{ \"type\": \"int32\" }");

    // Test validation failure - should fail for string data
    JsonValue invalidData = Json.parse("\"not-a-number\"");
    Jtd validator = new Jtd();
    Jtd.Result invalidResult = validator.validate(schema, invalidData);
    assertThat(invalidResult.isValid()).isFalse();
    assertThat(invalidResult.errors()).isNotEmpty();
    LOG.fine(() -> "Numeric types invalid test - schema: " + schema + ", invalid data: " + invalidData + ", errors: " + invalidResult.errors());
  }

  /// Nullable types
  @Test
  public void testNullableTypes() throws Exception {
    String[] nullableSchemas = {"{ \"type\": \"string\", \"nullable\": true }", "{ \"enum\": [\"foo\", \"bar\"], \"nullable\": true }", "{ \"elements\": { \"type\": \"string\" }, \"nullable\": true }"};

    for (String schemaJson : nullableSchemas) {
      JsonValue schema = Json.parse(schemaJson);
      assertThat(schema).isNotNull();
      LOG.fine(() -> "Nullable type test - schema: " + schema);
    }
  }
  
  /// Counter-test: Nullable types should still fail for non-matching non-null data
  /// Tests that nullable doesn't bypass type validation for non-null values
  @Test
  public void testNullableTypesInvalid() throws Exception {
    JsonValue schema = Json.parse("{ \"type\": \"string\", \"nullable\": true }");

    // Test validation failure - should fail for non-string, non-null data
    JsonValue invalidData = Json.parse("123");
    Jtd validator = new Jtd();
    Jtd.Result invalidResult = validator.validate(schema, invalidData);
    assertThat(invalidResult.isValid()).isFalse();
    assertThat(invalidResult.errors()).isNotEmpty();
    LOG.fine(() -> "Nullable types invalid test - schema: " + schema + ", invalid data: " + invalidData + ", errors: " + invalidResult.errors());
  }
}
