package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonSchemaTypeAndEnumTest extends JsonSchemaTestBase {

  @Test
  void testEnum_strict_noTypeCoercion_edgeCases() {
    // Heterogeneous enum must compare with strict JSON equality (no string/number/boolean coercion)
    final var schemaJson = """
        {
          "enum": ["1", 1, true, false, 0, null, {"a":1}, [1]]
        }
        """;

    final var schema = JsonSchema.compile(Json.parse(schemaJson));

    // ✅ Exact matches (should PASS)
    assertThat(schema.validate(Json.parse("\"1\"")).valid()).isTrue();     // string "1"
    assertThat(schema.validate(Json.parse("1")).valid()).isTrue();         // number 1
    assertThat(schema.validate(Json.parse("true")).valid()).isTrue();      // boolean true
    assertThat(schema.validate(Json.parse("false")).valid()).isTrue();     // boolean false
    assertThat(schema.validate(Json.parse("0")).valid()).isTrue();         // number 0
    assertThat(schema.validate(Json.parse("null")).valid()).isTrue();      // null
    assertThat(schema.validate(Json.parse("{\"a\":1}")).valid()).isTrue();  // object
    assertThat(schema.validate(Json.parse("[1]")).valid()).isTrue();        // array

    // ❌ Look-alikes (should FAIL — ensure no coercion)
    assertThat(schema.validate(Json.parse("\"true\"")).valid()).isFalse();  // string "true" ≠ true
    assertThat(schema.validate(Json.parse("\"false\"")).valid()).isFalse(); // string "false" ≠ false
    assertThat(schema.validate(Json.parse("\"0\"")).valid()).isFalse();     // string "0" ≠ 0 (already covered positive for "1")
    assertThat(schema.validate(Json.parse("0.0")).valid()).isFalse();       // 0.0 ≠ 0 if enum stores exact numeric identity
    assertThat(schema.validate(Json.parse("1.0")).valid()).isFalse();       // 1.0 ≠ 1 if equality is strict (no coercion)
    assertThat(schema.validate(Json.parse("false")).valid()).isTrue();      // sanity: false is in enum (contrast with failures above)

    // ❌ Structural near-misses
    assertThat(schema.validate(Json.parse("{\"a\":2}")).valid()).isFalse(); // object value differs
    assertThat(schema.validate(Json.parse("[1,2]")).valid()).isFalse();     // array contents differ

    // Optional: key order should not matter for object equality (document your intended policy).
    // If your validator treats {"a":1} equal regardless of key order, this should PASS.
    assertThat(schema.validate(Json.parse("{\"a\":1}")).valid()).isTrue();
  }

    @Test
    void testTypeArray_anyOfSemantics() {
        String schemaJson = """
            {
                "type": ["string", "null"]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - string
        var result1 = schema.validate(Json.parse("\"hello\""));
        assertThat(result1.valid()).isTrue();
        
        // Valid - null
        var result2 = schema.validate(Json.parse("null"));
        assertThat(result2.valid()).isTrue();
        
        // Invalid - number
        var result3 = schema.validate(Json.parse("42"));
        assertThat(result3.valid()).isFalse();
        
        // Invalid - boolean
        var result4 = schema.validate(Json.parse("true"));
        assertThat(result4.valid()).isFalse();
    }

    @Test
    void testTypeArray_multipleTypes() {
        String schemaJson = """
            {
                "type": ["string", "number", "boolean"]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - string
        assertThat(schema.validate(Json.parse("\"hello\"")).valid()).isTrue();
        
        // Valid - number
        assertThat(schema.validate(Json.parse("42")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("3.14")).valid()).isTrue();
        
        // Valid - boolean
        assertThat(schema.validate(Json.parse("true")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("false")).valid()).isTrue();
        
        // Invalid - null
        assertThat(schema.validate(Json.parse("null")).valid()).isFalse();
        
        // Invalid - object
        assertThat(schema.validate(Json.parse("{}")).valid()).isFalse();
        
        // Invalid - array
        assertThat(schema.validate(Json.parse("[]")).valid()).isFalse();
    }

    @Test
    void testTypeArray_withStringConstraints() {
        String schemaJson = """
            {
                "type": ["string", "null"],
                "minLength": 3,
                "maxLength": 10
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - null (constraints don't apply)
        assertThat(schema.validate(Json.parse("null")).valid()).isTrue();
        
        // Valid - string within length constraints
        assertThat(schema.validate(Json.parse("\"hello\"")).valid()).isTrue();
        
        // Invalid - string too short
        assertThat(schema.validate(Json.parse("\"hi\"")).valid()).isFalse();
        
        // Invalid - string too long
        assertThat(schema.validate(Json.parse("\"this is way too long\"")).valid()).isFalse();
    }

    @Test
    void testEnum_allKinds_strict() {
        // Test enum with different JSON types
        String schemaJson = """
            {
                "enum": ["hello", 42, true, null, {"key": "value"}, [1, 2, 3]]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - exact string match
        assertThat(schema.validate(Json.parse("\"hello\"")).valid()).isTrue();
        
        // Valid - exact number match
        assertThat(schema.validate(Json.parse("42")).valid()).isTrue();
        
        // Valid - exact boolean match
        assertThat(schema.validate(Json.parse("true")).valid()).isTrue();
        
        // Valid - exact null match
        assertThat(schema.validate(Json.parse("null")).valid()).isTrue();
        
        // Valid - exact object match
        assertThat(schema.validate(Json.parse("{\"key\": \"value\"}")).valid()).isTrue();
        
        // Valid - exact array match
        assertThat(schema.validate(Json.parse("[1, 2, 3]")).valid()).isTrue();
        
        // Invalid - string not in enum
        assertThat(schema.validate(Json.parse("\"world\"")).valid()).isFalse();
        
        // Invalid - number not in enum
        assertThat(schema.validate(Json.parse("43")).valid()).isFalse();
        
        // Invalid - boolean not in enum
        assertThat(schema.validate(Json.parse("false")).valid()).isFalse();
        
        // Invalid - similar object but different
        assertThat(schema.validate(Json.parse("{\"key\": \"different\"}")).valid()).isFalse();
        
        // Invalid - similar array but different
        assertThat(schema.validate(Json.parse("[1, 2, 4]")).valid()).isFalse();
    }

    @Test
    void testEnum_withTypeConstraint() {
        String schemaJson = """
            {
                "type": "string",
                "enum": ["red", "green", "blue"]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - string in enum
        assertThat(schema.validate(Json.parse("\"red\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"green\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"blue\"")).valid()).isTrue();
        
        // Invalid - string not in enum
        assertThat(schema.validate(Json.parse("\"yellow\"")).valid()).isFalse();
        
        // Invalid - not a string
        assertThat(schema.validate(Json.parse("42")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("null")).valid()).isFalse();
    }

    @Test
    void testConst_strict_noCoercion() {
        String schemaJson = """
            {
                "const": 42
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - exact number match
        assertThat(schema.validate(Json.parse("42")).valid()).isTrue();
        
        // Invalid - different number
        assertThat(schema.validate(Json.parse("43")).valid()).isFalse();
        
        // Invalid - string representation
        assertThat(schema.validate(Json.parse("\"42\"")).valid()).isFalse();
        
        // Invalid - boolean
        assertThat(schema.validate(Json.parse("true")).valid()).isFalse();
        
        // Invalid - null
        assertThat(schema.validate(Json.parse("null")).valid()).isFalse();
    }

    @Test
    void testConst_boolean() {
        String schemaJson = """
            {
                "const": true
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - exact boolean match
        assertThat(schema.validate(Json.parse("true")).valid()).isTrue();
        
        // Invalid - different boolean
        assertThat(schema.validate(Json.parse("false")).valid()).isFalse();
        
        // Invalid - number (no coercion)
        assertThat(schema.validate(Json.parse("1")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("0")).valid()).isFalse();
    }

    @Test
    void testConst_object() {
        String schemaJson = """
            {
                "const": {"name": "Alice", "age": 30}
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - exact object match
        assertThat(schema.validate(Json.parse("{\"name\": \"Alice\", \"age\": 30}")).valid()).isTrue();
        
        // Invalid - different order (JSON equality should handle this)
        assertThat(schema.validate(Json.parse("{\"age\": 30, \"name\": \"Alice\"}")).valid()).isTrue();
        
        // Invalid - missing field
        assertThat(schema.validate(Json.parse("{\"name\": \"Alice\"}")).valid()).isFalse();
        
        // Invalid - different value
        assertThat(schema.validate(Json.parse("{\"name\": \"Bob\", \"age\": 30}")).valid()).isFalse();
    }

    @Test
    void testConst_array() {
        String schemaJson = """
            {
                "const": [1, 2, 3]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - exact array match
        assertThat(schema.validate(Json.parse("[1, 2, 3]")).valid()).isTrue();
        
        // Invalid - different order
        assertThat(schema.validate(Json.parse("[3, 2, 1]")).valid()).isFalse();
        
        // Invalid - extra element
        assertThat(schema.validate(Json.parse("[1, 2, 3, 4]")).valid()).isFalse();
        
        // Invalid - missing element
        assertThat(schema.validate(Json.parse("[1, 2]")).valid()).isFalse();
    }
}
