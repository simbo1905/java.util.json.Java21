package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonSchemaCombinatorsTest extends JsonSchemaLoggingConfig {

    @Test
    void anyOfRequiresOneBranchValid() {
        String schemaJson = """
            {
              "anyOf": [
                {"type": "string", "minLength": 3},
                {"type": "number", "minimum": 10}
              ]
            }
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        assertThat(schema.validate(Json.parse("\"abc\""))).extracting("valid").isEqualTo(true);
        assertThat(schema.validate(Json.parse("12"))).extracting("valid").isEqualTo(true);

        var bad = schema.validate(Json.parse("\"x\""));
        assertThat(bad.valid()).isFalse();
        assertThat(bad.errors()).isNotEmpty();
    }

    @Test
    void notInvertsValidation() {
        String schemaJson = """
            { "not": {"type": "integer"} }
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        assertThat(schema.validate(Json.parse("\"ok\""))).extracting("valid").isEqualTo(true);
        assertThat(schema.validate(Json.parse("1"))).extracting("valid").isEqualTo(false);
    }

    @Test
    void unresolvedRefFailsCompilation() {
        String schemaJson = """
            {"$ref": "#/$defs/missing"}
            """;
        assertThatThrownBy(() -> JsonSchema.compile(Json.parse(schemaJson)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unresolved $ref");
    }

    @Test
    void nestedErrorPathsAreClear() {
        String schemaJson = """
            {
              "type": "object",
              "properties": {
                "user": {
                  "type": "object",
                  "properties": {"name": {"type": "string"}},
                  "required": ["name"]
                }
              }
            }
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        var bad = schema.validate(Json.parse("""
            {"user": {}}
        """));
        assertThat(bad.valid()).isFalse();
        assertThat(bad.errors().get(0).path()).isEqualTo("user");
        assertThat(bad.errors().get(0).message()).contains("Missing required property: name");
    }
}
