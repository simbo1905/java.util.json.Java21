package json.java21.jtd;

/// RFC 8927 validation error indicator: a pair of JSON Pointers.
///
/// - `instancePath` points to the value in the JSON document that failed.
/// - `schemaPath`   points to the keyword in the JTD schema that caused the failure.
///
/// Both paths follow RFC 6901 (JSON Pointer) notation.
public record JtdValidationError(String instancePath, String schemaPath) {

  @Override
  public String toString() {
    return "{instancePath=\"" + instancePath + "\", schemaPath=\"" + schemaPath + "\"}";
  }
}
