package json.java21.jtd;

/// Represents a single validation error
/// Contains error message describing what went wrong
public record ValidationError(String message) {
  
  /// Creates a validation error with the given message
  public ValidationError {
    if (message == null || message.isEmpty()) {
      throw new IllegalArgumentException("Error message cannot be null or empty");
    }
  }
}