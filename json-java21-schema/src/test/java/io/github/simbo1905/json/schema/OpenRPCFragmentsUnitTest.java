package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import static io.github.simbo1905.json.schema.JsonSchema.LOG;
import static org.assertj.core.api.Assertions.assertThat;

/// Unit tests that exercise OpenRPC-like schema fragments using only
/// keywords currently supported by the validator. These build confidence
/// incrementally before the larger IT that validates full documents.
class OpenRPCFragmentsUnitTest extends JsonSchemaTestBase {

    @Test
    void info_object_minimal_required_fields() {
        LOG.info(() -> "TEST: " + getClass().getSimpleName() + "#info_object_minimal_required_fields");
        final var schema = JsonSchema.compile(Json.parse(
            "{" +
            "\"type\":\"object\"," +
            "\"required\":[\"title\",\"version\"]," +
            "\"properties\":{\"title\":{\"type\":\"string\"},\"version\":{\"type\":\"string\"}}," +
            "\"additionalProperties\":true" +
            "}"
        ));

        final var good = schema.validate(Json.parse("{\"title\":\"X\",\"version\":\"1.0\"}"));
        assertThat(good.valid()).isTrue();

        final var bad = schema.validate(Json.parse("{\"title\":\"X\"}"));
        assertThat(bad.valid()).isFalse();
    }

    @Test
    void method_object_requires_name_and_params() {
        LOG.info(() -> "TEST: " + getClass().getSimpleName() + "#method_object_requires_name_and_params");
        final var schema = JsonSchema.compile(Json.parse("""
            {
              "type":"object",
              "required":["name","params"],
              "properties":{
                "name":{"type":"string","minLength":1},
                "params":{"type":"array"}
              },
              "additionalProperties": true
            }
        """));

        final var ok = schema.validate(Json.parse("{\"name\":\"op\",\"params\":[]}"));
        assertThat(ok.valid()).isTrue();

        final var missing = schema.validate(Json.parse("{\"name\":\"op\"}"));
        assertThat(missing.valid()).isFalse();
    }

    @Test
    void openrpc_field_is_non_empty_string() {
        final var schema = JsonSchema.compile(Json.parse(
            "{" +
            "\"type\":\"object\"," +
            "\"required\":[\"openrpc\"]," +
            "\"properties\":{\"openrpc\":{\"type\":\"string\",\"minLength\":1}}" +
            "}"
        ));
        assertThat(schema.validate(Json.parse("{\"openrpc\":\"1.3.0\"}"))).extracting("valid").isEqualTo(true);
        assertThat(schema.validate(Json.parse("{\"openrpc\":\"\"}"))).extracting("valid").isEqualTo(false);
        assertThat(schema.validate(Json.parse("{\"openrpc\":1}"))).extracting("valid").isEqualTo(false);
    }

    @Test
    void servers_array_items_are_objects() {
        final var schema = JsonSchema.compile(Json.parse(
            "{" +
            "\"type\":\"object\"," +
            "\"properties\":{\"servers\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}}}" +
            "}"
        ));
        assertThat(schema.validate(Json.parse("{\"servers\":[{}]}"))).extracting("valid").isEqualTo(true);
        assertThat(schema.validate(Json.parse("{\"servers\":[1,2,3]}"))).extracting("valid").isEqualTo(false);
    }

    @Test
    void components_object_accepts_any_members() {
        final var schema = JsonSchema.compile(Json.parse(
            "{" +
            "\"type\":\"object\"," +
            "\"properties\":{\"components\":{\"type\":\"object\"}}," +
            "\"additionalProperties\":true" +
            "}"
        ));
        assertThat(schema.validate(Json.parse("{\"components\":{\"x\":1}}"))).extracting("valid").isEqualTo(true);
        assertThat(schema.validate(Json.parse("{\"components\":1}"))).extracting("valid").isEqualTo(false);
    }

    @Test
    void param_object_requires_name_and_allows_schema_object() {
        LOG.info(() -> "TEST: " + getClass().getSimpleName() + "#param_object_requires_name_and_allows_schema_object");
        final var schema = JsonSchema.compile(Json.parse(
            "{" +
            "\"type\":\"object\"," +
            "\"required\":[\"name\"]," +
            "\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":1},\"schema\":{\"type\":\"object\"}}," +
            "\"additionalProperties\":true" +
            "}"
        ));

        final var ok = schema.validate(Json.parse("{\"name\":\"n\",\"schema\":{}}"));
        assertThat(ok.valid()).isTrue();

        final var bad = schema.validate(Json.parse("{\"schema\":{}}"));
        assertThat(bad.valid()).isFalse();
    }

    @Test
    void uri_format_example_as_used_by_openrpc_examples() {
        LOG.info(() -> "TEST: " + getClass().getSimpleName() + "#uri_format_example_as_used_by_openrpc_examples");
        final var schema = JsonSchema.compile(Json.parse("{\"type\":\"string\",\"format\":\"uri\"}"));

        assertThat(schema.validate(Json.parse("\"https://open-rpc.org\""))).extracting("valid").isEqualTo(true);
    }
}
