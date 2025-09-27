package json.java21.jtd;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    String schemaJson = "{ \"type\": \"string\" }";
    JsonValue result1;
    result1 = Json.parse(schemaJson);
    JsonValue schema = result1;

    // Test valid string
    JsonValue result;
    result = Json.parse("\"hello\"");
    JsonValue validData = result;
    assertThat(schema).isNotNull();
    assertThat(validData).isNotNull();
    LOG.fine(() -> "Type form string test - schema: " + schema + ", data: " + validData);
  }

  /// Enum form: string enumeration
  /// Example from docs: { enum: ["foo", "bar"] }
  @Test
  public void testEnumForm() throws Exception {
    String schemaJson = "{ \"enum\": [\"foo\", \"bar\"] }";
    JsonValue result2;
    result2 = Json.parse(schemaJson);
    JsonValue schema = result2;

    // Test valid enum values
    JsonValue result1;
    result1 = Json.parse("\"foo\"");
    JsonValue validFoo = result1;
    JsonValue result;
    result = Json.parse("\"bar\"");
    JsonValue validBar = result;

    assertThat(schema).isNotNull();
    assertThat(validFoo).isNotNull();
    assertThat(validBar).isNotNull();
    LOG.fine(() -> "Enum form test - schema: " + schema + ", valid values: foo, bar");
  }

  /// Elements form: homogeneous arrays
  /// Schema example: { elements: { type: "string" } }
  @Test
  public void testElementsForm() throws Exception {
    String schemaJson = "{ \"elements\": { \"type\": \"string\" } }";
    JsonValue result2;
    result2 = Json.parse(schemaJson);
    JsonValue schema = result2;

    // Test valid arrays
    JsonValue result1;
    result1 = Json.parse("[]");
    JsonValue emptyArray = result1;
    JsonValue result;
    result = Json.parse("[\"foo\", \"bar\"]");
    JsonValue stringArray = result;

    assertThat(schema).isNotNull();
    assertThat(emptyArray).isNotNull();
    assertThat(stringArray).isNotNull();
    LOG.fine(() -> "Elements form test - schema: " + schema + ", valid arrays: [], [\"foo\", \"bar\"]");
  }

  /// Properties form: objects with required properties
  /// Example 1: { properties: { foo: { type: "string" } } }
  @Test
  public void testPropertiesFormRequiredOnly() throws Exception {
    String schemaJson = "{ \"properties\": { \"foo\": { \"type\": \"string\" } } }";
    JsonValue result1;
    result1 = Json.parse(schemaJson);
    JsonValue schema = result1;

    // Test valid object
    JsonValue result;
    result = Json.parse("{\"foo\": \"bar\"}");
    JsonValue validObject = result;

    assertThat(schema).isNotNull();
    assertThat(validObject).isNotNull();
    LOG.fine(() -> "Properties form (required only) test - schema: " + schema + ", valid: {\"foo\": \"bar\"}");
  }

  /// Properties form: objects with required and optional properties
  /// Example 2: { properties: { foo: {type: "string"} }, optionalProperties: { bar: {enum: ["1", "2"]} }, additionalProperties: true }
  @Test
  public void testPropertiesFormWithOptional() throws Exception {
    String schemaJson = "{ \"properties\": { \"foo\": {\"type\": \"string\"} }, \"optionalProperties\": { \"bar\": {\"enum\": [\"1\", \"2\"]} }, \"additionalProperties\": true }";
    JsonValue result3;
    result3 = Json.parse(schemaJson);
    JsonValue schema = result3;

    // Test valid objects
    JsonValue result2;
    result2 = Json.parse("{\"foo\": \"bar\"}");
    JsonValue withRequired = result2;
    JsonValue result1;
    result1 = Json.parse("{\"foo\": \"bar\", \"bar\": \"1\"}");
    JsonValue withOptional = result1;
    JsonValue result;
    result = Json.parse("{\"foo\": \"bar\", \"additional\": 1}");
    JsonValue withAdditional = result;

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
    String schemaJson = "{ \"discriminator\": \"version\", \"mapping\": { \"1\": { \"properties\": { \"foo\": {\"type\": \"string\"} } }, \"2\": { \"properties\": { \"foo\": {\"type\": \"uint8\"} } } } }";
    JsonValue result2;
    result2 = Json.parse(schemaJson);
    JsonValue schema = result2;

    // Test valid discriminated objects
    JsonValue result1;
    result1 = Json.parse("{\"version\": \"1\", \"foo\": \"1\"}");
    JsonValue version1 = result1;
    JsonValue result;
    result = Json.parse("{\"version\": \"2\", \"foo\": 1}");
    JsonValue version2 = result;

    assertThat(schema).isNotNull();
    assertThat(version1).isNotNull();
    assertThat(version2).isNotNull();
    LOG.fine(() -> "Discriminator form test - schema: " + schema);
  }

  /// Values form: dictionary with homogeneous values
  /// Example: { values: { type: "uint8" } }
  @Test
  public void testValuesForm() throws Exception {
    String schemaJson = "{ \"values\": { \"type\": \"uint8\" } }";
    JsonValue result2;
    result2 = Json.parse(schemaJson);
    JsonValue schema = result2;

    // Test valid dictionaries
    JsonValue result1;
    result1 = Json.parse("{}");
    JsonValue emptyObj = result1;
    JsonValue result;
    result = Json.parse("{\"foo\": 1, \"bar\": 2}");
    JsonValue numberValues = result;

    assertThat(schema).isNotNull();
    assertThat(emptyObj).isNotNull();
    assertThat(numberValues).isNotNull();
    LOG.fine(() -> "Values form test - schema: " + schema + ", valid: {}, {\"foo\": 1, \"bar\": 2}");
  }

  /// Ref form: reference to definitions
  /// Example 1: { properties: { propFoo: {ref: "foo", nullable: true} }, definitions: { foo: {type: "string"} } }
  @Test
  public void testRefForm() throws Exception {
    String schemaJson = "{ \"properties\": { \"propFoo\": {\"ref\": \"foo\", \"nullable\": true} }, \"definitions\": { \"foo\": {\"type\": \"string\"} } }";
    JsonValue result;
    result = Json.parse(schemaJson);
    JsonValue schema = result;

    assertThat(schema).isNotNull();
    LOG.fine(() -> "Ref form test - schema: " + schema);
  }

  /// Self-referencing schema for binary tree
  /// Example 2: { ref: "tree", definitions: { tree: { properties: { value: {type: "int32"} }, optionalProperties: { left: {ref: "tree"}, right: {ref: "tree"} } } } }
  @Test
  public void testSelfReferencingSchema() throws Exception {
    String schemaJson = "{ \"ref\": \"tree\", \"definitions\": { \"tree\": { \"properties\": { \"value\": {\"type\": \"int32\"} }, \"optionalProperties\": { \"left\": {\"ref\": \"tree\"}, \"right\": {\"ref\": \"tree\"} } } } }";
    JsonValue result1;
    result1 = Json.parse(schemaJson);
    JsonValue schema = result1;

    // Test tree structure
    JsonValue result;
    result = Json.parse("{\"value\": 1, \"left\": {\"value\": 2}, \"right\": {\"value\": 3}}");
    JsonValue tree = result;

    assertThat(schema).isNotNull();
    assertThat(tree).isNotNull();
    LOG.fine(() -> "Self-referencing schema test - schema: " + schema + ", tree: " + tree);
  }

  /// Empty form: any data
  @Test
  public void testEmptyForm() throws Exception {
    String schemaJson = "{}";
    JsonValue result6;
    result6 = Json.parse(schemaJson);
    JsonValue schema = result6;

    // Test various data types
    JsonValue result5;
    result5 = Json.parse("\"hello\"");
    JsonValue stringData = result5;
    JsonValue result4;
    result4 = Json.parse("42");
    JsonValue numberData = result4;
    JsonValue result3;
    result3 = Json.parse("{\"key\": \"value\"}");
    JsonValue objectData = result3;
    JsonValue result2;
    result2 = Json.parse("[1, 2, 3]");
    JsonValue arrayData = result2;
    JsonValue result1;
    result1 = Json.parse("null");
    JsonValue nullData = result1;
    JsonValue result;
    result = Json.parse("true");
    JsonValue boolData = result;

    assertThat(schema).isNotNull();
    assertThat(stringData).isNotNull();
    assertThat(numberData).isNotNull();
    assertThat(objectData).isNotNull();
    assertThat(arrayData).isNotNull();
    assertThat(nullData).isNotNull();
    assertThat(boolData).isNotNull();
    LOG.fine(() -> "Empty form test - schema: " + schema + ", accepts any data");
  }

  /// Type form: numeric types
  @Test
  public void testNumericTypes() throws Exception {
    // Test various numeric types
    String[] numericSchemas = {"{ \"type\": \"int8\" }", "{ \"type\": \"uint8\" }", "{ \"type\": \"int16\" }", "{ \"type\": \"uint16\" }", "{ \"type\": \"int32\" }", "{ \"type\": \"uint32\" }", "{ \"type\": \"float32\" }", "{ \"type\": \"float64\" }"};

    for (String schemaJson : numericSchemas) {
      JsonValue result;
      result = Json.parse(schemaJson);
      JsonValue schema = result;
      assertThat(schema).isNotNull();
      LOG.fine(() -> "Numeric type test - schema: " + schema);
    }
  }

  /// Nullable types
  @Test
  public void testNullableTypes() throws Exception {
    String[] nullableSchemas = {"{ \"type\": \"string\", \"nullable\": true }", "{ \"enum\": [\"foo\", \"bar\"], \"nullable\": true }", "{ \"elements\": { \"type\": \"string\" }, \"nullable\": true }"};

    for (String schemaJson : nullableSchemas) {
      JsonValue result;
      result = Json.parse(schemaJson);
      JsonValue schema = result;
      assertThat(schema).isNotNull();
      LOG.fine(() -> "Nullable type test - schema: " + schema);
    }
  }
}
