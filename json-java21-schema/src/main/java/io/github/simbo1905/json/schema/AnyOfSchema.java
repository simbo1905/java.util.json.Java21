package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/// AnyOf composition - must satisfy at least one schema
public record AnyOfSchema(List<JsonSchema> schemas) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    List<ValidationError> collected = new ArrayList<>();
    boolean anyValid = false;

    for (JsonSchema schema : schemas) {
      // Create a separate validation stack for this branch
      Deque<ValidationFrame> branchStack = new ArrayDeque<>();
      List<ValidationError> branchErrors = new ArrayList<>();

      LOG.finest(() -> "BRANCH START: " + schema.getClass().getSimpleName());
      branchStack.push(new ValidationFrame(path, schema, json));

      while (!branchStack.isEmpty()) {
        ValidationFrame frame = branchStack.pop();
        ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), branchStack);
        if (!result.valid()) {
          branchErrors.addAll(result.errors());
        }
      }

      if (branchErrors.isEmpty()) {
        anyValid = true;
        break;
      }
      collected.addAll(branchErrors);
      LOG.finest(() -> "BRANCH END: " + branchErrors.size() + " errors");
    }

    return anyValid ? ValidationResult.success() : ValidationResult.failure(collected);
  }
}
