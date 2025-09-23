package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;
import java.util.List;
import java.util.Set;

/// Enum schema - validates that a value is in a set of allowed values
public record EnumSchema(JsonSchema baseSchema, Set<JsonValue> allowedValues) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    // First validate against base schema
    ValidationResult baseResult = baseSchema.validateAt(path, json, stack);
    if (!baseResult.valid()) {
      return baseResult;
    }

    // Then check if value is in enum
    if (!allowedValues.contains(json)) {
      return ValidationResult.failure(List.of(new ValidationError(path, "Not in enum")));
    }

    return ValidationResult.success();
  }
}
