package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;

/// Any schema - accepts all values
public record AnySchema() implements JsonSchema {
  static final io.github.simbo1905.json.schema.AnySchema INSTANCE = new io.github.simbo1905.json.schema.AnySchema();

  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    return ValidationResult.success();
  }
}
