package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// RFC 8927 compliance tests for failing JTD specification cases
/// These are the exact test cases that were failing in JtdSpecIT
/// with explicit multiline strings for schema and JSON documents
  public class TestRfc8927Compliance extends JtdTestBase {

  /// Test ref schema with nested definitions
  /// "ref schema - nested ref" from JTD specification test suite
  /// Should resolve nested ref "bar" inside definition "foo"
  /// RFC 8927: {} accepts anything - ref to {} should also accept anything
  @Test
  public void testRefSchemaNestedRef() throws Exception {
    // Schema with nested ref: foo references bar, bar is empty schema
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "foo": {
            "ref": "bar"
          },
          "bar": {}
        },
        "ref": "foo"
      }
      """);
    
    JsonValue instance = Json.parse("\"anything\""); // RFC 8927: {} accepts anything via ref
    
    LOG.info(() -> "Testing ref schema - nested ref");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + instance);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.severe(() -> "ERRORS: " + result.errors());
    }
    
    // This should be valid according to RFC 8927
    assertThat(result.isValid())
      .as("Nested ref should resolve correctly")
      .isTrue();
  }

  /// Test ref schema with recursive definitions
  /// "ref schema - recursive schema, ok" from JTD specification test suite
  /// Should handle recursive ref to self in elements schema
  @Test
  public void testRefSchemaRecursive() throws Exception {
    // Schema with recursive ref: root references itself in elements
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
      }
      """);
    
    JsonValue instance = Json.parse("[]");
    
    LOG.info(() -> "Testing ref schema - recursive schema, ok");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + instance);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.severe(() -> "ERRORS: " + result.errors());
    }
    
    // This should be valid according to RFC 8927
    assertThat(result.isValid())
      .as("Recursive ref should resolve correctly")
      .isTrue();
  }

  /// Test timestamp with leap second
  /// "timestamp type schema - 1990-12-31T23:59:60Z" from JTD specification test suite
  /// Should accept valid RFC 3339 timestamp with leap second
  @Test
  public void testTimestampWithLeapSecond() throws Exception {
    JsonValue schema = Json.parse("""
      {
        "type": "timestamp"
      }
      """);
    
    JsonValue instance = Json.parse("\"1990-12-31T23:59:60Z\"");
    
    LOG.info(() -> "Testing timestamp type schema - 1990-12-31T23:59:60Z");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + instance);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.severe(() -> "ERRORS: " + result.errors());
    }
    
    // This should be valid according to RFC 8927 (leap second support)
    assertThat(result.isValid())
      .as("Timestamp with leap second should be valid RFC 3339")
      .isTrue();
  }

  /// Test timestamp with timezone offset
  /// "timestamp type schema - 1990-12-31T15:59:60-08:00" from JTD specification test suite
  /// Should accept valid RFC 3339 timestamp with timezone offset
  @Test
  public void testTimestampWithTimezone() throws Exception {
    JsonValue schema = Json.parse("""
      {
        "type": "timestamp"
      }
      """);
    
    JsonValue instance = Json.parse("\"1990-12-31T15:59:60-08:00\"");
    
    LOG.info(() -> "Testing timestamp type schema - 1990-12-31T15:59:60-08:00");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + instance);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.severe(() -> "ERRORS: " + result.errors());
    }
    
    // This should be valid according to RFC 8927 (timezone support)
    assertThat(result.isValid())
      .as("Timestamp with timezone offset should be valid RFC 3339")
      .isTrue();
  }

  /// Test strict properties validation
  /// "strict properties - bad additional property" from JTD specification test suite
  /// Should reject object with additional property not in schema
  @Test
  public void testStrictPropertiesBadAdditionalProperty() throws Exception {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "foo": {
            "type": "string"
          }
        }
      }
      """);
    
    JsonValue instance = Json.parse("""
      {
        "foo": "foo",
        "bar": "bar"
      }
      """);
    
    LOG.info(() -> "Testing strict properties - bad additional property");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + instance);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.fine(() -> "ERRORS: " + result.errors());
    }
    
    // This should be invalid according to RFC 8927 (strict by default)
    assertThat(result.isValid())
      .as("Object with additional property should be rejected in strict mode")
      .isFalse();
    
    // Should have error about the additional property
    assertThat(result.errors())
      .isNotEmpty()
      .anySatisfy(error -> assertThat(error).contains("bar"));
  }

  /// Test strict optional properties validation
  /// "strict optionalProperties - bad additional property" from JTD specification test suite
  /// Should reject object with additional property not in optionalProperties
  @Test
  public void testStrictOptionalPropertiesBadAdditionalProperty() throws Exception {
    JsonValue schema = Json.parse("""
      {
        "optionalProperties": {
          "foo": {
            "type": "string"
          }
        }
      }
      """);
    
    JsonValue instance = Json.parse("""
      {
        "foo": "foo",
        "bar": "bar"
      }
      """);
    
    LOG.info(() -> "Testing strict optionalProperties - bad additional property");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + instance);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.fine(() -> "ERRORS: " + result.errors());
    }
    
    // This should be invalid according to RFC 8927 (strict by default)
    assertThat(result.isValid())
      .as("Object with additional property should be rejected in strict mode")
      .isFalse();
    
    // Should have error about the additional property
    assertThat(result.errors())
      .isNotEmpty()
      .anySatisfy(error -> assertThat(error).contains("bar"));
  }

  /// Test strict mixed properties validation
  /// "strict mixed properties and optionalProperties - bad additional property" from JTD specification test suite
  /// Should reject object with additional property not in properties or optionalProperties
  @Test
  public void testStrictMixedPropertiesBadAdditionalProperty() throws Exception {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "foo": {
            "type": "string"
          }
        },
        "optionalProperties": {
          "bar": {
            "type": "string"
          }
        }
      }
      """);
    
    JsonValue instance = Json.parse("""
      {
        "foo": "foo",
        "bar": "bar",
        "baz": "baz"
      }
      """);
    
    LOG.info(() -> "Testing strict mixed properties and optionalProperties - bad additional property");
    LOG.fine(() -> "Schema: " + schema);
    LOG.fine(() -> "Instance: " + instance);
    
    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, instance);
    
    LOG.fine(() -> "Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
    if (!result.isValid()) {
      LOG.fine(() -> "ERRORS: " + result.errors());
    }
    
    // This should be invalid according to RFC 8927 (strict by default)
    assertThat(result.isValid())
      .as("Object with additional property should be rejected in strict mode")
      .isFalse();
    
    // Should have error about the additional property
    assertThat(result.errors())
      .isNotEmpty()
      .anySatisfy(error -> assertThat(error).contains("baz"));
  }
}
