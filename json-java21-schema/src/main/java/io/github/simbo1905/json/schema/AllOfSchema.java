package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;
import java.util.List;

/// AllOf composition - must satisfy all schemas
public record AllOfSchema(List<JsonSchema> schemas) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    // Push all subschemas onto the stack for validation
    for (JsonSchema schema : schemas) {
      stack.push(new ValidationFrame(path, schema, json));
    }
    return ValidationResult.success(); // Actual results emerge from stack processing
  }
}
