package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

import static io.github.simbo1905.json.schema.JsonSchema.LOG;

/// Local file fetcher that enforces a mandatory jail root directory
record FileFetcher(Path jailRoot) implements JsonSchema.RemoteFetcher {
  FileFetcher(Path jailRoot) {
    this.jailRoot = Objects.requireNonNull(jailRoot, "jailRoot").toAbsolutePath().normalize();
    LOG.info(() -> "FileFetcher jailRoot=" + this.jailRoot);
  }

  @Override
  public String scheme() {
    return "file";
  }

  @Override
  public FetchResult fetch(URI uri, FetchPolicy policy) {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(policy, "policy");

    if (!"file".equalsIgnoreCase(uri.getScheme())) {
      LOG.severe(() -> "ERROR: FileFetcher received non-file URI " + uri);
      throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.POLICY_DENIED,
          "FileFetcher only handles file:// URIs");
    }

    Path target = toPath(uri).normalize();
    if (!target.startsWith(jailRoot)) {
      LOG.fine(() -> "FETCH DENIED outside jail: uri=" + uri + " path=" + target + " jailRoot=" + jailRoot);
      throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.POLICY_DENIED,
          "Outside jail: " + target);
    }

    if (!Files.exists(target) || !Files.isRegularFile(target)) {
      LOG.finer(() -> "NOT_FOUND: " + target);
      throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.NOT_FOUND,
          "No such file: " + target);
    }

    try {
      long size = Files.size(target);
      if (size > policy.maxDocumentBytes()) {
        throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.PAYLOAD_TOO_LARGE,
            "File exceeds maxDocumentBytes: " + size);
      }
      byte[] bytes = Files.readAllBytes(target);
      long actual = bytes.length;
      if (actual != size && actual > policy.maxDocumentBytes()) {
        throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.PAYLOAD_TOO_LARGE,
            "File exceeds maxDocumentBytes after read: " + actual);
      }
      JsonValue doc = Json.parse(new String(bytes, StandardCharsets.UTF_8));
      return new FetchResult(doc, actual, Optional.empty());
    } catch (IOException e) {
      LOG.log(Level.SEVERE, () -> "ERROR: IO reading file " + target);
      throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.NETWORK_ERROR,
          "IO reading file: " + e.getMessage());
    }
  }

  private static Path toPath(URI uri) {
    // java.nio handles file URIs via Paths.get(URI)
    return Path.of(uri);
  }
}
