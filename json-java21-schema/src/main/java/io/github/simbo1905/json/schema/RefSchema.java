package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.net.URI;
import java.util.Deque;
import java.util.List;

/// Reference schema for JSON Schema $ref
public record RefSchema(RefToken refToken, ResolverContext resolverContext) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    LOG.finest(() -> "RefSchema.validateAt: " + refToken + " at path: " + path + " with json=" + json);
    LOG.fine(() -> "RefSchema.validateAt: Using resolver context with roots.size=" + resolverContext.roots().size() +
        " localPointerIndex.size=" + resolverContext.localPointerIndex().size());

    // Add detailed logging for remote ref resolution
    if (refToken instanceof RefToken.RemoteRef(URI baseUri, URI targetUri)) {
      LOG.finest(() -> "RefSchema.validateAt: Attempting to resolve RemoteRef: baseUri=" + baseUri + ", targetUri=" + targetUri);
      LOG.finest(() -> "RefSchema.validateAt: Available roots in context: " + resolverContext.roots().keySet());
    }

    JsonSchema target = resolverContext.resolve(refToken);
    LOG.finest(() -> "RefSchema.validateAt: Resolved target=" + target);
    if (target == null) {
      return ValidationResult.failure(List.of(new ValidationError(path, "Unresolvable $ref: " + refToken)));
    }
    // Stay on the SAME traversal stack (uniform non-recursive execution).
    stack.push(new ValidationFrame(path, target, json));
    return ValidationResult.success();
  }

  @Override
  public String toString() {
    return "RefSchema[" + refToken + "]";
  }
}
