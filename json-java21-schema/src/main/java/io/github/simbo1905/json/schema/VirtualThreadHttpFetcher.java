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
import java.util.logging.Logger;

/// `RemoteFetcher` implementation that performs blocking HTTP requests
/// on Java 21 virtual threads. Reuses responses via an in-memory cache
/// so repeated `$ref` lookups avoid re-fetching during the same run.
final class VirtualThreadHttpFetcher implements JsonSchema.RemoteFetcher {
    static final Logger LOG = Logger.getLogger(VirtualThreadHttpFetcher.class.getName());

    private final HttpClient client;
    private final ConcurrentMap<URI, FetchResult> cache = new ConcurrentHashMap<>();
    private final AtomicInteger documentCount = new AtomicInteger();
    private final AtomicLong totalBytes = new AtomicLong();

    VirtualThreadHttpFetcher() {
        this(HttpClient.newBuilder().build());
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
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.TIMEOUT, "Interrupted while fetching " + uri, e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof JsonSchema.RemoteResolutionException ex) {
                throw ex;
            }
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.NETWORK_ERROR, "Failed fetching " + uri, cause);
        }
    }

    private FetchResult performFetch(URI uri, JsonSchema.FetchPolicy policy) {
        enforceDocumentLimits(uri, policy);

        long start = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(policy.timeout())
            .header("Accept", "application/schema+json, application/json")
            .GET()
            .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status / 100 != 2) {
                throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.NOT_FOUND, "HTTP " + status + " fetching " + uri);
            }

            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > policy.maxDocumentBytes()) {
                throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.PAYLOAD_TOO_LARGE, "Payload too large for " + uri);
            }

            long total = totalBytes.addAndGet(bytes.length);
            if (total > policy.maxTotalBytes()) {
                throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.POLICY_DENIED, "Total fetched bytes exceeded policy for " + uri);
            }

            JsonValue json = Json.parse(response.body());
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            return new FetchResult(json, bytes.length, Optional.of(elapsed));
        } catch (HttpTimeoutException e) {
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.TIMEOUT, "Fetch timeout for " + uri, e);
        } catch (IOException e) {
            throw new JsonSchema.RemoteResolutionException(uri, JsonSchema.RemoteResolutionException.Reason.NETWORK_ERROR, "I/O error fetching " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}
