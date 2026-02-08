package json.java21.jtd.codegen;

import jdk.sandbox.java.util.json.Json;
import json.java21.jtd.JtdValidationError;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Cross-validates the interpreter and codegen paths produce identical
/// RFC 8927 `(instancePath, schemaPath)` error sets for the same
/// schema/instance pairs.
///
/// Every case from [json.java21.jtd.JtdValidatorTest] is replicated here
/// plus additional cases from the RFC 8927 spec worked examples.
class CrossValidationTest extends CodegenTestBase {

  record Case(String name, String schema, String instance) {}

  static Stream<Arguments> cases() {
    return Stream.of(
        // -- Empty --
        args("empty accepts null", "{}", "null"),
        args("empty accepts number", "{}", "42"),
        args("empty accepts string", "{}", "\"hi\""),
        args("empty accepts array", "{}", "[1,2]"),
        args("empty accepts object", "{}", "{\"a\":1}"),

        // -- Type: boolean --
        args("boolean valid true", "{\"type\":\"boolean\"}", "true"),
        args("boolean valid false", "{\"type\":\"boolean\"}", "false"),
        args("boolean rejects number", "{\"type\":\"boolean\"}", "42"),
        args("boolean rejects string", "{\"type\":\"boolean\"}", "\"hi\""),

        // -- Type: string --
        args("string valid", "{\"type\":\"string\"}", "\"hello\""),
        args("string rejects number", "{\"type\":\"string\"}", "42"),
        args("string rejects bool", "{\"type\":\"string\"}", "true"),

        // -- Type: float --
        args("float64 valid int", "{\"type\":\"float64\"}", "42"),
        args("float64 valid decimal", "{\"type\":\"float64\"}", "3.14"),
        args("float32 rejects string", "{\"type\":\"float32\"}", "\"hi\""),

        // -- Type: uint8 --
        args("uint8 valid 0", "{\"type\":\"uint8\"}", "0"),
        args("uint8 valid 255", "{\"type\":\"uint8\"}", "255"),
        args("uint8 valid 3.0", "{\"type\":\"uint8\"}", "3.0"),
        args("uint8 rejects 256", "{\"type\":\"uint8\"}", "256"),
        args("uint8 rejects -1", "{\"type\":\"uint8\"}", "-1"),
        args("uint8 rejects 3.5", "{\"type\":\"uint8\"}", "3.5"),
        args("uint8 rejects string", "{\"type\":\"uint8\"}", "\"hi\""),

        // -- Type: int8 --
        args("int8 valid -128", "{\"type\":\"int8\"}", "-128"),
        args("int8 valid 127", "{\"type\":\"int8\"}", "127"),
        args("int8 rejects 128", "{\"type\":\"int8\"}", "128"),

        // -- Type: int32 --
        args("int32 valid max", "{\"type\":\"int32\"}", "2147483647"),
        args("int32 valid min", "{\"type\":\"int32\"}", "-2147483648"),
        args("int32 rejects overflow", "{\"type\":\"int32\"}", "2147483648"),
        args("int32 rejects decimal", "{\"type\":\"int32\"}", "3.14"),

        // -- Type: uint32 --
        args("uint32 valid max", "{\"type\":\"uint32\"}", "4294967295"),
        args("uint32 rejects overflow", "{\"type\":\"uint32\"}", "4294967296"),

        // -- Type: timestamp --
        args("timestamp valid UTC", "{\"type\":\"timestamp\"}", "\"1990-12-31T23:59:59Z\""),
        args("timestamp valid offset", "{\"type\":\"timestamp\"}", "\"2024-01-15T10:30:00+05:00\""),
        args("timestamp valid leap second", "{\"type\":\"timestamp\"}", "\"1990-12-31T23:59:60Z\""),
        args("timestamp rejects bad", "{\"type\":\"timestamp\"}", "\"not-a-date\""),
        args("timestamp rejects number", "{\"type\":\"timestamp\"}", "42"),

        // -- Enum --
        args("enum valid", "{\"enum\":[\"a\",\"b\",\"c\"]}", "\"a\""),
        args("enum rejects unknown", "{\"enum\":[\"a\",\"b\"]}", "\"c\""),
        args("enum rejects number", "{\"enum\":[\"a\"]}", "42"),

        // -- Nullable --
        args("nullable string accepts null", "{\"type\":\"string\",\"nullable\":true}", "null"),
        args("nullable string accepts string", "{\"type\":\"string\",\"nullable\":true}", "\"hi\""),
        args("nullable string rejects number", "{\"type\":\"string\",\"nullable\":true}", "42"),

        // -- Elements --
        args("elements valid", "{\"elements\":{\"type\":\"string\"}}", "[\"a\",\"b\"]"),
        args("elements empty", "{\"elements\":{\"type\":\"string\"}}", "[]"),
        args("elements rejects non-array", "{\"elements\":{\"type\":\"string\"}}", "42"),
        args("elements child errors", "{\"elements\":{\"type\":\"string\"}}", "[\"ok\",42,\"fine\",true]"),

        // -- Properties --
        args("props valid",
            "{\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"uint8\"}}}",
            "{\"name\":\"Alice\",\"age\":30}"),
        args("props missing required",
            "{\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"uint8\"}}}",
            "{\"name\":\"Alice\"}"),
        args("props additional rejected",
            "{\"properties\":{\"name\":{\"type\":\"string\"}}}",
            "{\"name\":\"Alice\",\"extra\":true}"),
        args("props child error",
            "{\"properties\":{\"age\":{\"type\":\"uint8\"}}}",
            "{\"age\":\"not a number\"}"),
        args("props rejects non-object",
            "{\"properties\":{\"x\":{\"type\":\"string\"}}}",
            "42"),

        // -- Optional properties --
        args("optional-only rejects non-object",
            "{\"optionalProperties\":{\"email\":{\"type\":\"string\"}}}",
            "42"),
        args("optional absent ok",
            "{\"optionalProperties\":{\"email\":{\"type\":\"string\"}}}",
            "{}"),
        args("optional present valid",
            "{\"optionalProperties\":{\"email\":{\"type\":\"string\"}}}",
            "{\"email\":\"a@b\"}"),
        args("optional present invalid",
            "{\"optionalProperties\":{\"email\":{\"type\":\"string\"}}}",
            "{\"email\":42}"),

        // -- Values --
        args("values valid", "{\"values\":{\"type\":\"string\"}}", "{\"a\":\"x\",\"b\":\"y\"}"),
        args("values rejects non-object", "{\"values\":{\"type\":\"string\"}}", "42"),
        args("values child error", "{\"values\":{\"type\":\"string\"}}", "{\"a\":\"ok\",\"b\":42}"),

        // -- Discriminator --
        args("disc valid",
            "{\"discriminator\":\"kind\",\"mapping\":{\"a\":{\"properties\":{\"x\":{\"type\":\"string\"}}}}}",
            "{\"kind\":\"a\",\"x\":\"ok\"}"),
        args("disc not object",
            "{\"discriminator\":\"kind\",\"mapping\":{\"a\":{\"properties\":{\"x\":{\"type\":\"string\"}}}}}",
            "42"),
        args("disc missing tag",
            "{\"discriminator\":\"kind\",\"mapping\":{\"a\":{\"properties\":{\"x\":{\"type\":\"string\"}}}}}",
            "{\"x\":1}"),
        args("disc tag not string",
            "{\"discriminator\":\"kind\",\"mapping\":{\"a\":{\"properties\":{\"x\":{\"type\":\"string\"}}}}}",
            "{\"kind\":42}"),
        args("disc tag not in mapping",
            "{\"discriminator\":\"kind\",\"mapping\":{\"a\":{\"properties\":{\"x\":{\"type\":\"string\"}}}}}",
            "{\"kind\":\"unknown\"}"),
        args("disc variant error",
            "{\"discriminator\":\"kind\",\"mapping\":{\"a\":{\"properties\":{\"x\":{\"type\":\"string\"}}}}}",
            "{\"kind\":\"a\",\"x\":42}"),

        // -- Ref --
        args("ref valid",
            "{\"definitions\":{\"addr\":{\"type\":\"string\"}},\"ref\":\"addr\"}",
            "\"hello\""),
        args("ref invalid",
            "{\"definitions\":{\"addr\":{\"type\":\"string\"}},\"ref\":\"addr\"}",
            "42"),

        // -- Nested: elements of properties --
        args("elements of properties valid",
            "{\"elements\":{\"properties\":{\"n\":{\"type\":\"string\"}}}}",
            "[{\"n\":\"ok\"}]"),
        args("elements of properties invalid child",
            "{\"elements\":{\"properties\":{\"n\":{\"type\":\"string\"}}}}",
            "[{\"n\":42}]"),
        args("elements of properties missing",
            "{\"elements\":{\"properties\":{\"n\":{\"type\":\"string\"}}}}",
            "[{}]"),

        // -- Nested: additional properties inside array elements --
        args("elements of properties additional rejected",
            "{\"elements\":{\"properties\":{\"n\":{\"type\":\"string\"}}}}",
            "[{\"n\":\"ok\",\"extra\":true}]"),

        // -- Nested: non-object inside elements of properties --
        args("elements of properties non-object",
            "{\"elements\":{\"properties\":{\"n\":{\"type\":\"string\"}}}}",
            "[42]"),

        // -- Nested: discriminator inside values --
        args("values of discriminator valid",
            "{\"values\":{\"discriminator\":\"kind\",\"mapping\":{\"a\":{\"properties\":{\"x\":{\"type\":\"string\"}}}}}}",
            "{\"k1\":{\"kind\":\"a\",\"x\":\"ok\"}}"),
        args("values of discriminator invalid",
            "{\"values\":{\"discriminator\":\"kind\",\"mapping\":{\"a\":{\"properties\":{\"x\":{\"type\":\"string\"}}}}}}",
            "{\"k1\":{\"kind\":\"a\",\"x\":42}}"),

        // -- Nested: elements of elements --
        args("elements of elements valid",
            "{\"elements\":{\"elements\":{\"type\":\"string\"}}}",
            "[[\"a\",\"b\"]]"),
        args("elements of elements invalid",
            "{\"elements\":{\"elements\":{\"type\":\"string\"}}}",
            "[[\"a\",42]]"),

        // -- Nested: values of values --
        args("values of values valid",
            "{\"values\":{\"values\":{\"type\":\"string\"}}}",
            "{\"a\":{\"b\":\"ok\"}}"),
        args("values of values invalid",
            "{\"values\":{\"values\":{\"type\":\"string\"}}}",
            "{\"a\":{\"b\":42}}"),

        // -- Discriminator with multi-variant mapping --
        args("disc multi-variant valid dog",
            "{\"discriminator\":\"kind\",\"mapping\":{\"dog\":{\"properties\":{\"breed\":{\"type\":\"string\"}}},\"cat\":{\"properties\":{\"indoor\":{\"type\":\"boolean\"}}}}}",
            "{\"kind\":\"dog\",\"breed\":\"poodle\"}"),
        args("disc multi-variant valid cat",
            "{\"discriminator\":\"kind\",\"mapping\":{\"dog\":{\"properties\":{\"breed\":{\"type\":\"string\"}}},\"cat\":{\"properties\":{\"indoor\":{\"type\":\"boolean\"}}}}}",
            "{\"kind\":\"cat\",\"indoor\":true}"),

        // -- Worked example from spec --
        args("worked example",
            "{\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"uint8\"},\"tags\":{\"elements\":{\"type\":\"string\"}}},\"optionalProperties\":{\"email\":{\"type\":\"string\"}}}",
            "{\"name\":\"Alice\",\"age\":300,\"tags\":[\"a\",42],\"extra\":true}")
    );
  }

  private static Arguments args(String name, String schema, String instance) {
    return Arguments.of(name, schema, instance);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void interpreterAndCodegenAgree(String name, String schemaJson, String instanceJson) {
    final var schema = Json.parse(schemaJson);
    final var instance = Json.parse(instanceJson);

    final var interpreter = json.java21.jtd.JtdValidator.compile(schema);
    final var codegen = JtdCodegen.compile(schema);

    final var interpResult = interpreter.validate(instance);
    final var codegenResult = codegen.validate(instance);

    assertThat(codegenResult.isValid())
        .as("isValid for: " + name)
        .isEqualTo(interpResult.isValid());

    final var interpErrors = sorted(interpResult.errors());
    final var codegenErrors = sorted(codegenResult.errors());

    assertThat(codegenErrors)
        .as("error set for: " + name)
        .containsExactlyElementsOf(interpErrors);
  }

  private static List<JtdValidationError> sorted(List<JtdValidationError> errors) {
    return errors.stream()
        .sorted(Comparator.comparing(JtdValidationError::instancePath)
            .thenComparing(JtdValidationError::schemaPath))
        .toList();
  }

  @org.junit.jupiter.api.Test
  void toStringReturnsOriginalSchemaJson() {
    LOG.info("CROSS-VALIDATE: toString");
    final var v = JtdCodegen.compile(Json.parse("{\"type\": \"string\"}"));
    assertThat(v.toString()).contains("type");
    assertThat(v.toString()).contains("string");
  }
}
