package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaTypeAndEnumParamTest extends JsonSchemaTestBase {

    static Stream<Arguments> provideSimpleTypes() { return JsonSamples.simpleTypes(); }

    @ParameterizedTest(name = "type={0} value={1} -> {2}")
    @MethodSource("provideSimpleTypes")
    void validatesSimpleTypes(String type, String json, boolean expected) {
        String schema = "{\"type\":\"" + type + "\"}";
        var compiled = JsonSchema.compile(Json.parse(schema));
        var result = compiled.validate(Json.parse(json));
        assertThat(result.valid()).isEqualTo(expected);
    }
}

