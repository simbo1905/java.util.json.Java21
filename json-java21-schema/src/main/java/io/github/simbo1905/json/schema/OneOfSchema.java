package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/// OneOf composition - must satisfy exactly one schema
public record OneOfSchema(List<JsonSchema> schemas) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    int validCount = 0;
    List<ValidationError> minimalErrors = null;

    for (JsonSchema schema : schemas) {
      // Create a separate validation stack for this branch
      Deque<ValidationFrame> branchStack = new ArrayDeque<>();
      List<ValidationError> branchErrors = new ArrayList<>();

      LOG.finest(() -> "one of BRANCH START: " + schema.getClass().getSimpleName());
      branchStack.push(new ValidationFrame(path, schema, json));

      while (!branchStack.isEmpty()) {
        ValidationFrame frame = branchStack.pop();
        ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), branchStack);
        if (!result.valid()) {
          branchErrors.addAll(result.errors());
        }
      }

      if (branchErrors.isEmpty()) {
        validCount++;
      } else {
        // Track minimal error set for zero-valid case
        // Prefer errors that don't start with "Expected" (type mismatches) if possible
        // In case of ties, prefer later branches (they tend to be more specific)
        if (minimalErrors == null ||
            (branchErrors.size() < minimalErrors.size()) ||
            (branchErrors.size() == minimalErrors.size() &&
                hasBetterErrorType(branchErrors, minimalErrors))) {
          minimalErrors = branchErrors;
        }
      }
      LOG.finest(() -> "one of BRANCH END: " + branchErrors.size() + " errors, valid=" + branchErrors.isEmpty());
    }

    // Exactly one must be valid
    if (validCount == 1) {
      return ValidationResult.success();
    } else if (validCount == 0) {
      // Zero valid - return minimal error set
      return ValidationResult.failure(minimalErrors != null ? minimalErrors : List.of());
    } else {
      // Multiple valid - single error
      return ValidationResult.failure(List.of(
          new ValidationError(path, "oneOf: multiple schemas matched (" + validCount + ")")
      ));
    }
  }

  private boolean hasBetterErrorType(List<ValidationError> newErrors, List<ValidationError> currentErrors) {
    // Prefer errors that don't start with "Expected" (type mismatches)
    boolean newHasTypeMismatch = newErrors.stream().anyMatch(e -> e.message().startsWith("Expected"));
    boolean currentHasTypeMismatch = currentErrors.stream().anyMatch(e -> e.message().startsWith("Expected"));

    // If new has type mismatch and current doesn't, current is better (keep current)
    return !newHasTypeMismatch || currentHasTypeMismatch;

    // If current has type mismatch and new doesn't, new is better (replace current)

    // If both have type mismatches or both don't, prefer later branches
    // This is a simple heuristic
  }
}
