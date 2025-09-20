package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaRemoteServerRefTest extends JsonSchemaTestBase {

    @RegisterExtension
    static final RemoteSchemaServerRule SERVER = new RemoteSchemaServerRule();

    @Test
    void resolves_pointer_inside_remote_doc_via_http() {
        var policy = JsonSchema.FetchPolicy.defaults().withAllowedSchemes(Set.of("http","https"));
        var options = JsonSchema.CompileOptions.remoteDefaults(new VirtualThreadHttpFetcher()).withFetchPolicy(policy);
        var schema = Json.parse("{\"$ref\":\"" + SERVER.url("/a.json") + "#/$defs/X\"}");
        var compiled = JsonSchema.compile(schema, JsonSchema.Options.DEFAULT, options);
        assertThat(compiled.validate(Json.parse("1")).valid()).isTrue();
        assertThat(compiled.validate(Json.parse("0")).valid()).isFalse();
    }

    @Test
    void remote_cycle_logs_error_taxonomy() {
        var policy = JsonSchema.FetchPolicy.defaults().withAllowedSchemes(Set.of("http","https"));
        var options = JsonSchema.CompileOptions.remoteDefaults(new VirtualThreadHttpFetcher()).withFetchPolicy(policy);
        try (CapturedLogs logs = captureLogs(java.util.logging.Level.SEVERE)) {
            try { JsonSchema.compile(Json.parse("{\"$ref\":\"" + SERVER.url("/cycle1.json") + "#\"}"), JsonSchema.Options.DEFAULT, options); } catch (RuntimeException ignored) {}
            assertThat(logs.lines().stream().anyMatch(s -> s.startsWith("ERROR: CYCLE:"))).isTrue();
        }
    }
}

