package json.java21.jtd;

import jdk.incubator.java.util.json.JsonValue;

import java.util.Objects;
import java.util.logging.Logger;

/// Functional interface for validating a JSON instance against a compiled JTD schema
/// using the stack-machine interpreter.
///
/// Works on any JDK 21+ runtime with zero extra dependencies.
/// For bytecode-generated validators (JDK 24+ build), see
/// {@code json.java21.jtd.codegen.JtdValidator} in the {@code json-java21-jtd-codegen} module.
///
/// Obtain an instance via:
/// - [#compileInterpreter(JsonValue)] -- interpreter path, always available.
@FunctionalInterface
public interface JtdValidator {

  Logger LOG = Logger.getLogger(JtdValidator.class.getName());

  /// Validates an instance against the compiled schema.
  ///
  /// @param instance the JSON value to validate
  /// @return the validation result with RFC 8927 error pairs
  JtdValidationResult validate(JsonValue instance);

  /// Compiles a JTD schema into a reusable validator using the stack-machine
  /// interpreter. Works on any JDK 21+ runtime with zero extra dependencies.
  ///
  /// @param schema the JTD schema as a parsed [JsonValue]
  /// @return a reusable [JtdValidator]
  /// @throws IllegalArgumentException if the schema is invalid per RFC 8927
  static JtdValidator compileInterpreter(JsonValue schema) {
    Objects.requireNonNull(schema, "schema must not be null");
    final var jtd = new Jtd();
    final var compiled = jtd.compileToSchema(schema);
    return new InterpreterValidator(compiled, jtd, schema.toString());
  }

  /// Compiles a JTD schema into a reusable validator using the stack-machine
  /// interpreter.
  ///
  /// @param schema the JTD schema as a parsed [JsonValue]
  /// @return a reusable [JtdValidator]
  /// @throws IllegalArgumentException if the schema is invalid per RFC 8927
  @Deprecated(since = "1.0.0", forRemoval = true)
  static JtdValidator compile(JsonValue schema) {
    return compileInterpreter(schema);
  }
}
