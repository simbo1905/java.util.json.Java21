package io.github.simbo1905.json.schema;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

final class JsonSamples {
    private JsonSamples() {}

    static Stream<Arguments> simpleTypes() {
        return Stream.of(
                Arguments.of("string", "\"hello\"", true),
                Arguments.of("string", "42", false),
                Arguments.of("number", "42", true),
                Arguments.of("number", "3.14", true),
                Arguments.of("number", "\"x\"", false),
                Arguments.of("boolean", "true", true),
                Arguments.of("boolean", "false", true),
                Arguments.of("boolean", "1", false),
                Arguments.of("null", "null", true),
                Arguments.of("null", "\"null\"", false)
        );
    }

    static Stream<Arguments> patterns() {
        return Stream.of(
                Arguments.of("^abc$", "\"abc\"", true),
                Arguments.of("^abc$", "\"ab\"", false),
                Arguments.of("^[0-9]+$", "\"123\"", true),
                Arguments.of("^[0-9]+$", "\"12a\"", false)
        );
    }
}

