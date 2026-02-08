package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests for the [JtdValidator] functional interface and [InterpreterValidator].
///
/// Exercises the RFC 8927 `(instancePath, schemaPath)` error pair format
/// produced by the interpreter path.
class JtdValidatorTest extends JtdTestBase {

  private static final Logger LOG = Logger.getLogger(JtdValidatorTest.class.getName());

  // ------------------------------------------------------------------
  // Factory smoke tests
  // ------------------------------------------------------------------

  @Test
  void compileReturnsValidatorForTypeSchema() {
    LOG.info("EXECUTING: compileReturnsValidatorForTypeSchema");
    final var validator = JtdValidator.compile(Json.parse("{\"type\": \"string\"}"));
    assertThat(validator).isNotNull();
    assertThat(validator.validate(Json.parse("\"hello\"")).isValid()).isTrue();
  }

  @Test
  void compileGeneratedThrowsWhenCodegenNotOnClasspath() {
    LOG.info("EXECUTING: compileGeneratedThrowsWhenCodegenNotOnClasspath");
    assertThatThrownBy(() -> JtdValidator.compileGenerated(Json.parse("{\"type\": \"string\"}")))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Codegen module not on classpath");
  }

  // ------------------------------------------------------------------
  // Empty form
  // ------------------------------------------------------------------

  @Test
  void emptySchemaAcceptsAnything() {
    LOG.info("EXECUTING: emptySchemaAcceptsAnything");
    final var v = JtdValidator.compile(Json.parse("{}"));
    assertThat(v.validate(Json.parse("null")).isValid()).isTrue();
    assertThat(v.validate(Json.parse("42")).isValid()).isTrue();
    assertThat(v.validate(Json.parse("\"hi\"")).isValid()).isTrue();
    assertThat(v.validate(Json.parse("[1,2]")).isValid()).isTrue();
    assertThat(v.validate(Json.parse("{\"a\":1}")).isValid()).isTrue();
  }

  // ------------------------------------------------------------------
  // Type form -- error paths
  // ------------------------------------------------------------------

  @Test
  void typeStringRejectsNumberWithCorrectPaths() {
    LOG.info("EXECUTING: typeStringRejectsNumberWithCorrectPaths");
    final var v = JtdValidator.compile(Json.parse("{\"type\": \"string\"}"));
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst().instancePath()).isEqualTo("");
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/type");
  }

  @Test
  void typeBooleanValid() {
    LOG.info("EXECUTING: typeBooleanValid");
    final var v = JtdValidator.compile(Json.parse("{\"type\": \"boolean\"}"));
    assertThat(v.validate(Json.parse("true")).isValid()).isTrue();
    assertThat(v.validate(Json.parse("false")).isValid()).isTrue();
  }

  @Test
  void typeUint8OutOfRangeErrors() {
    LOG.info("EXECUTING: typeUint8OutOfRangeErrors");
    final var v = JtdValidator.compile(Json.parse("{\"type\": \"uint8\"}"));
    final var result = v.validate(Json.parse("300"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/type");
  }

  // ------------------------------------------------------------------
  // Enum form
  // ------------------------------------------------------------------

  @Test
  void enumRejectsUnknownValueWithEnumPath() {
    LOG.info("EXECUTING: enumRejectsUnknownValueWithEnumPath");
    final var v = JtdValidator.compile(Json.parse("{\"enum\": [\"a\", \"b\"]}"));
    final var result = v.validate(Json.parse("\"c\""));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().instancePath()).isEqualTo("");
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/enum");
  }

  @Test
  void enumRejectsNonStringWithEnumPath() {
    LOG.info("EXECUTING: enumRejectsNonStringWithEnumPath");
    final var v = JtdValidator.compile(Json.parse("{\"enum\": [\"a\", \"b\"]}"));
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/enum");
  }

  // ------------------------------------------------------------------
  // Elements form
  // ------------------------------------------------------------------

  @Test
  void elementsRejectsNonArrayAtRootPath() {
    LOG.info("EXECUTING: elementsRejectsNonArrayAtRootPath");
    final var v = JtdValidator.compile(Json.parse("{\"elements\": {\"type\": \"string\"}}"));
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().instancePath()).isEqualTo("");
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/elements");
  }

  @Test
  void elementsReportsChildErrorsWithCorrectPaths() {
    LOG.info("EXECUTING: elementsReportsChildErrorsWithCorrectPaths");
    final var v = JtdValidator.compile(Json.parse("{\"elements\": {\"type\": \"string\"}}"));
    final var result = v.validate(Json.parse("[\"ok\", 42, \"fine\", true]"));
    assertThat(result.isValid()).isFalse();
    LOG.fine(() -> "Errors: " + result.errors());

    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/1") && e.schemaPath().equals("/elements/type"));
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/3") && e.schemaPath().equals("/elements/type"));
  }

  // ------------------------------------------------------------------
  // Properties form
  // ------------------------------------------------------------------

  @Test
  void propertiesRejectsNonObjectWithPropertiesPath() {
    LOG.info("EXECUTING: propertiesRejectsNonObjectWithPropertiesPath");
    final var v = JtdValidator.compile(Json.parse(
        "{\"properties\": {\"name\": {\"type\": \"string\"}}}"));
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().instancePath()).isEqualTo("");
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/properties");
  }

  @Test
  void optionalPropertiesOnlyRejectsNonObjectWithOptionalPath() {
    LOG.info("EXECUTING: optionalPropertiesOnlyRejectsNonObjectWithOptionalPath");
    final var v = JtdValidator.compile(Json.parse(
        "{\"optionalProperties\": {\"email\": {\"type\": \"string\"}}}"));
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().instancePath()).isEqualTo("");
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/optionalProperties");
  }

  @Test
  void propertiesMissingRequiredKeyError() {
    LOG.info("EXECUTING: propertiesMissingRequiredKeyError");
    final var schema = Json.parse("""
        {"properties": {"name": {"type": "string"}, "age": {"type": "uint8"}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("{\"name\": \"Alice\"}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("") && e.schemaPath().equals("/properties/age"));
  }

  @Test
  void propertiesAdditionalPropertyError() {
    LOG.info("EXECUTING: propertiesAdditionalPropertyError");
    final var schema = Json.parse("""
        {"properties": {"name": {"type": "string"}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("{\"name\": \"Alice\", \"extra\": true}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/extra") && e.schemaPath().equals(""));
  }

  @Test
  void propertiesChildValueError() {
    LOG.info("EXECUTING: propertiesChildValueError");
    final var schema = Json.parse("""
        {"properties": {"age": {"type": "uint8"}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("{\"age\": \"not a number\"}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/age") && e.schemaPath().equals("/properties/age/type"));
  }

  @Test
  void optionalPropertiesChildValueError() {
    LOG.info("EXECUTING: optionalPropertiesChildValueError");
    final var schema = Json.parse("""
        {"optionalProperties": {"email": {"type": "string"}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("{\"email\": 42}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/email")
            && e.schemaPath().equals("/optionalProperties/email/type"));
  }

  // ------------------------------------------------------------------
  // Values form
  // ------------------------------------------------------------------

  @Test
  void valuesRejectsNonObjectAtRootPath() {
    LOG.info("EXECUTING: valuesRejectsNonObjectAtRootPath");
    final var v = JtdValidator.compile(Json.parse("{\"values\": {\"type\": \"string\"}}"));
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/values");
  }

  @Test
  void valuesReportsChildErrors() {
    LOG.info("EXECUTING: valuesReportsChildErrors");
    final var v = JtdValidator.compile(Json.parse("{\"values\": {\"type\": \"string\"}}"));
    final var result = v.validate(Json.parse("{\"a\": \"ok\", \"b\": 42}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/b") && e.schemaPath().equals("/values/type"));
  }

  // ------------------------------------------------------------------
  // Discriminator form
  // ------------------------------------------------------------------

  @Test
  void discriminatorNotObjectError() {
    LOG.info("EXECUTING: discriminatorNotObjectError");
    final var schema = Json.parse("""
        {"discriminator": "type", "mapping": {"a": {"properties": {"x": {"type": "string"}}}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/discriminator");
  }

  @Test
  void discriminatorMissingTagError() {
    LOG.info("EXECUTING: discriminatorMissingTagError");
    final var schema = Json.parse("""
        {"discriminator": "type", "mapping": {"a": {"properties": {"x": {"type": "string"}}}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("{\"x\": 1}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().instancePath()).isEqualTo("");
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/discriminator");
  }

  @Test
  void discriminatorTagNotStringError() {
    LOG.info("EXECUTING: discriminatorTagNotStringError");
    final var schema = Json.parse("""
        {"discriminator": "type", "mapping": {"a": {"properties": {"x": {"type": "string"}}}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("{\"type\": 42}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().instancePath()).isEqualTo("/type");
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/discriminator");
  }

  @Test
  void discriminatorTagNotInMappingError() {
    LOG.info("EXECUTING: discriminatorTagNotInMappingError");
    final var schema = Json.parse("""
        {"discriminator": "type", "mapping": {"a": {"properties": {"x": {"type": "string"}}}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("{\"type\": \"unknown\"}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().instancePath()).isEqualTo("/type");
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/mapping");
  }

  @Test
  void discriminatorVariantValidationErrors() {
    LOG.info("EXECUTING: discriminatorVariantValidationErrors");
    final var schema = Json.parse("""
        {"discriminator": "type", "mapping": {"a": {"properties": {"x": {"type": "string"}}}}}
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(Json.parse("{\"type\": \"a\", \"x\": 42}"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/x")
            && e.schemaPath().equals("/mapping/a/properties/x/type"));
  }

  // ------------------------------------------------------------------
  // Nullable
  // ------------------------------------------------------------------

  @Test
  void nullableAcceptsNull() {
    LOG.info("EXECUTING: nullableAcceptsNull");
    final var v = JtdValidator.compile(Json.parse("{\"type\": \"string\", \"nullable\": true}"));
    assertThat(v.validate(Json.parse("null")).isValid()).isTrue();
    assertThat(v.validate(Json.parse("\"hi\"")).isValid()).isTrue();
  }

  @Test
  void nullableStillRejectsWrongType() {
    LOG.info("EXECUTING: nullableStillRejectsWrongType");
    final var v = JtdValidator.compile(Json.parse("{\"type\": \"string\", \"nullable\": true}"));
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/type");
  }

  // ------------------------------------------------------------------
  // Ref form
  // ------------------------------------------------------------------

  @Test
  void refValidatesViaDefinition() {
    LOG.info("EXECUTING: refValidatesViaDefinition");
    final var schema = Json.parse("""
        {"definitions": {"addr": {"type": "string"}}, "ref": "addr"}
        """);
    final var v = JtdValidator.compile(schema);
    assertThat(v.validate(Json.parse("\"hello\"")).isValid()).isTrue();
    final var result = v.validate(Json.parse("42"));
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors().getFirst().schemaPath()).isEqualTo("/definitions/addr/type");
  }

  // ------------------------------------------------------------------
  // toString returns original schema JSON
  // ------------------------------------------------------------------

  @Test
  void toStringReturnsSchemaJson() {
    LOG.info("EXECUTING: toStringReturnsSchemaJson");
    final var schemaJson = "{\"type\": \"string\"}";
    final var v = JtdValidator.compile(Json.parse(schemaJson));
    assertThat(v.toString()).isNotEmpty();
    LOG.fine(() -> "toString: " + v);
  }

  // ------------------------------------------------------------------
  // Functional interface usage (stream pipeline)
  // ------------------------------------------------------------------

  @Test
  void usableInStreamPipeline() {
    LOG.info("EXECUTING: usableInStreamPipeline");
    final var v = JtdValidator.compile(Json.parse("{\"type\": \"string\"}"));
    final var docs = java.util.List.of(
        Json.parse("\"a\""), Json.parse("42"), Json.parse("\"b\""), Json.parse("true"));
    final var invalid = docs.stream()
        .filter(doc -> !v.validate(doc).isValid())
        .toList();
    assertThat(invalid).hasSize(2);
  }

  // ------------------------------------------------------------------
  // Worked example from JTD_STACK_MACHINE_SPEC.md §10
  // ------------------------------------------------------------------

  @Test
  void workedExampleFromSpec() {
    LOG.info("EXECUTING: workedExampleFromSpec");
    final var schema = Json.parse("""
        {
          "properties": {
            "name": {"type": "string"},
            "age": {"type": "uint8"},
            "tags": {"elements": {"type": "string"}}
          },
          "optionalProperties": {
            "email": {"type": "string"}
          }
        }
        """);
    final var instance = Json.parse("""
        {
          "name": "Alice",
          "age": 300,
          "tags": ["a", 42],
          "extra": true
        }
        """);
    final var v = JtdValidator.compile(schema);
    final var result = v.validate(instance);
    assertThat(result.isValid()).isFalse();

    LOG.fine(() -> "Errors: " + result.errors());

    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/extra") && e.schemaPath().equals(""));
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/age") && e.schemaPath().equals("/properties/age/type"));
    assertThat(result.errors()).anyMatch(e ->
        e.instancePath().equals("/tags/1") && e.schemaPath().equals("/properties/tags/elements/type"));
  }
}
