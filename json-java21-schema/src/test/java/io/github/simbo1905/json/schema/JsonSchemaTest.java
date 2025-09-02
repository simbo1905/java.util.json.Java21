package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonSchemaTest {

    @Test
    void testStringTypeValidation() {
        String schemaJson = """
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
        assertThat(result2.errors().get(0).message()).contains("Expected string");
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
        assertThat(result2.errors().get(0).message()).contains("Missing required property: name");
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
        assertThat(result2.errors().get(0).message()).contains("Too many items");
        
        // Invalid - wrong type in array
        var result3 = schema.validate(Json.parse("[1, \"two\", 3]"));
        assertThat(result3.valid()).isFalse();
        assertThat(result3.errors().get(0).message()).contains("Expected number");
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
        assertThat(result2.errors().get(0).message()).contains("Pattern mismatch");
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
        assertThat(result2.errors().get(0).message()).contains("Not in enum");
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
        assertThat(result2.errors().get(0).path()).contains("user");
        assertThat(result2.errors().get(0).message()).contains("Missing required property: name");
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
        assertThat(result2.errors().get(0).path()).contains("billingAddress");
        assertThat(result2.errors().get(0).message()).contains("Missing required property: city");
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
        assertThat(result.errors().get(0).message()).contains("Below minimum");
        
        // Invalid - above maximum
        result = schema.validate(Json.parse("105"));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().get(0).message()).contains("Above maximum");
        
        // Invalid - not multiple of 5
        result = schema.validate(Json.parse("52"));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().get(0).message()).contains("Not multiple of");
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
}