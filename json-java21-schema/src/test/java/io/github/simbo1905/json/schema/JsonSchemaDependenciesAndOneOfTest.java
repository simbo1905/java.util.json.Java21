package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonSchemaDependenciesAndOneOfTest extends JsonSchemaLoggingConfig {

    @Test
    void testDependentRequiredBasics() {
        /// Test dependentRequired with creditCard requiring billingAddress
        String schemaJson = """
            {
              "type": "object",
              "dependentRequired": { "creditCard": ["billingAddress"] }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: both creditCard and billingAddress present
        var valid = schema.validate(Json.parse("""
            {"creditCard":"4111-...", "billingAddress":"X"}
        """));
        assertThat(valid.valid()).isTrue();
        
        // Invalid: creditCard present but billingAddress missing
        var invalid = schema.validate(Json.parse("""
            {"creditCard":"4111-..."}
        """));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors().getFirst().message()).contains("Property 'creditCard' requires property 'billingAddress' (dependentRequired)");
        
        // Valid: empty object (no trigger property)
        var empty = schema.validate(Json.parse("{}"));
        assertThat(empty.valid()).isTrue();
    }

    @Test
    void testMultipleDependentRequireds() {
        /// Test multiple dependentRequired triggers and requirements
        String schemaJson = """
            {
              "type": "object",
              "dependentRequired": {
                "a": ["b","c"],
                "x": ["y"]
              }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid: a present but missing c
        var missingC = schema.validate(Json.parse("{\"a\":1,\"b\":2}"));
        assertThat(missingC.valid()).isFalse();
        assertThat(missingC.errors().getFirst().message()).contains("Property 'a' requires property 'c' (dependentRequired)");
        
        // Invalid: a present but missing b and c (should get two errors)
        var missingBoth = schema.validate(Json.parse("{\"a\":1}"));
        assertThat(missingBoth.valid()).isFalse();
        assertThat(missingBoth.errors()).hasSize(2);
        
        // Valid: x present with y
        var validXY = schema.validate(Json.parse("{\"x\":1,\"y\":2}"));
        assertThat(validXY.valid()).isTrue();
    }

    @Test
    void testDependentSchemasFalse() {
        /// Test dependentSchemas with false schema (forbids object)
        String schemaJson = """
            {
              "type": "object",
              "dependentSchemas": { "debug": false }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: empty object
        var empty = schema.validate(Json.parse("{}"));
        assertThat(empty.valid()).isTrue();
        
        // Invalid: debug property present triggers false schema
        var invalid = schema.validate(Json.parse("{\"debug\": true}"));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors().getFirst().message()).contains("Property 'debug' forbids object unless its dependent schema is satisfied (dependentSchemas=false)");
    }

    @Test
    void testDependentSchemasWithSchema() {
        /// Test dependentSchemas with actual schema validation
        String schemaJson = """
            {
              "type": "object",
              "dependentSchemas": {
                "country": { 
                  "properties": { 
                    "postalCode": { "type":"string", "pattern":"^\\\\d{5}$" } 
                  }, 
                  "required": ["postalCode"] 
                }
              }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: country present with valid postalCode
        var valid = schema.validate(Json.parse("{\"country\":\"US\",\"postalCode\":\"12345\"}"));
        assertThat(valid.valid()).isTrue();
        
        // Invalid: country present but missing postalCode
        var missingPostal = schema.validate(Json.parse("{\"country\":\"US\"}"));
        assertThat(missingPostal.valid()).isFalse();
        assertThat(missingPostal.errors().getFirst().message()).contains("Missing required property: postalCode");
        
        // Invalid: country present with invalid postalCode pattern
        var invalidPattern = schema.validate(Json.parse("{\"country\":\"US\",\"postalCode\":\"ABCDE\"}"));
        assertThat(invalidPattern.valid()).isFalse();
        assertThat(invalidPattern.errors().getFirst().path()).isEqualTo("postalCode");
    }

    @Test
    void testDependenciesWithObjectKeywords() {
        /// Test interaction between dependencies and existing object keywords
        String schemaJson = """
            {
              "properties": { 
                "a": { "type":"integer" },
                "b": { "type":"string" }
              },
              "required": ["a"],
              "dependentRequired": { "a": ["b"] },
              "additionalProperties": false
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid: additionalProperties violation
        var extraProp = schema.validate(Json.parse("{\"a\":1,\"z\":0}"));
        assertThat(extraProp.valid()).isFalse();
        // Should have both additionalProperties and dependentRequired errors
        boolean foundAdditionalPropsError = false;
        for (var error : extraProp.errors()) {
            if (error.path().equals("z") && error.message().contains("Additional properties not allowed")) {
                foundAdditionalPropsError = true;
                break;
            }
        }
        assertThat(foundAdditionalPropsError).isTrue();
        
        // Invalid: missing b due to dependency
        var missingDep = schema.validate(Json.parse("{\"a\":1}"));
        assertThat(missingDep.valid()).isFalse();
        assertThat(missingDep.errors().getFirst().message()).contains("Property 'a' requires property 'b' (dependentRequired)");
        
        // Valid: a and b present, no extra properties
        var valid = schema.validate(Json.parse("{\"a\":1,\"b\":\"test\"}"));
        assertThat(valid.valid()).isTrue();
    }

    @Test
    void testOneOfExactOne() {
        /// Test oneOf with exact-one validation semantics
        String schemaJson = """
            {
              "oneOf": [
                { "type":"string", "minLength":2 },
                { "type":"integer", "minimum": 10 }
              ]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: string with minLength 2
        var validString = schema.validate(Json.parse("\"ok\""));
        assertThat(validString.valid()).isTrue();
        
        // Valid: integer with minimum 10
        var validInt = schema.validate(Json.parse("10"));
        assertThat(validInt.valid()).isTrue();
        
        // Invalid: integer below minimum (zero branches valid)
        var invalidInt = schema.validate(Json.parse("1"));
        assertThat(invalidInt.valid()).isFalse();
        assertThat(invalidInt.errors().getFirst().message()).contains("Below minimum");
        
        // Invalid: string too short (zero branches valid)
        var invalidString = schema.validate(Json.parse("\"x\""));
        assertThat(invalidString.valid()).isFalse();
        assertThat(invalidString.errors().getFirst().message()).contains("String too short");
    }

    @Test
    void testOneOfMultipleMatches() {
        /// Test oneOf error when multiple schemas match
        String schemaJson = """
            {
              "oneOf": [
                { "type":"string" },
                { "type":"string", "pattern":"^t.*" }
              ]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid: both string schemas match
        var multipleMatch = schema.validate(Json.parse("\"two\""));
        assertThat(multipleMatch.valid()).isFalse();
        assertThat(multipleMatch.errors().getFirst().message()).contains("oneOf: multiple schemas matched (2)");
    }

    @Test
    void testBooleanSubschemasInDependentSchemas() {
        /// Test boolean subschemas in dependentSchemas
        String schemaJson = """
            {
              "dependentSchemas": {
                "k1": true,
                "k2": false
              }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: k1 present with true schema (no additional constraint)
        var validTrue = schema.validate(Json.parse("{\"k1\": 1}"));
        assertThat(validTrue.valid()).isTrue();
        
        // Invalid: k2 present with false schema (forbids object)
        var invalidFalse = schema.validate(Json.parse("{\"k2\": 1}"));
        assertThat(invalidFalse.valid()).isFalse();
        assertThat(invalidFalse.errors().getFirst().message()).contains("Property 'k2' forbids object unless its dependent schema is satisfied (dependentSchemas=false)");
    }

    @Test
    void testComplexDependenciesAndOneOf() {
        /// Test complex combination of all new features
        String schemaJson = """
            {
              "type": "object",
              "properties": {
                "paymentMethod": { "enum": ["card", "bank"] },
                "accountNumber": { "type": "string" }
              },
              "required": ["paymentMethod"],
              "dependentRequired": {
                "accountNumber": ["routingNumber"]
              },
              "dependentSchemas": {
                "paymentMethod": {
                  "oneOf": [
                    {
                      "properties": { "paymentMethod": { "const": "card" } },
                      "required": ["cardNumber"]
                    },
                    {
                      "properties": { "paymentMethod": { "const": "bank" } },
                      "required": ["accountNumber", "routingNumber"]
                    }
                  ]
                }
              }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid: card payment with cardNumber
        var validCard = schema.validate(Json.parse("""
            {
              "paymentMethod": "card",
              "cardNumber": "1234-5678-9012-3456"
            }
        """));
        assertThat(validCard.valid()).isTrue();
        
        // Valid: bank payment with all required fields
        var validBank = schema.validate(Json.parse("""
            {
              "paymentMethod": "bank",
              "accountNumber": "123456789",
              "routingNumber": "123456789"
            }
        """));
        assertThat(validBank.valid()).isTrue();
        
        // Invalid: accountNumber present but missing routingNumber (dependentRequired)
        var missingRouting = schema.validate(Json.parse("""
            {
              "paymentMethod": "bank",
              "accountNumber": "123456789"
            }
        """));
        assertThat(missingRouting.valid()).isFalse();
        assertThat(missingRouting.errors().getFirst().message()).contains("Property 'accountNumber' requires property 'routingNumber' (dependentRequired)");
    }
}