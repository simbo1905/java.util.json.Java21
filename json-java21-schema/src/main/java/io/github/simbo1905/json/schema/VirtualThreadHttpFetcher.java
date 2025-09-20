package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static io.github.simbo1905.json.schema.SchemaLogging.LOG;

/// `RemoteFetcher` implementation that performs blocking HTTP requests
/// on Java 21 virtual threads. Reuses responses via an in-memory cache
/// so repeated `$ref` lookups avoid re-fetching during the same run.
final class VirtualThreadHttpFetcher implements JsonSchema.RemoteFetcher {

    private final HttpClient client;
    private final ConcurrentMap<URI, FetchResult> cache = new ConcurrentHashMap<>();
    private final AtomicInteger documentCount = new AtomicInteger();
    private final AtomicLong totalBytes = new AtomicLong();

    VirtualThreadHttpFetcher() {
        this(HttpClient.newBuilder().build());
        // Centralized network logging banner
        LOG.config(() -> "http.fetcher init redirectPolicy=default timeout=" + 0 + "ms");
    }

    VirtualThreadHttpFetcher(HttpClient client) {
        this.client = client;
    }

    @Override
    public FetchResult fetch(URI uri, JsonSchema.FetchPolicy policy) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(policy, "policy");
        ensureSchemeAllowed(uri, policy.allowedSchemes());

        FetchResult cached = cache.get(uri);
        if (cached != null) {
            LOG.finer(() -> "VirtualThreadHttpFetcher.cacheHit " + uri);
            return cached;
        }

        FetchResult fetched = fetchOnVirtualThread(uri, policy);
        FetchResult previous = cache.putIfAbsent(uri, fetched);
        return previous != null ? previous : fetched;
    }

    private FetchResult fetchOnVirtualThread(URI uri, JsonSchema.FetchPolicy policy) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<FetchResult> future = executor.submit(() -> performFetch(uri, policy));
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - interrupted TIMEOUT");
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.TIMEOUT, "Interrupted while fetching " + uri, e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof JsonSchema.RemoteResolutionException ex) {
                throw ex;
            }
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - exec NETWORK_ERROR");
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.NETWORK_ERROR, "Failed fetching " + uri, cause);
        }
    }

    private FetchResult performFetch(URI uri, JsonSchema.FetchPolicy policy) {
        enforceDocumentLimits(uri, policy);
        LOG.finer(() -> "http.fetch start method=GET uri=" + uri);

        long start = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(policy.timeout())
            .header("Accept", "application/schema+json, application/json")
            .GET()
            .build();

        try {
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status / 100 != 2) {
                LOG.severe(() -> "ERROR: FETCH: " + uri + " - " + status + " NOT_FOUND");
                throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.NOT_FOUND, "HTTP " + status + " fetching " + uri);
            }

            // Stream with hard cap to enforce maxDocumentBytes during read
            byte[] bytes;
            try (java.io.InputStream in = response.body();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                long cap = policy.maxDocumentBytes();
                long readTotal = 0L;
                while (true) {
                    int n = in.read(buf);
                    if (n == -1) break;
                    readTotal += n;
                    if (readTotal > cap) {
                        LOG.severe(() -> "ERROR: FETCH: " + uri + " - 413 PAYLOAD_TOO_LARGE");
                        throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.PAYLOAD_TOO_LARGE, "Payload too large for " + uri);
                    }
                    out.write(buf, 0, n);
                }
                bytes = out.toByteArray();
            }

            long total = totalBytes.addAndGet(bytes.length);
            if (total > policy.maxTotalBytes()) {
                LOG.severe(() -> "ERROR: FETCH: " + uri + " - policy TOTAL_BYTES_EXCEEDED");
                throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.POLICY_DENIED, "Total fetched bytes exceeded policy for " + uri);
            }

            String body = new String(bytes, StandardCharsets.UTF_8);
            JsonValue json = Json.parse(body);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            LOG.finer(() -> "http.fetch done  status=" + status + " bytes=" + bytes.length + " uri=" + uri);
            return new FetchResult(json, bytes.length, Optional.of(elapsed));
        } catch (HttpTimeoutException e) {
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - timeout TIMEOUT");
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.TIMEOUT, "Fetch timeout for " + uri, e);
        } catch (IOException e) {
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - io NETWORK_ERROR");
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.NETWORK_ERROR, "I/O error fetching " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - interrupted TIMEOUT");
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.TIMEOUT, "Interrupted fetching " + uri, e);
        }
    }

    private void ensureSchemeAllowed(URI uri, Set<String> allowedSchemes) {
        String scheme = uri.getScheme();
        if (scheme == null || !allowedSchemes.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.POLICY_DENIED, "Disallowed scheme: " + scheme);
        }
    }

    private void enforceDocumentLimits(URI uri, JsonSchema.FetchPolicy policy) {
        int docs = documentCount.incrementAndGet();
        if (docs > policy.maxDocuments()) {
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.POLICY_DENIED, "Maximum document count exceeded for " + uri);
        }
    }

    /// Fetch schema JSON for MVF work-stack architecture
    JsonValue fetchSchemaJson(java.net.URI docUri) {
        LOG.fine(() -> "fetchSchemaJson: start fetch, method=GET, uri=" + docUri + ", timeout=default");
        LOG.finest(() -> "fetchSchemaJson: docUri object=" + docUri + ", scheme=" + docUri.getScheme() + ", host=" + docUri.getHost() + ", path=" + docUri.getPath());
        
        try {
            long start = System.nanoTime();
            JsonSchema.FetchPolicy policy = JsonSchema.FetchPolicy.defaults();
            LOG.finest(() -> "fetchSchemaJson: policy object=" + policy + ", allowedSchemes=" + policy.allowedSchemes() + ", maxDocumentBytes=" + policy.maxDocumentBytes() + ", timeout=" + policy.timeout());
            
            JsonSchema.RemoteFetcher.FetchResult result = fetch(docUri, policy);
            LOG.finest(() -> "fetchSchemaJson: fetch result object=" + result + ", document=" + result.document() + ", byteSize=" + result.byteSize() + ", elapsed=" + result.elapsed());
            
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            LOG.finer(() -> "fetchSchemaJson: response code=200, content length=" + result.byteSize() + ", elapsed ms=" + elapsed.toMillis());
            LOG.finest(() -> "fetchSchemaJson: returning document object=" + result.document() + ", type=" + result.document().getClass().getSimpleName() + ", content=" + result.document().toString());
            
            return result.document();
        } catch (JsonSchema.RemoteResolutionException e) {
            // Already logged by the fetch path; rethrow
            throw e;
        } catch (Exception e) {
            LOG.severe(() -> "ERROR: FETCH: " + docUri + " - unexpected NETWORK_ERROR");
            throw new JsonSchema.RemoteResolutionException(docUri, JsonSchema.RemoteResolutionException.Reason.NETWORK_ERROR, "Failed to fetch schema", e);
        }
    }
}
