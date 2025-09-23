package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/// Number schema with range and multiple constraints
public record NumberSchema(
    BigDecimal minimum,
    BigDecimal maximum,
    BigDecimal multipleOf,
    Boolean exclusiveMinimum,
    Boolean exclusiveMaximum
) implements JsonSchema {

  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    LOG.finest(() -> "NumberSchema.validateAt: " + json + " minimum=" + minimum + " maximum=" + maximum);
    if (!(json instanceof JsonNumber num)) {
      return ValidationResult.failure(List.of(
          new ValidationError(path, "Expected number")
      ));
    }

    BigDecimal value = num.toNumber() instanceof BigDecimal bd ? bd : BigDecimal.valueOf(num.toNumber().doubleValue());
    List<ValidationError> errors = new ArrayList<>();

    // Check minimum
    if (minimum != null) {
      int comparison = value.compareTo(minimum);
      LOG.finest(() -> "NumberSchema.validateAt: value=" + value + " minimum=" + minimum + " comparison=" + comparison);
      if (exclusiveMinimum != null && exclusiveMinimum && comparison <= 0) {
        errors.add(new ValidationError(path, "Below minimum"));
      } else if (comparison < 0) {
        errors.add(new ValidationError(path, "Below minimum"));
      }
    }

    // Check maximum
    if (maximum != null) {
      int comparison = value.compareTo(maximum);
      if (exclusiveMaximum != null && exclusiveMaximum && comparison >= 0) {
        errors.add(new ValidationError(path, "Above maximum"));
      } else if (comparison > 0) {
        errors.add(new ValidationError(path, "Above maximum"));
      }
    }

    // Check multipleOf
    if (multipleOf != null) {
      BigDecimal remainder = value.remainder(multipleOf);
      if (remainder.compareTo(BigDecimal.ZERO) != 0) {
        errors.add(new ValidationError(path, "Not multiple of " + multipleOf));
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }
}
