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

    @Test
    void testExclusiveMinimum_numericForm() {
        // Test numeric exclusiveMinimum (2020-12 spec)
        String schemaJson = """
            {
                "type": "number",
                "exclusiveMinimum": 0
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid - exactly at boundary
        assertThat(schema.validate(Json.parse("0")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("0.0")).valid()).isFalse();
        
        // Valid - above boundary
        assertThat(schema.validate(Json.parse("0.0001")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("1")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("100")).valid()).isTrue();
        
        // Invalid - below boundary
        assertThat(schema.validate(Json.parse("-1")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("-0.1")).valid()).isFalse();
    }

    @Test
    void testExclusiveMaximum_numericForm() {
        // Test numeric exclusiveMaximum (2020-12 spec)
        String schemaJson = """
            {
                "type": "number",
                "exclusiveMaximum": 10
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid - exactly at boundary
        assertThat(schema.validate(Json.parse("10")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("10.0")).valid()).isFalse();
        
        // Valid - below boundary
        assertThat(schema.validate(Json.parse("9.9999")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("9")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("0")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("-10")).valid()).isTrue();
        
        // Invalid - above boundary
        assertThat(schema.validate(Json.parse("10.1")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("11")).valid()).isFalse();
    }

    @Test
    void testExclusiveMinMax_numericForm_combined() {
        // Test both numeric exclusive bounds
        String schemaJson = """
            {
                "type": "number",
                "exclusiveMinimum": 0,
                "exclusiveMaximum": 100
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid - at lower boundary
        assertThat(schema.validate(Json.parse("0")).valid()).isFalse();
        
        // Invalid - at upper boundary
        assertThat(schema.validate(Json.parse("100")).valid()).isFalse();
        
        // Valid - within exclusive bounds
        assertThat(schema.validate(Json.parse("0.0001")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("50")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("99.9999")).valid()).isTrue();
        
        // Invalid - outside bounds
        assertThat(schema.validate(Json.parse("-1")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("100.0001")).valid()).isFalse();
    }

    @Test
    void testExclusiveMinimum_booleanForm_stillWorks() {
        // Test that boolean exclusiveMinimum still works (backwards compatibility)
        String schemaJson = """
            {
                "type": "number",
                "minimum": 0,
                "exclusiveMinimum": true
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid - exactly at boundary
        assertThat(schema.validate(Json.parse("0")).valid()).isFalse();
        
        // Valid - above boundary
        assertThat(schema.validate(Json.parse("0.0001")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("1")).valid()).isTrue();
        
        // Invalid - below boundary
        assertThat(schema.validate(Json.parse("-1")).valid()).isFalse();
    }

    @Test
    void testExclusiveMaximum_booleanForm_stillWorks() {
        // Test that boolean exclusiveMaximum still works (backwards compatibility)
        String schemaJson = """
            {
                "type": "number",
                "maximum": 10,
                "exclusiveMaximum": true
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid - exactly at boundary
        assertThat(schema.validate(Json.parse("10")).valid()).isFalse();
        
        // Valid - below boundary
        assertThat(schema.validate(Json.parse("9.9999")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("9")).valid()).isTrue();
        
        // Invalid - above boundary
        assertThat(schema.validate(Json.parse("10.1")).valid()).isFalse();
    }

    @Test
    void testExclusiveMinMax_mixedForms() {
        // Test mixing numeric and boolean forms
        String schemaJson = """
            {
                "type": "number",
                "minimum": 0,
                "exclusiveMinimum": true,
                "exclusiveMaximum": 100
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid - at lower boundary (boolean exclusive)
        assertThat(schema.validate(Json.parse("0")).valid()).isFalse();
        
        // Invalid - at upper boundary (numeric exclusive)
        assertThat(schema.validate(Json.parse("100")).valid()).isFalse();
        
        // Valid - within bounds
        assertThat(schema.validate(Json.parse("0.0001")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("99.9999")).valid()).isTrue();
    }

    @Test
    void testIntegerType_treatedAsNumber() {
        // Test that integer type is treated as number (current behavior)
        String schemaJson = """
            {
                "type": "integer",
                "minimum": 0,
                "maximum": 100
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - integers within range
        assertThat(schema.validate(Json.parse("0")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("50")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("100")).valid()).isTrue();
        
        // Invalid - integers outside range
        assertThat(schema.validate(Json.parse("-1")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("101")).valid()).isFalse();
        
        // Valid - floats should be accepted (treated as number)
        assertThat(schema.validate(Json.parse("50.5")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("3.14")).valid()).isTrue();
    }
}

