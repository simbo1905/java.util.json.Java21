package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Compile-only posture: deny all remote fetches to reveal which fragments
/// compile locally. This is a unit-level gate prior to the full OpenRPC IT.
class OpenRPCCompileOnlyTest extends JsonSchemaLoggingConfig {

    @Test
    void compile_local_fragment_succeeds_with_remote_denied() {
        final var fragment = "{" +
                "\"$defs\":{\"X\":{\"type\":\"integer\"}}," +
                "\"$ref\":\"#/$defs/X\"" +
                "}";

        final var fetcher = new MapRemoteFetcher(Map.of());
        final var policy = JsonSchema.FetchPolicy.defaults().withAllowedSchemes(Set.of("file"));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher).withFetchPolicy(policy);

        final var schema = JsonSchema.compile(Json.parse(fragment), JsonSchema.Options.DEFAULT, options);
        assertThat(schema.validate(Json.parse("1")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"x\""))).extracting("valid").isEqualTo(false);
    }

    @Test
    void compile_remote_ref_is_denied_by_policy() {
        final var fragment = "{" +
                "\"$ref\":\"http://example.com/openrpc.json#/$defs/X\"" +
                "}";

        final var fetcher = new MapRemoteFetcher(Map.of());
        final var policy = JsonSchema.FetchPolicy.defaults().withAllowedSchemes(Set.of("file"));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher).withFetchPolicy(policy);

        assertThatThrownBy(() -> JsonSchema.compile(Json.parse(fragment), JsonSchema.Options.DEFAULT, options))
                .isInstanceOf(JsonSchema.RemoteResolutionException.class)
                .hasFieldOrPropertyWithValue("reason", JsonSchema.RemoteResolutionException.Reason.POLICY_DENIED)
                .hasMessageContaining("http://example.com/openrpc.json");
    }

    private static final class MapRemoteFetcher implements JsonSchema.RemoteFetcher {
        private final Map<URI, JsonValue> documents;
        private MapRemoteFetcher(Map<URI, JsonValue> documents) { this.documents = Map.copyOf(documents); }
        @Override public FetchResult fetch(URI uri, JsonSchema.FetchPolicy policy) {
            throw new JsonSchema.RemoteResolutionException(uri,
                    JsonSchema.RemoteResolutionException.Reason.NOT_FOUND,
                    "No remote document registered for " + uri);
        }
    }
}
