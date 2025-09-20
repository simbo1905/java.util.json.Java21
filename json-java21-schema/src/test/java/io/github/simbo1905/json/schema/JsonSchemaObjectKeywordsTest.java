package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonSchemaObjectKeywordsTest extends JsonSchemaLoggingConfig {

    @Test
    void additionalPropertiesFalseDisallowsUnknown() {
        String schemaJson = """
            {
              "type": "object",
              "properties": {"name": {"type": "string"}},
              "additionalProperties": false
            }
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        var result = schema.validate(Json.parse("""
            {"name":"Alice","extra": 123}
        """));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().getFirst().path()).isEqualTo("extra");
    }

    @Test
    void additionalPropertiesSchemaValidatesUnknown() {
        String schemaJson = """
            {
              "type": "object",
              "properties": {"id": {"type": "integer"}},
              "additionalProperties": {"type": "string"}
            }
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // invalid because extra is not a string
        var bad = schema.validate(Json.parse("""
            {"id": 1, "extra": 999}
        """));
        assertThat(bad.valid()).isFalse();
        assertThat(bad.errors().getFirst().path()).isEqualTo("extra");
        assertThat(bad.errors().getFirst().message()).contains("Expected string");

        // valid because extra is a string
        var ok = schema.validate(Json.parse("""
            {"id": 1, "extra": "note"}
        """));
        assertThat(ok.valid()).isTrue();
    }

    @Test
    void minAndMaxPropertiesAreEnforced() {
        String schemaJson = """
            {
              "type": "object",
              "minProperties": 2,
              "maxProperties": 3
            }
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        var tooFew = schema.validate(Json.parse("""
            {"a": 1}
        """));
        assertThat(tooFew.valid()).isFalse();
        assertThat(tooFew.errors().getFirst().message()).contains("Too few properties");

        var ok = schema.validate(Json.parse("""
            {"a": 1, "b": 2}
        """));
        assertThat(ok.valid()).isTrue();

        var tooMany = schema.validate(Json.parse("""
            {"a":1, "b":2, "c":3, "d":4}
        """));
        assertThat(tooMany.valid()).isFalse();
        assertThat(tooMany.errors().getFirst().message()).contains("Too many properties");
    }

    @Test
    void objectKeywordsWithoutExplicitTypeAreTreatedAsObject() {
        String schemaJson = """
            {
              "properties": {"name": {"type": "string"}},
              "required": ["name"]
            }
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        var bad = schema.validate(Json.parse("{}"));
        assertThat(bad.valid()).isFalse();
        assertThat(bad.errors().getFirst().message()).contains("Missing required property: name");

        var ok = schema.validate(Json.parse("""
            {"name":"x"}
        """));
        assertThat(ok.valid()).isTrue();
    }

    @Test
    void testRequiredAndProperties() {
        /// Test required / properties validation
        String schemaJson = """
            {
                "type": "object",
                "properties": { "a": { "type": "integer" }, "b": { "type": "string" } },
                "required": ["a"]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: {"a":1}, {"a":1,"b":"x"}
        assertThat(schema.validate(Json.parse("{\"a\":1}")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("{\"a\":1,\"b\":\"x\"}")).valid()).isTrue();
        
        // Invalid: {} (missing a), {"a":"1"} (type error at .a)
        var missingA = schema.validate(Json.parse("{}"));
        assertThat(missingA.valid()).isFalse();
        assertThat(missingA.errors().getFirst().message()).contains("Missing required property: a");
        
        var wrongType = schema.validate(Json.parse("{\"a\":\"1\"}"));
        assertThat(wrongType.valid()).isFalse();
        assertThat(wrongType.errors().getFirst().path()).isEqualTo("a");
        assertThat(wrongType.errors().getFirst().message()).contains("Expected number");
    }

    @Test
    void testAdditionalPropertiesFalse() {
        /// Test additionalProperties = false blocks unknown keys
        String schemaJson = """
            {
                "properties": {"a": {"type": "integer"}},
                "additionalProperties": false
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: {"a":1}
        assertThat(schema.validate(Json.parse("{\"a\":1}")).valid()).isTrue();
        
        // Invalid: {"a":1,"z":0} ("Additional properties not allowed" at .z)
        var invalid = schema.validate(Json.parse("{\"a\":1,\"z\":0}"));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors().getFirst().path()).isEqualTo("z");
        assertThat(invalid.errors().getFirst().message()).contains("Additional properties not allowed");
    }

    @Test
    void testAdditionalPropertiesTrue() {
        /// Test additionalProperties = true allows unknown keys
        String schemaJson = """
            {
                "properties": {"a": {"type": "integer"}},
                "additionalProperties": true
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: {"a":1,"z":{}}
        assertThat(schema.validate(Json.parse("{\"a\":1,\"z\":{}}")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("{\"a\":1,\"z\":\"anything\"}")).valid()).isTrue();
    }

    @Test
    void testAdditionalPropertiesSchema() {
        /// Test additionalProperties schema applies to unknown keys
        String schemaJson = """
            {
                "properties": {"a": {"type": "integer"}},
                "additionalProperties": {"type": "number"}
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: {"a":1,"z":2}
        assertThat(schema.validate(Json.parse("{\"a\":1,\"z\":2}")).valid()).isTrue();
        
        // Invalid: {"a":1,"z":"no"} (error at .z)
        var invalid = schema.validate(Json.parse("{\"a\":1,\"z\":\"no\"}"));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors().getFirst().path()).isEqualTo("z");
        assertThat(invalid.errors().getFirst().message()).contains("Expected number");
    }

    @Test
    void testPatternProperties() {
        /// Test patternProperties with unanchored find semantics
        String schemaJson = """
            {
                "patternProperties": { 
                    "^[a-z]+$": { "type": "integer" }, 
                    "Id": { "type": "string" } 
                }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: {"foo":1,"clientId":"abc"}
        assertThat(schema.validate(Json.parse("{\"foo\":1,\"clientId\":\"abc\"}")).valid()).isTrue();
        
        // Invalid: {"foo":"1"} (type at .foo)
        var invalidFoo = schema.validate(Json.parse("{\"foo\":\"1\"}"));
        assertThat(invalidFoo.valid()).isFalse();
        assertThat(invalidFoo.errors().getFirst().path()).isEqualTo("foo");
        assertThat(invalidFoo.errors().getFirst().message()).contains("Expected number");
        
        // Invalid: {"clientId":5} (type at .clientId)
        var invalidClientId = schema.validate(Json.parse("{\"clientId\":5}"));
        assertThat(invalidClientId.valid()).isFalse();
        assertThat(invalidClientId.errors().getFirst().path()).isEqualTo("clientId");
        assertThat(invalidClientId.errors().getFirst().message()).contains("Expected string");
    }

    @Test
    void testPropertiesVsPatternPropertiesPrecedence() {
        /// Test properties and patternProperties interaction - both apply when property name matches both
        String schemaJson = """
            {
                "properties": { "userId": { "type": "integer" } },
                "patternProperties": { "Id$": { "type": "string" } }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Both properties and patternProperties apply to userId, so both must pass
        // {"userId":7} fails because 7 is not a string (fails patternProperties)
        var numericResult = schema.validate(Json.parse("{\"userId\":7}"));
        assertThat(numericResult.valid()).isFalse();
        assertThat(numericResult.errors().getFirst().path()).isEqualTo("userId");
        assertThat(numericResult.errors().getFirst().message()).contains("Expected string");
        
        // {"userId":"7"} fails because "7" is a string, not an integer
        // (fails properties validation even though it passes patternProperties)
        var stringResult = schema.validate(Json.parse("{\"userId\":\"7\"}"));
        assertThat(stringResult.valid()).isFalse();
        assertThat(stringResult.errors().getFirst().path()).isEqualTo("userId");
        assertThat(stringResult.errors().getFirst().message()).contains("Expected number");
        
        // Valid: {"orderId":"x"} (pattern kicks in, no properties match)
        assertThat(schema.validate(Json.parse("{\"orderId\":\"x\"}")).valid()).isTrue();
        
        // Invalid: {"userId":"x"} (invalid under properties at .userId - "x" is not an integer)
        var invalid = schema.validate(Json.parse("{\"userId\":\"x\"}"));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors().getFirst().path()).isEqualTo("userId");
        assertThat(invalid.errors().getFirst().message()).contains("Expected number");
    }

    @Test
    void testPropertyNames() {
        /// Test propertyNames validation
        String schemaJson = """
            {
                "propertyNames": { "pattern": "^[A-Z][A-Za-z0-9_]*$" }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: {"Foo":1,"Bar_2":2}
        assertThat(schema.validate(Json.parse("{\"Foo\":1,\"Bar_2\":2}")).valid()).isTrue();
        
        // Invalid: {"foo":1} (error at .foo for property name schema)
        var invalid = schema.validate(Json.parse("{\"foo\":1}"));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors().getFirst().path()).isEqualTo("foo");
        assertThat(invalid.errors().getFirst().message()).contains("Property name violates propertyNames");
    }

    @Test
    void testMinPropertiesMaxProperties() {
        /// Test minProperties / maxProperties constraints
        String schemaJson = """
            { "minProperties": 1, "maxProperties": 2 }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: {"a":1}, {"a":1,"b":2}
        assertThat(schema.validate(Json.parse("{\"a\":1}")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("{\"a\":1,\"b\":2}")).valid()).isTrue();
        
        // Invalid: {} (too few)
        var tooFew = schema.validate(Json.parse("{}"));
        assertThat(tooFew.valid()).isFalse();
        assertThat(tooFew.errors().getFirst().message()).contains("Too few properties");
        
        // Invalid: {"a":1,"b":2,"c":3} (too many)
        var tooMany = schema.validate(Json.parse("{\"a\":1,\"b\":2,\"c\":3}"));
        assertThat(tooMany.valid()).isFalse();
        assertThat(tooMany.errors().getFirst().message()).contains("Too many properties");
    }

    @Test
    void testBooleanSubschemasInProperties() {
        /// Test boolean sub-schemas in properties
        String schemaJson = """
            {
                "properties": { 
                    "deny": false, 
                    "ok": true 
                }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid: {"deny":0}
        var denyInvalid = schema.validate(Json.parse("{\"deny\":0}"));
        assertThat(denyInvalid.valid()).isFalse();
        assertThat(denyInvalid.errors().getFirst().path()).isEqualTo("deny");
        assertThat(denyInvalid.errors().getFirst().message()).contains("Schema should not match");
        
        // Valid: {"ok":123}
        assertThat(schema.validate(Json.parse("{\"ok\":123}")).valid()).isTrue();
    }

    @Test
    void testBooleanSubschemasInPatternProperties() {
        /// Test boolean sub-schemas in patternProperties
        String schemaJson = """
            {
                "patternProperties": { "^x": false }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid: {"xray":1}
        var invalid = schema.validate(Json.parse("{\"xray\":1}"));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors().getFirst().path()).isEqualTo("xray");
        assertThat(invalid.errors().getFirst().message()).contains("Schema should not match");
    }

    @Test
    void testComplexObjectValidation() {
        /// Test complex combination of all object keywords
        String schemaJson = """
            {
                "type": "object",
                "properties": {
                    "id": { "type": "integer" },
                    "name": { "type": "string" }
                },
                "required": ["id"],
                "patternProperties": {
                    "^meta_": { "type": "string" }
                },
                "additionalProperties": { "type": "number" },
                "propertyNames": { "pattern": "^[a-zA-Z_][a-zA-Z0-9_]*$" },
                "minProperties": 2,
                "maxProperties": 5
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: complex object meeting all constraints
        var valid = schema.validate(Json.parse("""
            {
                "id": 123,
                "name": "test",
                "meta_type": "user",
                "score": 95.5
            }
            """));
        assertThat(valid.valid()).isTrue();
        
        // Invalid: missing required property
        var missingRequired = schema.validate(Json.parse("{\"name\":\"test\"}"));
        assertThat(missingRequired.valid()).isFalse();
        // Could be either "Missing required property: id" or "Too few properties: expected at least 2"
        // Both are valid error messages for this case
        var errorMessage = missingRequired.errors().getFirst().message();
        assertThat(errorMessage).satisfiesAnyOf(
            msg -> assertThat(msg).contains("id"),
            msg -> assertThat(msg).contains("Too few properties")
        );
        
        // Invalid: pattern property with wrong type
        var patternWrongType = schema.validate(Json.parse("""
            {"id":123,"meta_type":456}
            """));
        assertThat(patternWrongType.valid()).isFalse();
        assertThat(patternWrongType.errors().getFirst().path()).isEqualTo("meta_type");
        
        // Invalid: additional property with wrong type
        var additionalWrongType = schema.validate(Json.parse("""
            {"id":123,"extra":"not a number"}
            """));
        assertThat(additionalWrongType.valid()).isFalse();
        assertThat(additionalWrongType.errors().getFirst().path()).isEqualTo("extra");
        
        // Invalid: invalid property name
        var invalidName = schema.validate(Json.parse("""
            {"id":123,"123invalid":456}
            """));
        assertThat(invalidName.valid()).isFalse();
        assertThat(invalidName.errors().getFirst().path()).isEqualTo("123invalid");
        assertThat(invalidName.errors().getFirst().message()).contains("Property name violates propertyNames");
    }
}
