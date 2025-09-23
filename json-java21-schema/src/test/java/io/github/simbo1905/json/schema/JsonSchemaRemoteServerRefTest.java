package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaRemoteServerRefTest extends JsonSchemaTestBase {

  @RegisterExtension
  static final RemoteSchemaServerRule SERVER = new RemoteSchemaServerRule();

  @Test
    void resolves_pointer_inside_remote_doc_via_http() {
        var policy = FetchPolicy.defaults().withAllowedSchemes(Set.of(FetchPolicy.HTTP, FetchPolicy.HTTPS));
        var fetcher = new JsonSchema.CompileOptions.DelegatingRemoteFetcher(
            new VirtualThreadHttpFetcher(FetchPolicy.HTTP),
            new VirtualThreadHttpFetcher(FetchPolicy.HTTPS));
        var options = JsonSchema.CompileOptions.remoteDefaults(fetcher).withFetchPolicy(policy);
        var schema = Json.parse("{\"$ref\":\"" + SERVER.url("/a.json") + "#/$defs/X\"}");
        var compiled = JsonSchema.compile(URI.create("urn:inmemory:root"), schema, JsonSchema.JsonSchemaOptions.DEFAULT, options);
        assertThat(compiled.validate(Json.parse("1")).valid()).isTrue();
        assertThat(compiled.validate(Json.parse("0")).valid()).isFalse();
    }

    @Test
    void remote_cycle_detected_and_throws() {
        var policy = FetchPolicy.defaults().withAllowedSchemes(Set.of(FetchPolicy.HTTP, FetchPolicy.HTTPS));
        var fetcher = new JsonSchema.CompileOptions.DelegatingRemoteFetcher(
            new VirtualThreadHttpFetcher(FetchPolicy.HTTP),
            new VirtualThreadHttpFetcher(FetchPolicy.HTTPS));
        var options = JsonSchema.CompileOptions.remoteDefaults(fetcher).withFetchPolicy(policy);
        
        // Cycles should be detected and throw an exception regardless of scheme
        assertThatThrownBy(() -> JsonSchema.compile(
            URI.create("urn:inmemory:root"), Json.parse("{\"$ref\":\"" + SERVER.url("/cycle1.json") + "#\"}"),
            JsonSchema.JsonSchemaOptions.DEFAULT,
            options
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ERROR: CYCLE: remote $ref cycle");
    }
}
