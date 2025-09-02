package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonSchemaNumberKeywordsTest extends JsonSchemaLoggingConfig {

    @Test
    void exclusiveMinimumAndMaximumAreHonored() {
        String schemaJson = """
            {
              "type": "number",
              "minimum": 0,
              "maximum": 10,
              "exclusiveMinimum": true,
              "exclusiveMaximum": true
            }
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // Boundary values should fail when exclusive
        assertThat(schema.validate(Json.parse("0")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("10")).valid()).isFalse();

        // Inside range should pass
        assertThat(schema.validate(Json.parse("5")).valid()).isTrue();
    }

    @Test
    void multipleOfForDecimals() {
        String schemaJson = """
            {"type":"number", "multipleOf": 0.1}
            """;
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        assertThat(schema.validate(Json.parse("0.3")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("0.25")).valid()).isFalse();
    }
}

