package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;
import java.util.List;

/// Root reference schema that refers back to the root schema
public record RootRef(java.util.function.Supplier<JsonSchema> rootSupplier) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    LOG.finest(() -> "RootRef.validateAt at path: " + path);
    JsonSchema root = rootSupplier.get();
    if (root == null) {
      // Shouldn't happen once compilation finishes; be conservative and fail closed:
      return ValidationResult.failure(List.of(new ValidationError(path, "Root schema not available")));
    }
    // Stay within the SAME stack to preserve traversal semantics (matches AllOf/Conditional).
    stack.push(new ValidationFrame(path, root, json));
    return ValidationResult.success();
  }
}
