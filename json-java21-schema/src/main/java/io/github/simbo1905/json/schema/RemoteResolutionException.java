package io.github.simbo1905.json.schema;

import java.util.Objects;

/// Exception signalling remote resolution failures with typed reasons
public final class RemoteResolutionException extends RuntimeException {
  private final java.net.URI uri;
  private final Reason reason;

  RemoteResolutionException(java.net.URI uri, Reason reason, String message) {
    super(message);
    this.uri = Objects.requireNonNull(uri, "uri");
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  RemoteResolutionException(java.net.URI uri, Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.uri = Objects.requireNonNull(uri, "uri");
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  public java.net.URI uri() {
    return uri;
  }

  public Reason reason() {
    return reason;
  }

  enum Reason {
    NETWORK_ERROR,
    POLICY_DENIED,
    NOT_FOUND,
    POINTER_MISSING,
    PAYLOAD_TOO_LARGE,
    TIMEOUT
  }
}
