package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/// String schema with length, pattern, and enum constraints
public record StringSchema(
    Integer minLength,
    Integer maxLength,
    Pattern pattern,
    FormatValidator formatValidator,
    boolean assertFormats
) implements JsonSchema {

  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    if (!(json instanceof JsonString str)) {
      return ValidationResult.failure(List.of(
          new ValidationError(path, "Expected string")
      ));
    }

    String value = str.value();
    List<ValidationError> errors = new ArrayList<>();

    // Check length constraints
    int length = value.length();
    if (minLength != null && length < minLength) {
      errors.add(new ValidationError(path, "String too short: expected at least " + minLength + " characters"));
    }
    if (maxLength != null && length > maxLength) {
      errors.add(new ValidationError(path, "String too long: expected at most " + maxLength + " characters"));
    }

    // Check pattern (unanchored matching - uses find() instead of matches())
    if (pattern != null && !pattern.matcher(value).find()) {
      errors.add(new ValidationError(path, "Pattern mismatch"));
    }

    // Check format validation (only when format assertion is enabled)
    if (formatValidator != null && assertFormats) {
      if (!formatValidator.test(value)) {
        String formatName = formatValidator instanceof Format format ? format.name().toLowerCase().replace("_", "-") : "unknown";
        errors.add(new ValidationError(path, "Invalid format '" + formatName + "'"));
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }
}
