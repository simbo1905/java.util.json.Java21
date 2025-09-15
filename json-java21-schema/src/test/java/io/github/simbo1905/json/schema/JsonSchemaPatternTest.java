package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonSchemaPatternTest extends JsonSchemaLoggingConfig {

    @Test
    void testPattern_unanchored_contains() {
        // Test that pattern uses unanchored matching (find() not matches())
        String schemaJson = """
            {
                "type": "string",
                "pattern": "[A-Z]{3}"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - contains the pattern as substring
        assertThat(schema.validate(Json.parse("\"ABC\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"xxABCxx\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"startABCend\"")).valid()).isTrue();
        
        // Invalid - no match found
        assertThat(schema.validate(Json.parse("\"ab\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"123\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"abc\"")).valid()).isFalse();
    }

    @Test
    void testPattern_anchored_stillWorks() {
        // Test that anchored patterns still work when explicitly anchored
        String schemaJson = """
            {
                "type": "string",
                "pattern": "^[A-Z]{3}$"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - exact match
        assertThat(schema.validate(Json.parse("\"ABC\"")).valid()).isTrue();
        
        // Invalid - contains but not exact match
        assertThat(schema.validate(Json.parse("\"xxABCxx\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"startABCend\"")).valid()).isFalse();
        
        // Invalid - wrong case
        assertThat(schema.validate(Json.parse("\"abc\"")).valid()).isFalse();
    }

    @Test
    void testPattern_complexRegex() {
        // Test more complex pattern matching
        String schemaJson = """
            {
                "type": "string",
                "pattern": "\\\\d{3}-\\\\d{3}-\\\\d{4}"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - contains phone number pattern
        assertThat(schema.validate(Json.parse("\"123-456-7890\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"Call me at 123-456-7890 please\"")).valid()).isTrue();
        
        // Invalid - wrong format
        assertThat(schema.validate(Json.parse("\"1234567890\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"123-45-6789\"")).valid()).isFalse();
    }

    @Test
    void testPattern_withOtherConstraints() {
        // Test pattern combined with other string constraints
        String schemaJson = """
            {
                "type": "string",
                "pattern": "[A-Z]+",
                "minLength": 3,
                "maxLength": 10
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - meets all constraints
        assertThat(schema.validate(Json.parse("\"HELLO\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"ABC WORLD\"")).valid()).isTrue();
        
        // Invalid - pattern matches but too short
        assertThat(schema.validate(Json.parse("\"AB\"")).valid()).isFalse();
        
        // Invalid - pattern matches but too long
        assertThat(schema.validate(Json.parse("\"ABCDEFGHIJKLMNOP\"")).valid()).isFalse();
        
        // Invalid - length OK but no pattern match
        assertThat(schema.validate(Json.parse("\"hello\"")).valid()).isFalse();
    }

    @Test
    void testPattern_emptyString() {
        String schemaJson = """
        {
            "type": "string",
            "pattern": "a+"
        }
        """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Invalid - empty string doesn't match a+ (needs at least one 'a')
        assertThat(schema.validate(Json.parse("\"\"")).valid()).isFalse();
        
        // Valid - contains 'a'
        assertThat(schema.validate(Json.parse("\"banana\"")).valid()).isTrue();
        
        // Invalid - no 'a'
        assertThat(schema.validate(Json.parse("\"bbb\"")).valid()).isFalse();
    }
}