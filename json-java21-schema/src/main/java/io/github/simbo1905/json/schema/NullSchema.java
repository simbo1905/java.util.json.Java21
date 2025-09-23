package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonNull;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;
import java.util.List;

/// Null schema - always valid for null values
public record NullSchema() implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    if (!(json instanceof JsonNull)) {
      return ValidationResult.failure(List.of(
          new ValidationError(path, "Expected null")
      ));
    }
    return ValidationResult.success();
  }
}
