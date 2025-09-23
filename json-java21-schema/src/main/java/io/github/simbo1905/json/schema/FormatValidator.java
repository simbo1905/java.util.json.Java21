package io.github.simbo1905.json.schema;

/// Format validator interface for string format validation
sealed public interface FormatValidator permits Format {
  /// Test if the string value matches the format
  /// @param s the string to test
  /// @return true if the string matches the format, false otherwise
  boolean test(String s);
}
