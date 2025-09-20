package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class JsonSchemaRemoteRefTest extends JsonSchemaLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonSchemaRemoteRefTest.class.getName());

    @Test
    void resolves_http_ref_to_pointer_inside_remote_doc() {
        LOG.info(() -> "START resolves_http_ref_to_pointer_inside_remote_doc");
        final var remoteUri = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/a.json");
        final var remoteDoc = Json.parse("""
            {
              "$id": "file:///JsonSchemaRemoteRefTest/a.json",
              "$defs": {
                "X": {
                  "type": "integer",
                  "minimum": 2
                }
              }
            }
            """);
        logRemote("remoteDoc=", remoteDoc);
        final var fetcher = new MapRemoteFetcher(Map.of(remoteUri, RemoteDocument.json(remoteDoc)));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher);

        LOG.finer(() -> "Compiling schema for file remote ref");
        final var schema = JsonSchema.compile(
            Json.parse("""
            {"$ref":"file:///JsonSchemaRemoteRefTest/a.json#/$defs/X"}
            """),
            JsonSchema.Options.DEFAULT,
            options
        );

        final var pass = schema.validate(Json.parse("3"));
        logResult("validate-3", pass);
        assertThat(pass.valid()).isTrue();
        final var fail = schema.validate(Json.parse("1"));
        logResult("validate-1", fail);
        assertThat(fail.valid()).isFalse();
    }

    @Test
    void resolves_relative_ref_against_remote_id_chain() {
        LOG.info(() -> "START resolves_relative_ref_against_remote_id_chain");
        final var remoteUri = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/base/root.json");
        final var remoteDoc = Json.parse("""
            {
              "$id": "%s",
              "$defs": {
                "Module": {
                  "$id": "dir/schema.json",
                  "$defs": {
                    "Name": {
                      "type": "string",
                      "minLength": 2
                    }
                  },
                  "$ref": "#/$defs/Name"
                }
              }
            }
            """.formatted(remoteUri));
        logRemote("remoteDoc=", remoteDoc);
        final var fetcher = new MapRemoteFetcher(Map.of(remoteUri, RemoteDocument.json(remoteDoc)));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher);

        LOG.finer(() -> "Compiling schema for relative remote $id chain");
        final var schema = JsonSchema.compile(
            Json.parse("""
            {"$ref":"%s#/$defs/Module"}
            """.formatted(remoteUri)),
            JsonSchema.Options.DEFAULT,
            options
        );

        final var ok = schema.validate(Json.parse("\"Al\""));
        logResult("validate-Al", ok);
        assertThat(ok.valid()).isTrue();
        final var bad = schema.validate(Json.parse("\"A\""));
        logResult("validate-A", bad);
        assertThat(bad.valid()).isFalse();
    }

    @Test
    void resolves_named_anchor_in_remote_doc() {
        LOG.info(() -> "START resolves_named_anchor_in_remote_doc");
        final var remoteUri = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/anchors.json");
        final var remoteDoc = Json.parse("""
            {
              "$id": "%s",
              "$anchor": "root",
              "$defs": {
                "A": {
                  "$anchor": "top",
                  "type": "string"
                }
              }
            }
            """.formatted(remoteUri));
        logRemote("remoteDoc=", remoteDoc);
        final var fetcher = new MapRemoteFetcher(Map.of(remoteUri, RemoteDocument.json(remoteDoc)));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher);

        LOG.finer(() -> "Compiling schema for remote anchor");
        final var schema = JsonSchema.compile(
            Json.parse("""
            {"$ref":"%s#top"}
            """.formatted(remoteUri)),
            JsonSchema.Options.DEFAULT,
            options
        );

        final var pass = schema.validate(Json.parse("\"x\""));
        logResult("validate-x", pass);
        assertThat(pass.valid()).isTrue();
        final var fail = schema.validate(Json.parse("1"));
        logResult("validate-1", fail);
        assertThat(fail.valid()).isFalse();
    }

    @Test
    void error_unresolvable_remote_pointer() {
        LOG.info(() -> "START error_unresolvable_remote_pointer");
        final var remoteUri = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/a.json");
        final var remoteDoc = Json.parse("""
            {
              "$id": "file:///JsonSchemaRemoteRefTest/a.json",
              "$defs": {
                "Present": {"type":"integer"}
              }
            }
            """);
        logRemote("remoteDoc=", remoteDoc);
        final var fetcher = new MapRemoteFetcher(Map.of(remoteUri, RemoteDocument.json(remoteDoc)));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher);

        LOG.finer(() -> "Attempting compile expecting pointer failure");
        final ThrowableAssert.ThrowingCallable compile = () -> JsonSchema.compile(
            toJson("""
            {"$ref":"file:///JsonSchemaRemoteRefTest/a.json#/$defs/Missing"}
            """),
            JsonSchema.Options.DEFAULT,
            options
        );

        LOG.finer(() -> "Asserting RemoteResolutionException for missing pointer");
        assertThatThrownBy(compile)
            .isInstanceOf(JsonSchema.RemoteResolutionException.class)
            .hasFieldOrPropertyWithValue("reason", JsonSchema.RemoteResolutionException.Reason.POINTER_MISSING)
            .hasMessageContaining("file:///JsonSchemaRemoteRefTest/a.json#/$defs/Missing");
    }

    @Test
    void denies_disallowed_scheme() {
        LOG.info(() -> "START denies_disallowed_scheme");
        final var fetcher = new MapRemoteFetcher(Map.of());
        final var policy = JsonSchema.FetchPolicy.defaults().withAllowedSchemes(Set.of("http", "https"));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher).withFetchPolicy(policy);

        LOG.finer(() -> "Compiling schema expecting disallowed scheme");
        final ThrowableAssert.ThrowingCallable compile = () -> JsonSchema.compile(
            toJson("""
            {"$ref":"file:///etc/passwd#/"}
            """),
            JsonSchema.Options.DEFAULT,
            options
        );

        LOG.finer(() -> "Asserting RemoteResolutionException for scheme policy");
        assertThatThrownBy(compile)
            .isInstanceOf(JsonSchema.RemoteResolutionException.class)
            .hasFieldOrPropertyWithValue("reason", JsonSchema.RemoteResolutionException.Reason.POLICY_DENIED)
            .hasMessageContaining("file:///etc/passwd");
    }

    @Test
    void enforces_timeout_and_size_limits() {
        LOG.info(() -> "START enforces_timeout_and_size_limits");
        final var remoteUri = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/cache.json");
        final var remoteDoc = toJson("""
            {"type":"integer"}
            """);
        logRemote("remoteDoc=", remoteDoc);

        final var policy = JsonSchema.FetchPolicy.defaults()
            .withMaxDocumentBytes()
            .withTimeout(Duration.ofMillis(5));

        final var oversizedFetcher = new MapRemoteFetcher(Map.of(remoteUri, RemoteDocument.json(remoteDoc, 2048, Optional.of(Duration.ofMillis(1)))));
        final var timeoutFetcher = new MapRemoteFetcher(Map.of(remoteUri, RemoteDocument.json(remoteDoc, 1, Optional.of(Duration.ofMillis(50)))));

        final var oversizedOptions = JsonSchema.CompileOptions.remoteDefaults(oversizedFetcher).withFetchPolicy(policy);
        final var timeoutOptions = JsonSchema.CompileOptions.remoteDefaults(timeoutFetcher).withFetchPolicy(policy);

        LOG.finer(() -> "Asserting payload too large");
        final ThrowableAssert.ThrowingCallable oversizedCompile = () -> JsonSchema.compile(
            toJson("""
            {"$ref":"file:///JsonSchemaRemoteRefTest/cache.json"}
            """),
            JsonSchema.Options.DEFAULT,
            oversizedOptions
        );

        assertThatThrownBy(oversizedCompile)
            .isInstanceOf(JsonSchema.RemoteResolutionException.class)
            .hasFieldOrPropertyWithValue("reason", JsonSchema.RemoteResolutionException.Reason.PAYLOAD_TOO_LARGE)
            .hasMessageContaining("file:///JsonSchemaRemoteRefTest/cache.json");

        LOG.finer(() -> "Asserting timeout policy violation");
        final ThrowableAssert.ThrowingCallable timeoutCompile = () -> JsonSchema.compile(
            toJson("""
            {"$ref":"file:///JsonSchemaRemoteRefTest/cache.json"}
            """),
            JsonSchema.Options.DEFAULT,
            timeoutOptions
        );

        assertThatThrownBy(timeoutCompile)
            .isInstanceOf(JsonSchema.RemoteResolutionException.class)
            .hasFieldOrPropertyWithValue("reason", JsonSchema.RemoteResolutionException.Reason.TIMEOUT)
            .hasMessageContaining("file:///JsonSchemaRemoteRefTest/cache.json");
    }

    @Test
    void caches_remote_doc_and_reuses_compiled_node() {
        LOG.info(() -> "START caches_remote_doc_and_reuses_compiled_node");
        final var remoteUri = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/cache.json");
        final var remoteDoc = toJson("""
            {
              "$id": "file:///JsonSchemaRemoteRefTest/cache.json",
              "type": "integer"
            }
            """);
        logRemote("remoteDoc=", remoteDoc);

        final var fetcher = new CountingFetcher(Map.of(remoteUri, RemoteDocument.json(remoteDoc)));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher);

        LOG.finer(() -> "Compiling schema twice with same remote ref");
        final var schema = JsonSchema.compile(
            toJson("""
            {
              "allOf": [
                {"$ref":"file:///JsonSchemaRemoteRefTest/cache.json"},
                {"$ref":"file:///JsonSchemaRemoteRefTest/cache.json"}
                  ]
                }
                """),
            JsonSchema.Options.DEFAULT,
            options
        );

        assertThat(fetcher.calls()).isEqualTo(1);
        final var first = schema.validate(toJson("5"));
        logResult("validate-5-first", first);
        assertThat(first.valid()).isTrue();
        final var second = schema.validate(toJson("5"));
        logResult("validate-5-second", second);
        assertThat(second.valid()).isTrue();
        assertThat(fetcher.calls()).isEqualTo(1);
    }

    @Test
    void detects_cross_document_cycle() {
        LOG.info(() -> "START detects_cross_document_cycle");
        final var uriA = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/a.json");
        final var uriB = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/b.json");
        final var docA = toJson("""
            {"$id":"file:///JsonSchemaRemoteRefTest/a.json","$ref":"file:///JsonSchemaRemoteRefTest/b.json"}
            """);
        final var docB = toJson("""
            {"$id":"file:///JsonSchemaRemoteRefTest/b.json","$ref":"file:///JsonSchemaRemoteRefTest/a.json"}
            """);
        logRemote("docA=", docA);
        logRemote("docB=", docB);

        final var fetcher = new MapRemoteFetcher(Map.of(
            uriA, RemoteDocument.json(docA),
            uriB, RemoteDocument.json(docB)
        ));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher);

        LOG.finer(() -> "Compiling schema expecting cycle resolution");
        final var schema = JsonSchema.compile(
            toJson("""
            {"$ref":"file:///JsonSchemaRemoteRefTest/a.json"}
            """),
            JsonSchema.Options.DEFAULT,
            options
        );

        final var result = schema.validate(toJson("true"));
        logResult("validate-true", result);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void resolves_anchor_defined_in_nested_remote_scope() {
        LOG.info(() -> "START resolves_anchor_defined_in_nested_remote_scope");
        final var remoteUri = TestResourceUtils.getTestResourceUri("JsonSchemaRemoteRefTest/nest.json");
        final var remoteDoc = toJson("""
            {
              "$id": "file:///JsonSchemaRemoteRefTest/nest.json",
              "$defs": {
                "Inner": {
                  "$anchor": "inner",
                  "type": "number",
                  "minimum": 0
                }
              }
            }
            """);
        logRemote("remoteDoc=", remoteDoc);

        final var fetcher = new MapRemoteFetcher(Map.of(remoteUri, RemoteDocument.json(remoteDoc)));
        final var options = JsonSchema.CompileOptions.remoteDefaults(fetcher);

        LOG.finer(() -> "Compiling schema for nested anchor");
        final var schema = JsonSchema.compile(
            toJson("""
            {"$ref":"file:///JsonSchemaRemoteRefTest/nest.json#inner"}
            """),
            JsonSchema.Options.DEFAULT,
            options
        );

        final var positive = schema.validate(toJson("1"));
        logResult("validate-1", positive);
        assertThat(positive.valid()).isTrue();
        final var negative = schema.validate(toJson("-1"));
        logResult("validate-minus1", negative);
        assertThat(negative.valid()).isFalse();
    }

    private static JsonValue toJson(String json) {
        return Json.parse(json);
    }

    private record RemoteDocument(JsonValue document, long byteSize, Optional<Duration> elapsed) {
        static RemoteDocument json(JsonValue document) {
            return new RemoteDocument(document, document.toString().getBytes().length, Optional.empty());
        }

        static RemoteDocument json(JsonValue document, long byteSize, Optional<Duration> elapsed) {
            return new RemoteDocument(document, byteSize, elapsed);
        }
    }

    private static final class MapRemoteFetcher implements JsonSchema.RemoteFetcher {
        private final Map<URI, RemoteDocument> documents;

        private MapRemoteFetcher(Map<URI, RemoteDocument> documents) {
            this.documents = Map.copyOf(documents);
        }

        @Override
        public FetchResult fetch(URI uri, JsonSchema.FetchPolicy policy) {
            final var doc = documents.get(uri);
            if (doc == null) {
                throw new JsonSchema.RemoteResolutionException(
                    uri,
                    JsonSchema.RemoteResolutionException.Reason.NOT_FOUND,
                    "No remote document registered for " + uri
                );
            }
            return new FetchResult(doc.document(), doc.byteSize(), doc.elapsed());
        }
    }

    private static final class CountingFetcher implements JsonSchema.RemoteFetcher {
        private final MapRemoteFetcher delegate;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingFetcher(Map<URI, RemoteDocument> documents) {
            this.delegate = new MapRemoteFetcher(documents);
        }

        int calls() {
            return calls.get();
        }
        
        @Override
        public FetchResult fetch(URI uri, JsonSchema.FetchPolicy policy) {
            calls.incrementAndGet();
            return delegate.fetch(uri, policy);
        }
    }

    private static void logRemote(String label, JsonValue json) {
        LOG.finest(() -> label + json);
    }

    private static void logResult(String label, JsonSchema.ValidationResult result) {
        LOG.fine(() -> label + " valid=" + result.valid());
        if (!result.valid()) {
            LOG.finest(() -> label + " errors=" + result.errors());
        }
    }
}
