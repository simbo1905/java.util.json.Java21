package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonSchemaTest extends JsonSchemaLoggingConfig {

    @Test
    void testStringTypeValidation() {
        io.github.simbo1905.json.schema.SchemaLogging.LOG.info("TEST: JsonSchemaTest#testStringTypeValidation");        String schemaJson = """
            {
                "type": "string"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid string
        var result = schema.validate(Json.parse("\"hello\""));
        assertThat(result.valid()).isTrue();
        
        // Invalid - number instead of string
        var result2 = schema.validate(Json.parse("42"));
        assertThat(result2.valid()).isFalse();
        assertThat(result2.errors()).hasSize(1);
        assertThat(result2.errors().getFirst().message()).contains("Expected string");
    }

    @Test
    void testObjectWithRequiredProperties() {
        String schemaJson = """
            {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "integer", "minimum": 0}
                },
                "required": ["name"]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - has required name
        String validJson = """
            {"name": "Alice", "age": 30}
            """;
        var result = schema.validate(Json.parse(validJson));
        assertThat(result.valid()).isTrue();
        
        // Invalid - missing required name
        String invalidJson = """
            {"age": 30}
            """;
        var result2 = schema.validate(Json.parse(invalidJson));
        assertThat(result2.valid()).isFalse();
        assertThat(result2.errors().getFirst().message()).contains("Missing required property: name");
    }

    @Test
    void testArrayWithItemsConstraint() {
        String schemaJson = """
            {
                "type": "array",
                "items": {"type": "number"},
                "minItems": 1,
                "maxItems": 3
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid array
        var result = schema.validate(Json.parse("[1, 2, 3]"));
        assertThat(result.valid()).isTrue();
        
        // Invalid - too many items
        var result2 = schema.validate(Json.parse("[1, 2, 3, 4]"));
        assertThat(result2.valid()).isFalse();
        assertThat(result2.errors().getFirst().message()).contains("Too many items");
        
        // Invalid - wrong type in array
        var result3 = schema.validate(Json.parse("[1, \"two\", 3]"));
        assertThat(result3.valid()).isFalse();
        assertThat(result3.errors().getFirst().message()).contains("Expected number");
    }

    @Test
    void testStringPatternValidation() {
        String schemaJson = """
            {
                "type": "string",
                "pattern": "^[A-Z]{3}-\\\\d{3}$"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid pattern
        var result = schema.validate(Json.parse("\"ABC-123\""));
        assertThat(result.valid()).isTrue();
        
        // Invalid pattern
        var result2 = schema.validate(Json.parse("\"abc-123\""));
        assertThat(result2.valid()).isFalse();
        assertThat(result2.errors().getFirst().message()).contains("Pattern mismatch");
    }

    @Test
    void testEnumValidation() {
        String schemaJson = """
            {
                "type": "string",
                "enum": ["red", "green", "blue"]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid enum value
        var result = schema.validate(Json.parse("\"red\""));
        assertThat(result.valid()).isTrue();
        
        // Invalid - not in enum
        var result2 = schema.validate(Json.parse("\"yellow\""));
        assertThat(result2.valid()).isFalse();
        assertThat(result2.errors().getFirst().message()).contains("Not in enum");
    }

    @Test
    void testNestedObjectValidation() {
        String schemaJson = """
            {
                "type": "object",
                "properties": {
                    "user": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "email": {"type": "string"}
                        },
                        "required": ["name"]
                    }
                }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        String validJson = """
            {
                "user": {
                    "name": "Bob",
                    "email": "bob@example.com"
                }
            }
            """;
        var result = schema.validate(Json.parse(validJson));
        assertThat(result.valid()).isTrue();
        
        String invalidJson = """
            {
                "user": {
                    "email": "bob@example.com"
                }
            }
            """;
        var result2 = schema.validate(Json.parse(invalidJson));
        assertThat(result2.valid()).isFalse();
        assertThat(result2.errors().getFirst().path()).contains("user");
        assertThat(result2.errors().getFirst().message()).contains("Missing required property: name");
    }

    @Test
    void testAllOfComposition() {
        String schemaJson = """
            {
                "allOf": [
                    {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"}
                        },
                        "required": ["name"]
                    },
                    {
                        "type": "object",
                        "properties": {
                            "age": {"type": "number"}
                        },
                        "required": ["age"]
                    }
                ]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - satisfies both schemas
        String validJson = """
            {"name": "Alice", "age": 30}
            """;
        var result = schema.validate(Json.parse(validJson));
        assertThat(result.valid()).isTrue();
        
        // Invalid - missing age
        String invalidJson = """
            {"name": "Alice"}
            """;
        var result2 = schema.validate(Json.parse(invalidJson));
        assertThat(result2.valid()).isFalse();
    }

    @Test
    void testReferenceResolution() {
        String schemaJson = """
            {
                "$defs": {
                    "address": {
                        "type": "object",
                        "properties": {
                            "street": {"type": "string"},
                            "city": {"type": "string"}
                        },
                        "required": ["city"]
                    }
                },
                "type": "object",
                "properties": {
                    "billingAddress": {"$ref": "#/$defs/address"},
                    "shippingAddress": {"$ref": "#/$defs/address"}
                }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        String validJson = """
            {
                "billingAddress": {"street": "123 Main", "city": "NYC"},
                "shippingAddress": {"city": "Boston"}
            }
            """;
        var result = schema.validate(Json.parse(validJson));
        assertThat(result.valid()).isTrue();
        
        String invalidJson = """
            {
                "billingAddress": {"street": "123 Main"}
            }
            """;
        var result2 = schema.validate(Json.parse(invalidJson));
        assertThat(result2.valid()).isFalse();
        assertThat(result2.errors().getFirst().path()).contains("billingAddress");
        assertThat(result2.errors().getFirst().message()).contains("Missing required property: city");
    }

    @Test
    void testNumberConstraints() {
        String schemaJson = """
            {
                "type": "number",
                "minimum": 0,
                "maximum": 100,
                "multipleOf": 5
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid
        assertThat(schema.validate(Json.parse("50")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("0")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("100")).valid()).isTrue();
        
        // Invalid - below minimum
        var result = schema.validate(Json.parse("-5"));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().getFirst().message()).contains("Below minimum");
        
        // Invalid - above maximum
        result = schema.validate(Json.parse("105"));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().getFirst().message()).contains("Above maximum");
        
        // Invalid - not multiple of 5
        result = schema.validate(Json.parse("52"));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().getFirst().message()).contains("Not multiple of");
    }

    @Test
    void testBooleanSchema() {
        // true schema accepts everything
        JsonSchema trueSchema = JsonSchema.compile(Json.parse("true"));
        assertThat(trueSchema.validate(Json.parse("\"anything\"")).valid()).isTrue();
        assertThat(trueSchema.validate(Json.parse("42")).valid()).isTrue();
        
        // false schema rejects everything
        JsonSchema falseSchema = JsonSchema.compile(Json.parse("false"));
        assertThat(falseSchema.validate(Json.parse("\"anything\"")).valid()).isFalse();
        assertThat(falseSchema.validate(Json.parse("42")).valid()).isFalse();
    }

    @Test
    void testUnsatisfiableSchema() {
        String schemaJson = """
            {
                "allOf": [
                    {"type": "integer"},
                    {"not": {"type": "integer"}}
                ]
            }
            """;

        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // Any value should fail validation since the schema is unsatisfiable
        var result = schema.validate(Json.parse("42"));
        assertThat(result.valid()).isFalse();

        result = schema.validate(Json.parse("\"string\""));
        assertThat(result.valid()).isFalse();
    }

    @Test
    void testArrayItemsValidation() {
        String schemaJson = """
            {
                "type": "array",
                "items": {
                    "type": "integer",
                    "minimum": 0,
                    "maximum": 100
                },
                "minItems": 2,
                "uniqueItems": true
            }
            """;

        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // Valid array
        var result = schema.validate(Json.parse("[1, 2, 3]"));
        assertThat(result.valid()).isTrue();

        // Invalid - contains non-integer
        result = schema.validate(Json.parse("[1, \"2\", 3]"));
        assertThat(result.valid()).isFalse();

        // Invalid - number out of range
        result = schema.validate(Json.parse("[1, 101]"));
        assertThat(result.valid()).isFalse();

        // Invalid - duplicate items
        result = schema.validate(Json.parse("[1, 1, 2]"));
        assertThat(result.valid()).isFalse();

        // Invalid - too few items
        result = schema.validate(Json.parse("[1]"));
        assertThat(result.valid()).isFalse();
    }

    @Test
    void testConditionalValidation() {
        String schemaJson = """
            {
                "type": "object",
                "properties": {
                    "country": {"type": "string"},
                    "postal_code": {"type": "string"}
                },
                "required": ["country", "postal_code"],
                "allOf": [
                    {
                        "if": {
                            "properties": {"country": {"const": "US"}},
                            "required": ["country"]
                        },
                        "then": {
                            "properties": {
                                "postal_code": {"pattern": "^[0-9]{5}(-[0-9]{4})?$"}
                            }
                        }
                    },
                    {
                        "if": {
                            "properties": {"country": {"const": "CA"}},
                            "required": ["country"]
                        },
                        "then": {
                            "properties": {
                                "postal_code": {"pattern": "^[A-Z][0-9][A-Z] [0-9][A-Z][0-9]$"}
                            }
                        }
                    }
                ]
            }
            """;

        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // Valid US postal code
        var result = schema.validate(Json.parse("""
            {"country": "US", "postal_code": "12345"}
            """));
        assertThat(result.valid()).isTrue();

        // Valid US postal code with extension
        result = schema.validate(Json.parse("""
            {"country": "US", "postal_code": "12345-6789"}
            """));
        assertThat(result.valid()).isTrue();

        // Valid Canadian postal code
        result = schema.validate(Json.parse("""
            {"country": "CA", "postal_code": "M5V 2H1"}
            """));
        assertThat(result.valid()).isTrue();

        // Invalid US postal code
        result = schema.validate(Json.parse("""
            {"country": "US", "postal_code": "1234"}
            """));
        assertThat(result.valid()).isFalse();

        // Invalid Canadian postal code
        result = schema.validate(Json.parse("""
            {"country": "CA", "postal_code": "12345"}
            """));
        assertThat(result.valid()).isFalse();
    }

    @Test
    void testComplexRecursiveSchema() {
        String schemaJson = """
            {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "name": {"type": "string"},
                    "children": {
                        "type": "array",
                        "items": {"$ref": "#"}
                    }
                },
                "required": ["id", "name"]
            }
            """;
        io.github.simbo1905.json.schema.SchemaLogging.LOG.info("TEST: JsonSchemaTest#testComplexRecursiveSchema");

        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // Valid recursive structure
        var result = schema.validate(Json.parse("""
            {
                "id": "root",
                "name": "Root Node",
                "children": [
                    {
                        "id": "child1",
                        "name": "Child 1",
                        "children": []
                    },
                    {
                        "id": "child2",
                        "name": "Child 2",
                        "children": [
                            {
                                "id": "grandchild1",
                                "name": "Grandchild 1"
                            }
                        ]
                    }
                ]
            }
            """));
        assertThat(result.valid()).isTrue();

        // Invalid - missing required field in nested object
        result = schema.validate(Json.parse("""
            {
                "id": "root",
                "name": "Root Node",
                "children": [
                    {
                        "id": "child1",
                        "children": []
                    }
                ]
            }
            """));
        assertThat(result.valid()).isFalse();
    }

    @Test
    void testStringFormatValidation() {
        String schemaJson = """
            {
                "type": "object",
                "properties": {
                    "email": {
                        "type": "string",
                        "pattern": "^[^@]+@[^@]+\\\\.[^@]+$"
                    },
                    "url": {
                        "type": "string",
                        "pattern": "^https?://[^\\\\s/$.?#].[^\\\\s]*$"
                    },
                    "date": {
                        "type": "string",
                        "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}$"
                    }
                }
            }
            """;

        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // Valid formats
        var result = schema.validate(Json.parse("""
            {
                "email": "user@example.com",
                "url": "https://example.com",
                "date": "2025-09-02"
            }
            """));
        assertThat(result.valid()).isTrue();

        // Invalid email
        result = schema.validate(Json.parse("""
            {"email": "invalid-email"}
            """));
        assertThat(result.valid()).isFalse();

        // Invalid URL
        result = schema.validate(Json.parse("""
            {"url": "not-a-url"}
            """));
        assertThat(result.valid()).isFalse();

        // Invalid date
        result = schema.validate(Json.parse("""
            {"date": "2025/09/02"}
            """));
        assertThat(result.valid()).isFalse();
    }

    @Test
    void linkedListRecursion() {
        String schema = """
          {
            "type":"object",
            "properties": {
              "value": { "type":"integer" },
              "next":  { "$ref":"#" }
            },
            "required":["value"]
          }""";
        JsonSchema s = JsonSchema.compile(Json.parse(schema));

        assertThat(s.validate(Json.parse("""
          {"value":1,"next":{"value":2,"next":{"value":3}}}
        """)).valid()).isTrue();            // ✓ valid

        io.github.simbo1905.json.schema.SchemaLogging.LOG.info("TEST: JsonSchemaTest#linkedListRecursion");
        assertThat(s.validate(Json.parse("""
          {"value":1,"next":{"next":{"value":3}}}
        """)).valid()).isFalse();           // ✗ missing value
    }

    @Test
    void binaryTreeRecursion() {
        io.github.simbo1905.json.schema.SchemaLogging.LOG.info("TEST: JsonSchemaTest#binaryTreeRecursion");        String schema = """
          {
            "type":"object",
            "properties":{
              "id":   {"type":"string"},
              "left": {"$ref":"#"},
              "right":{"$ref":"#"}
            },
            "required":["id"]
          }""";
        JsonSchema s = JsonSchema.compile(Json.parse(schema));

        assertThat(s.validate(Json.parse("""
          {"id":"root","left":{"id":"L"},
           "right":{"id":"R","left":{"id":"RL"}}}
        """)).valid()).isTrue();            // ✓ valid

        assertThat(s.validate(Json.parse("""
          {"id":"root","right":{"left":{}}}
        """)).valid()).isFalse();           // ✗ missing id
    }
}
