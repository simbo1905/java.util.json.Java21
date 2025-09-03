package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonSchemaErrorMessagesTest extends JsonSchemaLoggingConfig {

    @Test
    void typeMismatchMessages() {
        JsonSchema sString = JsonSchema.compile(Json.parse("""
            {"type":"string"}
        """));
        var r1 = sString.validate(Json.parse("123"));
        assertThat(r1.valid()).isFalse();
        assertThat(r1.errors().getFirst().message()).contains("Expected string");

        JsonSchema sArray = JsonSchema.compile(Json.parse("""
            {"type":"array"}
        """));
        var r2 = sArray.validate(Json.parse("{}"));
        assertThat(r2.valid()).isFalse();
        assertThat(r2.errors().getFirst().message()).contains("Expected array");

        JsonSchema sObject = JsonSchema.compile(Json.parse("""
            {"type":"object"}
        """));
        var r3 = sObject.validate(Json.parse("[]"));
        assertThat(r3.valid()).isFalse();
        assertThat(r3.errors().getFirst().message()).contains("Expected object");
    }

    @Test
    void numericConstraintMessages() {
        String schemaJson = """
            {"type":"number","minimum":1,"maximum":2,"multipleOf": 2}
            """;
        JsonSchema s = JsonSchema.compile(Json.parse(schemaJson));

        var below = s.validate(Json.parse("0"));
        assertThat(below.valid()).isFalse();
        assertThat(below.errors().getFirst().message()).contains("Below minimum");

        var above = s.validate(Json.parse("3"));
        assertThat(above.valid()).isFalse();
        assertThat(above.errors().getFirst().message()).contains("Above maximum");

        var notMultiple = s.validate(Json.parse("1"));
        assertThat(notMultiple.valid()).isFalse();
        assertThat(notMultiple.errors().getFirst().message()).contains("Not multiple of");
    }

    @Test
    void arrayIndexAppearsInPath() {
        String schemaJson = """
            {"type":"array","items":{"type":"integer"}}
            """;
        JsonSchema s = JsonSchema.compile(Json.parse(schemaJson));

        var bad = s.validate(Json.parse("""
            [1, "two", 3]
        """));
        assertThat(bad.valid()).isFalse();
        // Expect failing path to point to the non-integer element
        assertThat(bad.errors().getFirst().path()).isEqualTo("[1]");
        assertThat(bad.errors().getFirst().message()).contains("Expected number");
    }

    @Test
    void patternAndEnumMessages() {
        String schemaJson = """
            {"type":"string","pattern":"^x+$","enum":["x","xx","xxx"]}
            """;
        JsonSchema s = JsonSchema.compile(Json.parse(schemaJson));

        var badEnum = s.validate(Json.parse("\"xxxx\""));
        assertThat(badEnum.valid()).isFalse();
        assertThat(badEnum.errors().getFirst().message()).satisfiesAnyOf(
            m -> assertThat(m).contains("Not in enum"),
            m -> assertThat(m).contains("Pattern mismatch")
        );
    }
}
