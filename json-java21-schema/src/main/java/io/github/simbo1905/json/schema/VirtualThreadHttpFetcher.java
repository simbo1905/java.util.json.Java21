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

import static io.github.simbo1905.json.schema.JsonSchema.LOG;

/// `RemoteFetcher` implementation that performs blocking HTTP requests
/// on Java 21 virtual threads. Reuses responses via an in-memory cache
/// so repeated `$ref` lookups avoid re-fetching during the same run.
final class VirtualThreadHttpFetcher implements JsonSchema.RemoteFetcher {

    private final HttpClient client;
    private final ConcurrentMap<URI, FetchResult> cache = new ConcurrentHashMap<>();
    private final AtomicInteger documentCount = new AtomicInteger();
    private final AtomicLong totalBytes = new AtomicLong();
    private final String scheme;

    VirtualThreadHttpFetcher(String scheme) {
        this(scheme, HttpClient.newBuilder().build());
        LOG.config(() -> "http.fetcher init scheme=" + this.scheme);
    }

    VirtualThreadHttpFetcher(String scheme, HttpClient client) {
        this.scheme = Objects.requireNonNull(scheme, "scheme").toLowerCase(Locale.ROOT);
        this.client = client;
    }

    @Override
    public String scheme() {
        return scheme;
    }

    @Override
    public FetchResult fetch(URI uri, FetchPolicy policy) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(policy, "policy");
        String uriScheme = ensureSchemeAllowed(uri, policy.allowedSchemes());
        if (!scheme.equals(uriScheme)) {
            throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.POLICY_DENIED,
                "Fetcher configured for scheme " + scheme + " but received " + uriScheme);
        }

        FetchResult cached = cache.get(uri);
        if (cached != null) {
            LOG.finer(() -> "VirtualThreadHttpFetcher.cacheHit " + uri);
            return cached;
        }

        FetchResult fetched = fetchOnVirtualThread(uri, policy);
        FetchResult previous = cache.putIfAbsent(uri, fetched);
        return previous != null ? previous : fetched;
    }

    private FetchResult fetchOnVirtualThread(URI uri, FetchPolicy policy) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<FetchResult> future = executor.submit(() -> performFetch(uri, policy));
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - interrupted TIMEOUT");
            throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.TIMEOUT, "Interrupted while fetching " + uri, e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RemoteResolutionException ex) {
                throw ex;
            }
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - exec NETWORK_ERROR");
            throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.NETWORK_ERROR, "Failed fetching " + uri, cause);
        }
    }

    private FetchResult performFetch(URI uri, FetchPolicy policy) {
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
                throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.NOT_FOUND, "HTTP " + status + " fetching " + uri);
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
                        throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.PAYLOAD_TOO_LARGE, "Payload too large for " + uri);
                    }
                    out.write(buf, 0, n);
                }
                bytes = out.toByteArray();
            }

            long total = totalBytes.addAndGet(bytes.length);
            if (total > policy.maxTotalBytes()) {
                LOG.severe(() -> "ERROR: FETCH: " + uri + " - policy TOTAL_BYTES_EXCEEDED");
                throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.POLICY_DENIED, "Total fetched bytes exceeded policy for " + uri);
            }

            String body = new String(bytes, StandardCharsets.UTF_8);
            JsonValue json = Json.parse(body);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            LOG.finer(() -> "http.fetch done  status=" + status + " bytes=" + bytes.length + " uri=" + uri);
            return new FetchResult(json, bytes.length, Optional.of(elapsed));
        } catch (HttpTimeoutException e) {
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - timeout TIMEOUT");
            throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.TIMEOUT, "Fetch timeout for " + uri, e);
        } catch (IOException e) {
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - io NETWORK_ERROR");
            throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.NETWORK_ERROR, "I/O error fetching " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.severe(() -> "ERROR: FETCH: " + uri + " - interrupted TIMEOUT");
            throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.TIMEOUT, "Interrupted fetching " + uri, e);
        }
    }

    private String ensureSchemeAllowed(URI uri, Set<String> allowedSchemes) {
        String uriScheme = uri.getScheme();
        if (uriScheme == null || !allowedSchemes.contains(uriScheme.toLowerCase(Locale.ROOT))) {
            throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.POLICY_DENIED, "Disallowed scheme: " + uriScheme);
        }
        return uriScheme.toLowerCase(Locale.ROOT);
    }

    private void enforceDocumentLimits(URI uri, FetchPolicy policy) {
        int docs = documentCount.incrementAndGet();
        if (docs > policy.maxDocuments()) {
            throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.POLICY_DENIED, "Maximum document count exceeded for " + uri);
        }
    }

    /// Fetch schema JSON for MVF work-stack architecture
    JsonValue fetchSchemaJson(java.net.URI docUri) {
        LOG.fine(() -> "fetchSchemaJson: start fetch, method=GET, uri=" + docUri + ", timeout=default");
        LOG.finest(() -> "fetchSchemaJson: docUri object=" + docUri + ", scheme=" + docUri.getScheme() + ", host=" + docUri.getHost() + ", path=" + docUri.getPath());
        
        try {
            long start = System.nanoTime();
            FetchPolicy policy = FetchPolicy.defaults();
            LOG.finest(() -> "fetchSchemaJson: policy object=" + policy + ", allowedSchemes=" + policy.allowedSchemes() + ", maxDocumentBytes=" + policy.maxDocumentBytes() + ", timeout=" + policy.timeout());
            
            JsonSchema.RemoteFetcher.FetchResult result = fetch(docUri, policy);
            LOG.finest(() -> "fetchSchemaJson: fetch result object=" + result + ", document=" + result.document() + ", byteSize=" + result.byteSize() + ", elapsed=" + result.elapsed());
            
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            LOG.finer(() -> "fetchSchemaJson: response code=200, content length=" + result.byteSize() + ", elapsed ms=" + elapsed.toMillis());
            LOG.finest(() -> "fetchSchemaJson: returning document object=" + result.document() + ", type=" + result.document().getClass().getSimpleName() + ", content=" + result.document().toString());
            
            return result.document();
        } catch (RemoteResolutionException e) {
            // Already logged by the fetch path; rethrow
            throw e;
        } catch (Exception e) {
            LOG.severe(() -> "ERROR: FETCH: " + docUri + " - unexpected NETWORK_ERROR");
            throw new RemoteResolutionException(docUri, RemoteResolutionException.Reason.NETWORK_ERROR, "Failed to fetch schema", e);
        }
    }
}
