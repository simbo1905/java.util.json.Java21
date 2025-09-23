package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonBoolean;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;
import java.util.List;

/// Boolean schema - validates boolean values
public record BooleanSchema() implements JsonSchema {
  /// Singleton instances for boolean sub-schema handling
  static final io.github.simbo1905.json.schema.BooleanSchema TRUE = new io.github.simbo1905.json.schema.BooleanSchema();
  static final io.github.simbo1905.json.schema.BooleanSchema FALSE = new io.github.simbo1905.json.schema.BooleanSchema();

  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    // For boolean subschemas, FALSE always fails, TRUE always passes
    if (this == FALSE) {
      return ValidationResult.failure(List.of(
          new ValidationError(path, "Schema should not match")
      ));
    }
    if (this == TRUE) {
      return ValidationResult.success();
    }
    // Regular boolean validation for normal boolean schemas
    if (!(json instanceof JsonBoolean)) {
      return ValidationResult.failure(List.of(
          new ValidationError(path, "Expected boolean")
      ));
    }
    return ValidationResult.success();
  }
}
