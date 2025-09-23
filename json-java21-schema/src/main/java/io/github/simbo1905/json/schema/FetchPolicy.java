package io.github.simbo1905.json.schema;

import java.util.Objects;
import java.util.Set;

/// Fetch policy settings controlling network guardrails
public record FetchPolicy(
    Set<String> allowedSchemes,
    long maxDocumentBytes,
    long maxTotalBytes,
    java.time.Duration timeout,
    int maxRedirects,
    int maxDocuments,
    int maxDepth
) {
  public FetchPolicy {
    Objects.requireNonNull(allowedSchemes, "allowedSchemes");
    Objects.requireNonNull(timeout, "timeout");
    if (allowedSchemes.isEmpty()) {
      throw new IllegalArgumentException("allowedSchemes must not be empty");
    }
    if (maxDocumentBytes <= 0L) {
      throw new IllegalArgumentException("maxDocumentBytes must be > 0");
    }
    if (maxTotalBytes <= 0L) {
      throw new IllegalArgumentException("maxTotalBytes must be > 0");
    }
    if (maxRedirects < 0) {
      throw new IllegalArgumentException("maxRedirects must be >= 0");
    }
    if (maxDocuments <= 0) {
      throw new IllegalArgumentException("maxDocuments must be > 0");
    }
    if (maxDepth <= 0) {
      throw new IllegalArgumentException("maxDepth must be > 0");
    }
  }

  static FetchPolicy defaults() {
    return new FetchPolicy(Set.of("http", "https", "file"), 1_048_576L, 8_388_608L, java.time.Duration.ofSeconds(5), 3, 64, 64);
  }

  FetchPolicy withAllowedSchemes(Set<String> schemes) {
    Objects.requireNonNull(schemes, "schemes");
    return new FetchPolicy(Set.copyOf(schemes), maxDocumentBytes, maxTotalBytes, timeout, maxRedirects, maxDocuments, maxDepth);
  }

  FetchPolicy withMaxDocumentBytes() {
    return new FetchPolicy(allowedSchemes, 10, maxTotalBytes, timeout, maxRedirects, maxDocuments, maxDepth);
  }

  FetchPolicy withTimeout(java.time.Duration newTimeout) {
    Objects.requireNonNull(newTimeout, "newTimeout");
    return new FetchPolicy(allowedSchemes, maxDocumentBytes, maxTotalBytes, newTimeout, maxRedirects, maxDocuments, maxDepth);
  }
}
