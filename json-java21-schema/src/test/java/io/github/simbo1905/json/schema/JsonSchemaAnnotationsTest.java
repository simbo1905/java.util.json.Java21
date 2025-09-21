package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/// Covers annotation-only keywords from JSON Schema such as
/// title, description, $comment, and examples. These MUST NOT
/// affect validation (they are informational).
class JsonSchemaAnnotationsTest extends JsonSchemaTestBase {

    @Test
    void examplesDoNotAffectValidation() {
        String schemaJson = """
            {
              "type": "object",
              "title": "User",
              "description": "A simple user object",
              "$comment": "Examples are informational only",
              "examples": [
                {"id": 1, "name": "Alice"},
                {"id": 2, "name": "Bob"}
              ],
              "properties": {
                "id":   {"type": "integer", "minimum": 0},
                "name": {"type": "string", "minLength": 1}
              },
              "required": ["id", "name"]
            }
            """;

        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // Valid instance should pass regardless of examples
        var ok = schema.validate(Json.parse("""
            {"id": 10, "name": "Jane"}
        """));
        assertThat(ok.valid()).isTrue();

        // Invalid instance should still fail regardless of examples
        var bad = schema.validate(Json.parse("""
            {"id": -1}
        """));
        assertThat(bad.valid()).isFalse();
        assertThat(bad.errors()).isNotEmpty();
        assertThat(bad.errors().get(0).message())
            .satisfiesAnyOf(
                m -> assertThat(m).contains("Missing required property: name"),
                m -> assertThat(m).contains("Below minimum")
            );
    }

    @Test
    void unknownAnnotationKeywordsAreIgnored() {
        String schemaJson = """
            {
              "type": "string",
              "description": "A labeled string",
              "title": "Label",
              "$comment": "Arbitrary annotations should be ignored by validation",
              "x-internal": true
            }
            """;

        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        assertThat(schema.validate(Json.parse("\"hello\""))).isNotNull();
        assertThat(schema.validate(Json.parse("\"hello\""))).extracting("valid").isEqualTo(true);
        assertThat(schema.validate(Json.parse("123"))).extracting("valid").isEqualTo(false);
    }
}
