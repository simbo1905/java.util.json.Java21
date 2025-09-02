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
        assertThat(result.errors().get(0).path()).isEqualTo("extra");
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
        assertThat(bad.errors().get(0).path()).isEqualTo("extra");
        assertThat(bad.errors().get(0).message()).contains("Expected string");

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
        assertThat(tooFew.errors().get(0).message()).contains("Too few properties");

        var ok = schema.validate(Json.parse("""
            {"a": 1, "b": 2}
        """));
        assertThat(ok.valid()).isTrue();

        var tooMany = schema.validate(Json.parse("""
            {"a":1, "b":2, "c":3, "d":4}
        """));
        assertThat(tooMany.valid()).isFalse();
        assertThat(tooMany.errors().get(0).message()).contains("Too many properties");
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
        assertThat(bad.errors().get(0).message()).contains("Missing required property: name");

        var ok = schema.validate(Json.parse("""
            {"name":"x"}
        """));
        assertThat(ok.valid()).isTrue();
    }
}
