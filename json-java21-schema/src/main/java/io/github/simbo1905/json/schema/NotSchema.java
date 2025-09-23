package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;
import java.util.List;

/// Not composition - inverts the validation result of the inner schema
public record NotSchema(JsonSchema schema) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    ValidationResult result = schema.validate(json);
    return result.valid() ?
        ValidationResult.failure(List.of(new ValidationError(path, "Schema should not match"))) :
        ValidationResult.success();
  }
}
