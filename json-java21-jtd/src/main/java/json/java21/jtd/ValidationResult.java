package json.java21.jtd;

import java.util.Collections;
import java.util.List;

/// Result of JTD schema validation
/// Immutable result containing validation status and any errors
public record ValidationResult(boolean isValid, List<ValidationError> errors) {
  
  /// Singleton success result - no errors
  private static final ValidationResult SUCCESS = new ValidationResult(true, Collections.emptyList());
  
  /// Creates a successful validation result
  public static ValidationResult success() {
    return SUCCESS;
  }
  
  /// Creates a failed validation result with the given errors
  public static ValidationResult failure(List<ValidationError> errors) {
    return new ValidationResult(false, Collections.unmodifiableList(errors));
  }
  
  /// Creates a failed validation result with a single error
  public static ValidationResult failure(ValidationError error) {
    return failure(List.of(error));
  }
}