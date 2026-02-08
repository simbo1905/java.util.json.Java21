package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/// Probes for Elements form edge cases and potential issues
/// 
/// Areas to probe:
/// 1. Empty arrays
/// 2. Nested elements
/// 3. Elements with complex schemas
/// 4. Large arrays
/// 5. Array element error reporting
public class ElementsEdgeCaseProbe extends JtdTestBase {

  /// Test: Empty array validation
  @Test
  public void probeEmptyArray() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    
    JsonValue emptyArray = Json.parse("[]");
    Jtd.Result result = new Jtd().validate(schema, emptyArray);
    
    LOG.info(() -> "Empty array validation: " + result.isValid());
    assertTrue(result.isValid(), "Empty array should be valid");
    assertThat(result.errors()).isEmpty();
  }

  /// Test: Single element array
  @Test
  public void probeSingleElementArray() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"int32\"}}");
    
    assertTrue(new Jtd().validate(schema, Json.parse("[42]")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("[\"not-int\"]")).isValid());
    
    LOG.info(() -> "Single element array: passed");
  }

  /// Test: Nested elements (2D array)
  @Test
  public void probeNestedElements2D() {
    JsonValue schema = Json.parse("{\"elements\": {\"elements\": {\"type\": \"string\"}}}");
    
    // Valid 2D array
    assertTrue(new Jtd().validate(schema, Json.parse("[[\"a\", \"b\"], [\"c\"]]")).isValid());
    
    // Invalid inner element
    Jtd.Result result = new Jtd().validate(schema, Json.parse("[[\"a\"], [123]]"));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Nested elements 2D: " + !result.isValid());
  }

  /// Test: Deeply nested elements (3D array)
  @Test
  public void probeNestedElements3D() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "elements": {
            "elements": {"type": "int32"}
          }
        }
      }
      """);
    
    // Valid 3D array
    JsonValue valid = Json.parse("[[[1, 2], [3]], [[4]]]");
    assertTrue(new Jtd().validate(schema, valid).isValid());
    
    // Invalid at deepest level
    JsonValue invalid = Json.parse("[[[1, \"bad\"], [3]]]");
    Jtd.Result result = new Jtd().validate(schema, invalid);
    assertFalse(result.isValid());
    
    LOG.info(() -> "Nested elements 3D: " + !result.isValid());
  }

  /// Test: Elements with properties schema
  @Test
  public void probeElementsWithProperties() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "properties": {
            "name": {"type": "string"},
            "age": {"type": "int32"}
          }
        }
      }
      """);
    
    // Valid array of objects
    JsonValue valid = Json.parse("""
      [
        {"name": "Alice", "age": 30},
        {"name": "Bob", "age": 25}
      ]
      """);
    assertTrue(new Jtd().validate(schema, valid).isValid());
    
    // Invalid: missing required property
    JsonValue invalid = Json.parse("[{\"name\": \"Charlie\"}]");
    Jtd.Result result = new Jtd().validate(schema, invalid);
    
    LOG.info(() -> "Elements with properties: " + !result.isValid());
  }

  /// Test: Elements with discriminator
  @Test
  public void probeElementsWithDiscriminator() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "discriminator": "type",
          "mapping": {
            "a": {"properties": {"value": {"type": "string"}}},
            "b": {"properties": {"count": {"type": "int32"}}}
          }
        }
      }
      """);
    
    // Valid heterogeneous array
    JsonValue valid = Json.parse("""
      [
        {"type": "a", "value": "test"},
        {"type": "b", "count": 5}
      ]
      """);
    assertTrue(new Jtd().validate(schema, valid).isValid());
    
    LOG.info(() -> "Elements with discriminator: passed");
  }

  /// Test: Elements error collection (all elements validated)
  @Test
  public void probeElementsErrorCollection() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    
    // All elements invalid
    JsonValue instance = Json.parse("[1, 2, 3, 4, 5]");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Elements error count: " + result.errors().size());
    
    assertFalse(result.isValid());
    
    // All 5 should be reported
    assertThat(result.errors().size())
        .as("Should report errors for all invalid elements")
        .isGreaterThanOrEqualTo(5);
  }

  /// Test: Elements with additionalProperties in nested object
  @Test
  public void probeElementsWithStrictNestedObjects() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "properties": {
            "id": {"type": "int32"}
          },
          "additionalProperties": false
        }
      }
      """);
    
    // Valid
    assertTrue(new Jtd().validate(schema, Json.parse("[{\"id\": 1}]")).isValid());
    
    // Invalid: extra property in element
    Jtd.Result result = new Jtd().validate(schema, Json.parse("[{\"id\": 1, \"extra\": \"bad\"}]"));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Elements with strict nested: " + !result.isValid());
  }

  /// Test: Large array performance probe
  @Test
  public void probeLargeArray() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"int32\"}}");
    
    // Build large array
    StringBuilder arrayBuilder = new StringBuilder("[");
    int count = 1000;
    for (int i = 0; i < count; i++) {
      if (i > 0) arrayBuilder.append(", ");
      arrayBuilder.append(i);
    }
    arrayBuilder.append("]");
    
    JsonValue instance = Json.parse(arrayBuilder.toString());
    
    long start = System.currentTimeMillis();
    Jtd.Result result = new Jtd().validate(schema, instance);
    long elapsed = System.currentTimeMillis() - start;
    
    LOG.info(() -> "Large array (" + count + " elements): valid=" + result.isValid() + ", time=" + elapsed + "ms");
    
    assertTrue(result.isValid());
    assertThat(elapsed).isLessThan(5000); // Should complete in reasonable time
  }

  /// Test: Array with null elements
  @Test
  public void probeArrayWithNullElements() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    
    // Null element should be invalid (string type rejects null)
    JsonValue instance = Json.parse("[\"valid\", null, \"also-valid\"]");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Array with null element: " + !result.isValid());
    assertFalse(result.isValid());
  }

  /// Test: Array with nullable elements
  @Test
  public void probeArrayWithNullableElements() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\", \"nullable\": true}}");
    
    // Null element should now be valid
    JsonValue instance = Json.parse("[\"valid\", null, \"also-valid\"]");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Array with nullable elements: " + result.isValid());
    assertTrue(result.isValid());
  }

  /// Test: Mixed valid and invalid elements
  @Test
  public void probeMixedValidInvalidElements() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    
    JsonValue instance = Json.parse("[\"a\", \"b\", 123, \"c\", 456]");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Mixed elements error count: " + result.errors().size());
    
    assertFalse(result.isValid());
    
    // Should have 2 errors (for the 2 invalid elements)
    List<String> errors = result.errors();
    long errorCount = errors.stream().filter(e -> e.contains("expected string")).count();
    
    LOG.info(() -> "Type errors found: " + errorCount);
    assertThat(errorCount).isGreaterThanOrEqualTo(2);
  }

  /// Test: Empty schema in elements (accepts anything)
  @Test
  public void probeElementsWithEmptySchema() {
    JsonValue schema = Json.parse("{\"elements\": {}}");
    
    // Should accept any elements
    assertTrue(new Jtd().validate(schema, Json.parse("[1, \"two\", true, null, []]")).isValid());
    assertTrue(new Jtd().validate(schema, Json.parse("[]")).isValid());
    
    LOG.info(() -> "Elements with empty schema: passed");
  }

  /// Test: Elements with ref
  @Test
  public void probeElementsWithRef() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "item": {"type": "int32"}
        },
        "elements": {"ref": "item"}
      }
      """);
    
    assertTrue(new Jtd().validate(schema, Json.parse("[1, 2, 3]")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("[1, \"two\", 3]")).isValid());
    
    LOG.info(() -> "Elements with ref: passed");
  }

  /// Test: Elements error path construction
  @Test
  public void probeElementsErrorPaths() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    
    JsonValue instance = Json.parse("[\"a\", 123]");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    String error = result.errors().get(0);
    LOG.info(() -> "Elements error: " + error);
    
    // Error should indicate array index
    assertThat(error)
        .as("Error should reference array element")
        .containsAnyOf("1", "elements");
  }

  /// Test: Nested elements error path
  @Test
  public void probeNestedElementsErrorPath() {
    JsonValue schema = Json.parse("{\"elements\": {\"elements\": {\"type\": \"string\"}}}");
    
    JsonValue instance = Json.parse("[[\"a\"], [\"b\", 123]]");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    String error = result.errors().get(0);
    LOG.info(() -> "Nested elements error: " + error);
    
    // Should indicate nested position
    assertThat(error)
        .as("Error should indicate nested array position")
        .containsAnyOf("1", "elements");
  }

  /// Test: Array with object having additional properties
  @Test
  public void probeElementsWithObjectAdditionalProperties() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "properties": {
            "id": {"type": "int32"}
          },
          "additionalProperties": false
        }
      }
      """);
    
    JsonValue instance = Json.parse("""
      [
        {"id": 1},
        {"id": 2, "extra": "bad"},
        {"id": 3}
      ]
      """);
    
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Elements with object having extra props: " + !result.isValid());
    assertFalse(result.isValid());
  }

  /// Test: Multiple arrays in same schema
  @Test
  public void probeMultipleArrays() {
    JsonValue schema = Json.parse("""
      {
        "properties": {
          "names": {"elements": {"type": "string"}},
          "ages": {"elements": {"type": "int32"}}
        }
      }
      """);
    
    // Valid
    JsonValue valid = Json.parse("""
      {
        "names": ["Alice", "Bob"],
        "ages": [30, 25]
      }
      """);
    assertTrue(new Jtd().validate(schema, valid).isValid());
    
    // Invalid in second array
    JsonValue invalid = Json.parse("""
      {
        "names": ["Alice", "Bob"],
        "ages": [30, "twenty-five"]
      }
      """);
    Jtd.Result result = new Jtd().validate(schema, invalid);
    assertFalse(result.isValid());
    
    LOG.info(() -> "Multiple arrays: " + !result.isValid());
  }

  /// Test: Elements with values (object values schema)
  @Test
  public void probeElementsWithValuesSchema() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "values": {"type": "int32"}
        }
      }
      """);
    
    // Array of objects with int values
    JsonValue valid = Json.parse("[\"a\": 1, \"b\": 2}, {\"c\": 3}]");
    assertTrue(new Jtd().validate(schema, valid).isValid());
    
    LOG.info(() -> "Elements with values schema: passed");
  }

  /// Test: Elements with enum
  @Test
  public void probeElementsWithEnum() {
    JsonValue schema = Json.parse("{\"elements\": {\"enum\": [\"red\", \"green\", \"blue\"]}}");
    
    assertTrue(new Jtd().validate(schema, Json.parse("[\"red\", \"blue\"]")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("[\"red\", \"yellow\"]")).isValid());
    
    LOG.info(() -> "Elements with enum: passed");
  }

  /// Test: Sparse array (with undefined/null gaps)
  @Test
  public void probeSparseArray() {
    // JSON doesn't really have sparse arrays, but let's check null handling
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\", \"nullable\": true}}");
    
    // Explicit null elements
    JsonValue instance = Json.parse("[\"a\", null, \"c\"]");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Array with nulls: " + result.isValid());
    assertTrue(result.isValid());
  }

  /// Test: Elements with complex nested structure
  @Test
  public void probeElementsWithComplexStructure() {
    JsonValue schema = Json.parse("""
      {
        "elements": {
          "properties": {
            "users": {
              "elements": {
                "properties": {
                  "name": {"type": "string"},
                  "roles": {
                    "elements": {"enum": [\"admin\", \"user\"]}
                  }
                }
              }
            }
          }
        }
      }
      """);
    
    // Valid complex structure
    JsonValue valid = Json.parse("""
      [
        {
          "users": [
            {"name": "Alice", "roles": ["admin", "user"]},
            {"name": "Bob", "roles": ["user"]}
          ]
        }
      ]
      """);
    
    assertTrue(new Jtd().validate(schema, valid).isValid());
    
    LOG.info(() -> "Complex nested elements: passed");
  }

  /// Test: Array type guard (non-array rejection)
  @Test
  public void probeElementsTypeGuard() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"string\"}}");
    
    // Non-array values should be rejected
    assertFalse(new Jtd().validate(schema, Json.parse("\"not-array\"")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("123")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("{}")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("null")).isValid());
    
    LOG.info(() -> "Elements type guard: passed");
  }

  /// Test: Elements with timestamp type
  @Test
  public void probeElementsWithTimestamp() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"timestamp\"}}");
    
    // Valid timestamps
    assertTrue(new Jtd().validate(schema, Json.parse("[\"2023-01-01T00:00:00Z\"]")).isValid());
    
    // Invalid timestamp
    assertFalse(new Jtd().validate(schema, Json.parse("[\"invalid\"]")).isValid());
    
    LOG.info(() -> "Elements with timestamp: passed");
  }

  /// Test: Elements with boolean type
  @Test
  public void probeElementsWithBoolean() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"boolean\"}}");
    
    assertTrue(new Jtd().validate(schema, Json.parse("[true, false, true]")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("[true, \"false\"]")).isValid());
    
    LOG.info(() -> "Elements with boolean: passed");
  }

  /// Test: Elements with float type
  @Test
  public void probeElementsWithFloat() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"float64\"}}");
    
    assertTrue(new Jtd().validate(schema, Json.parse("[1.5, -3.14, 1e10]")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("[1.5, \"not-float\"]")).isValid());
    
    LOG.info(() -> "Elements with float: passed");
  }

  /// Test: Elements with integer types and boundary values
  @Test
  public void probeElementsWithIntegerBoundaries() {
    JsonValue schema = Json.parse("{\"elements\": {\"type\": \"uint8\"}}");
    
    // At boundary
    assertTrue(new Jtd().validate(schema, Json.parse("[0, 127, 255]")).isValid());
    
    // Outside boundary
    assertFalse(new Jtd().validate(schema, Json.parse("[256]")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("[-1]")).isValid());
    
    LOG.info(() -> "Elements with uint8 boundaries: passed");
  }
}
