package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;
import java.util.List;

/// Const schema - validates that a value equals a constant
public record ConstSchema(JsonValue constValue) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    return json.equals(constValue) ?
        ValidationResult.success() :
        ValidationResult.failure(List.of(new ValidationError(path, "Value must equal const value")));
  }
}
