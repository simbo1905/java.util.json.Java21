package json.java21.jtd;

import java.util.Collections;
import java.util.List;

/// Result of validating a JSON instance against a JTD schema.
///
/// When `isValid()` is true the error list is empty.
/// When `isValid()` is false at least one [JtdValidationError] is present.
public record JtdValidationResult(boolean isValid, List<JtdValidationError> errors) {

  private static final JtdValidationResult SUCCESS = new JtdValidationResult(true, List.of());

  public static JtdValidationResult success() {
    return SUCCESS;
  }

  public static JtdValidationResult failure(List<JtdValidationError> errors) {
    return new JtdValidationResult(false, Collections.unmodifiableList(errors));
  }
}
