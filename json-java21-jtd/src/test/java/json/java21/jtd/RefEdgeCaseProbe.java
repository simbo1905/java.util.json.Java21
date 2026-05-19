package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/// Probes for Ref schema edge cases and potential issues
/// 
/// Areas to probe:
/// 1. Forward references
/// 2. Circular/recursive references
/// 3. Nested references
/// 4. Ref in different contexts (elements, values, properties)
/// 5. Ref to different form types
public class RefEdgeCaseProbe extends JtdTestBase {

  /// Test: Forward reference resolution
  /// Definitions can reference each other in any order
  @Test
  public void probeForwardReference() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "a": {"ref": "b"},
          "b": {"type": "string"}
        },
        "ref": "a"
      }
      """);
    
    JsonValue valid = Json.parse("\"test\"");
    Jtd.Result result = new Jtd().validate(schema, valid);
    
    LOG.info(() -> "Forward reference result: " + result.isValid());
    assertTrue(result.isValid(), "Forward reference should resolve");
  }

  /// Test: Mutual recursion
  /// Two definitions that reference each other
  @Test
  public void probeMutualRecursion() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "parent": {
            "properties": {
              "children": {"elements": {"ref": "child"}}
            }
          },
          "child": {
            "properties": {
              "parent": {"ref": "parent"}
            }
          }
        },
        "ref": "parent"
      }
      """);
    
    // This is a valid instance (empty children, no parent reference)
    JsonValue valid = Json.parse("{\"children\": []}");
    Jtd.Result result = new Jtd().validate(schema, valid);
    
    LOG.info(() -> "Mutual recursion result: " + result.isValid());
    
    // Should compile and validate
    assertTrue(result.isValid(), "Mutual recursion should work");
  }

  /// Test: Deeply nested refs
  @Test
  public void probeDeeplyNestedRefs() {
    // Create a chain of references
    StringBuilder schemaBuilder = new StringBuilder();
    schemaBuilder.append("{\"definitions\": {");
    
    int depth = 50;
    
    for (int i = 0; i < depth; i++) {
      if (i > 0) schemaBuilder.append(", ");
      schemaBuilder.append("\"level").append(i).append("\": {");
      if (i < depth - 1) {
        schemaBuilder.append("\"ref\": \"level").append(i + 1).append("\"");
      } else {
        schemaBuilder.append("\"type\": \"string\"");
      }
      schemaBuilder.append("}");
    }
    
    schemaBuilder.append("}, \"ref\": \"level0\"}");
    
    JsonValue schema = Json.parse(schemaBuilder.toString());
    JsonValue instance = Json.parse("\"test\"");
    
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Deeply nested refs (" + depth + "): " + result.isValid());
    assertTrue(result.isValid(), "Deep reference chain should resolve");
  }

  /// Test: Ref in elements context
  @Test
  public void probeRefInElements() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "item": {"type": "string"}
        },
        "elements": {"ref": "item"}
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Valid
    assertTrue(validator.validate(schema, Json.parse("[\"a\", \"b\", \"c\"]")).isValid());
    
    // Invalid element
    Jtd.Result result = validator.validate(schema, Json.parse("[\"a\", 123]"));
    assertFalse(result.isValid(), "Should reject invalid element via ref");
    
    LOG.info(() -> "Ref in elements: error count=" + result.errors().size());
  }

  /// Test: Ref in values context
  @Test
  public void probeRefInValues() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "value": {"type": "int32"}
        },
        "values": {"ref": "value"}
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Valid
    assertTrue(validator.validate(schema, Json.parse("{\"a\": 1, \"b\": 2}")).isValid());
    
    // Invalid value
    Jtd.Result result = validator.validate(schema, Json.parse("{\"a\": \"not-int\"}"));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Ref in values: " + !result.isValid());
  }

  /// Test: Ref in properties context
  @Test
  public void probeRefInProperties() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "address": {
            "properties": {
              "street": {"type": "string"},
              "city": {"type": "string"}
            }
          }
        },
        "properties": {
          "home": {"ref": "address"},
          "work": {"ref": "address"}
        }
      }
      """);
    
    JsonValue valid = Json.parse("""
      {
        "home": {"street": "123 Main", "city": "Boston"},
        "work": {"street": "456 Oak", "city": "NYC"}
      }
      """);
    
    Jtd.Result result = new Jtd().validate(schema, valid);
    assertTrue(result.isValid(), "Ref in properties should work");
    
    LOG.info(() -> "Ref in properties: " + result.isValid());
  }

  /// Test: Ref to empty schema
  @Test
  public void probeRefToEmptySchema() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "anything": {}
        },
        "ref": "anything"
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Empty schema accepts anything
    assertTrue(validator.validate(schema, Json.parse("null")).isValid());
    assertTrue(validator.validate(schema, Json.parse("123")).isValid());
    assertTrue(validator.validate(schema, Json.parse("\"string\"")).isValid());
    assertTrue(validator.validate(schema, Json.parse("[]")).isValid());
    assertTrue(validator.validate(schema, Json.parse("{}")).isValid());
    
    LOG.info(() -> "Ref to empty schema accepts anything (correct)");
  }

  /// Test: Ref to discriminator
  @Test
  public void probeRefToDiscriminator() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "shape": {
            "discriminator": "type",
            "mapping": {
              "circle": {"properties": {"radius": {"type": "float64"}}},
              "square": {"properties": {"side": {"type": "float64"}}}
            }
          }
        },
        "ref": "shape"
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Valid circles
    assertTrue(validator.validate(schema, Json.parse("{\"type\": \"circle\", \"radius\": 5.0}")).isValid());
    
    // Valid squares
    assertTrue(validator.validate(schema, Json.parse("{\"type\": \"square\", \"side\": 10.0}")).isValid());
    
    // Invalid
    Jtd.Result result = validator.validate(schema, Json.parse("{\"type\": \"unknown\"}"));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Ref to discriminator: " + !result.isValid());
  }

  /// Test: Ref to nullable schema
  @Test
  public void probeRefToNullableSchema() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "maybeString": {
            "type": "string",
            "nullable": true
          }
        },
        "properties": {
          "field": {"ref": "maybeString"}
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Valid: null
    assertTrue(validator.validate(schema, Json.parse("{\"field\": null}")).isValid());
    
    // Valid: string
    assertTrue(validator.validate(schema, Json.parse("{\"field\": \"test\"}")).isValid());
    
    // Invalid: number
    assertFalse(validator.validate(schema, Json.parse("{\"field\": 123}")).isValid());
    
    LOG.info(() -> "Ref to nullable: test passed");
  }

  /// Test: Ref to elements
  @Test
  public void probeRefToElements() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "stringArray": {
            "elements": {"type": "string"}
          }
        },
        "ref": "stringArray"
      }
      """);
    
    // Valid
    assertTrue(new Jtd().validate(schema, Json.parse("[\"a\", \"b\"]")).isValid());
    
    // Invalid
    Jtd.Result result = new Jtd().validate(schema, Json.parse("[1, 2, 3]"));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Ref to elements: " + !result.isValid());
  }

  /// Test: Ref to values
  @Test
  public void probeRefToValues() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "intMap": {
            "values": {"type": "int32"}
          }
        },
        "ref": "intMap"
      }
      """);
    
    // Valid
    assertTrue(new Jtd().validate(schema, Json.parse("{\"a\": 1, \"b\": 2}")).isValid());
    
    // Invalid
    Jtd.Result result = new Jtd().validate(schema, Json.parse("{\"a\": \"string\"}"));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Ref to values: " + !result.isValid());
  }

  /// Test: Ref to properties
  @Test
  public void probeRefToProperties() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "person": {
            "properties": {
              "name": {"type": "string"}
            }
          }
        },
        "ref": "person"
      }
      """);
    
    // Valid
    assertTrue(new Jtd().validate(schema, Json.parse("{\"name\": \"Alice\"}")).isValid());
    
    // Invalid: missing required
    Jtd.Result result = new Jtd().validate(schema, Json.parse("{}"));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Ref to properties: " + !result.isValid());
  }

  /// Test: Ref to enum
  @Test
  public void probeRefToEnum() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "color": {
            "enum": ["red", "green", "blue"]
          }
        },
        "ref": "color"
      }
      """);
    
    // Valid
    assertTrue(new Jtd().validate(schema, Json.parse("\"red\"")).isValid());
    
    // Invalid
    Jtd.Result result = new Jtd().validate(schema, Json.parse("\"yellow\""));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Ref to enum: " + !result.isValid());
  }

  /// Test: Ref to type
  @Test
  public void probeRefToType() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "id": {"type": "int32"}
        },
        "ref": "id"
      }
      """);
    
    // Valid
    assertTrue(new Jtd().validate(schema, Json.parse("42")).isValid());
    
    // Invalid
    Jtd.Result result = new Jtd().validate(schema, Json.parse("\"not-int\""));
    assertFalse(result.isValid());
    
    LOG.info(() -> "Ref to type: " + !result.isValid());
  }

  /// Test: Unused definitions
  @Test
  public void probeUnusedDefinitions() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "used": {"type": "string"},
          "unused": {"type": "int32"}
        },
        "ref": "used"
      }
      """);
    
    // Should compile fine even with unused definition
    JsonValue instance = Json.parse("\"test\"");
    Jtd.Result result = new Jtd().validate(schema, instance);
    
    LOG.info(() -> "Unused definitions: " + result.isValid());
    assertTrue(result.isValid());
  }

  /// Test: Multiple refs to same definition
  @Test
  public void probeMultipleRefsToSameDefinition() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "nameType": {"type": "string"}
        },
        "properties": {
          "firstName": {"ref": "nameType"},
          "lastName": {"ref": "nameType"},
          "middleName": {"ref": "nameType"}
        }
      }
      """);
    
    JsonValue instance = Json.parse("""
      {
        "firstName": "John",
        "lastName": "Doe",
        "middleName": "Q"
      }
      """);
    
    Jtd.Result result = new Jtd().validate(schema, instance);
    assertTrue(result.isValid());
    
    LOG.info(() -> "Multiple refs to same def: " + result.isValid());
  }

  /// Test: Recursive ref with complex nesting
  @Test
  public void probeComplexRecursiveRef() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "tree": {
            "properties": {
              "value": {"type": "string"},
              "left": {"nullable": true, "ref": "tree"},
              "right": {"nullable": true, "ref": "tree"}
            }
          }
        },
        "ref": "tree"
      }
      """);
    
    // Valid binary tree
    JsonValue tree = Json.parse("""
      {
        "value": "root",
        "left": {
          "value": "left",
          "left": null,
          "right": null
        },
        "right": {
          "value": "right",
          "left": null,
          "right": null
        }
      }
      """);
    
    Jtd.Result result = new Jtd().validate(schema, tree);
    LOG.info(() -> "Complex recursive ref: " + result.isValid());
    assertTrue(result.isValid());
  }

  /// Test: Ref in optionalProperties
  @Test
  public void probeRefInOptionalProperties() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "details": {"type": "string"}
        },
        "optionalProperties": {
          "info": {"ref": "details"}
        }
      }
      """);
    
    Jtd validator = new Jtd();
    
    // Missing - valid
    assertTrue(validator.validate(schema, Json.parse("{}")).isValid());
    
    // Present and valid
    assertTrue(validator.validate(schema, Json.parse("{\"info\": \"test\"}")).isValid());
    
    // Present but invalid type
    assertFalse(validator.validate(schema, Json.parse("{\"info\": 123}")).isValid());
    
    LOG.info(() -> "Ref in optionalProperties: passed");
  }

  /// Test: Ref resolution at multiple levels
  @Test
  public void probeMultiLevelRefResolution() {
    JsonValue schema = Json.parse("""
      {
        "definitions": {
          "level1": {"ref": "level2"},
          "level2": {"ref": "level3"},
          "level3": {"type": "boolean"}
        },
        "ref": "level1"
      }
      """);
    
    assertTrue(new Jtd().validate(schema, Json.parse("true")).isValid());
    assertFalse(new Jtd().validate(schema, Json.parse("\"not-bool\"")).isValid());
    
    LOG.info(() -> "Multi-level ref resolution: passed");
  }
}
