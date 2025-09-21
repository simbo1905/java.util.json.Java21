package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaPatternParamTest extends JsonSchemaTestBase {

    static Stream<Arguments> providePatterns() { return JsonSamples.patterns(); }

    @ParameterizedTest(name = "pattern={0} value={1} -> {2}")
    @MethodSource("providePatterns")
    void validatesPatterns(String pattern, String json, boolean expected) {
        String schema = "{\"type\":\"string\",\"pattern\":\"" + pattern.replace("\\", "\\\\") + "\"}";
        var compiled = JsonSchema.compile(Json.parse(schema));
        var result = compiled.validate(Json.parse(json));
        assertThat(result.valid()).isEqualTo(expected);
    }
}

