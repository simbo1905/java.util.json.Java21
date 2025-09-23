package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.Deque;

/// If/Then/Else conditional schema
public record ConditionalSchema(JsonSchema ifSchema, JsonSchema thenSchema,
                                JsonSchema elseSchema) implements JsonSchema {
  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    // Step 1 - evaluate IF condition (still needs direct validation)
    ValidationResult ifResult = ifSchema.validate(json);

    // Step 2 - choose branch
    JsonSchema branch = ifResult.valid() ? thenSchema : elseSchema;

    LOG.finer(() -> String.format(
        "Conditional path=%s ifValid=%b branch=%s",
        path, ifResult.valid(),
        branch == null ? "none" : (ifResult.valid() ? "then" : "else")));

    // Step 3 - if there's a branch, push it onto the stack for later evaluation
    if (branch == null) {
      return ValidationResult.success();      // no branch → accept
    }

    // NEW: push branch onto SAME stack instead of direct call
    stack.push(new ValidationFrame(path, branch, json));
    return ValidationResult.success();          // real result emerges later
  }
}
