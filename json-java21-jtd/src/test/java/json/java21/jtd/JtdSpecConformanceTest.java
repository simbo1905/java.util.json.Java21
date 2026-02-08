package json.java21.jtd;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Runs the official json-typedef-spec validation test suite against
/// the interpreter path. This is the authoritative conformance test.
///
/// Test data: `jtd-spec-validation.json` from
/// <https://github.com/jsontypedef/json-typedef-spec/blob/master/tests/validation.json>
class JtdSpecConformanceTest extends JtdTestBase {

  static Stream<Arguments> cases() throws IOException {
    final var raw = JtdSpecConformanceTest.class.getClassLoader()
        .getResourceAsStream("jtd-spec-validation.json");
    assert raw != null : "jtd-spec-validation.json not found on classpath";
    final var jsonText = new String(raw.readAllBytes(), StandardCharsets.UTF_8);
    final var root = Json.parse(jsonText);
    assert root instanceof JsonObject : "expected top-level object";
    final var obj = (JsonObject) root;

    return obj.members().entrySet().stream()
        .map(entry -> Arguments.of(
            entry.getKey(),
            entry.getValue()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void interpreterMatchesSpecSuite(String name, JsonValue caseValue) {
    LOG.info("SPEC: " + name);

    final var caseObj = (JsonObject) caseValue;
    final var schema = caseObj.members().get("schema");
    final var instance = caseObj.members().get("instance");
    final var expectedErrors = (JsonArray) caseObj.members().get("errors");

    final var validator = JtdValidator.compile(schema);
    final var result = validator.validate(instance);

    final var expected = expectedErrors.elements().stream()
        .map(e -> {
          final var errObj = (JsonObject) e;
          final var ip = toJsonPointer((JsonArray) errObj.members().get("instancePath"));
          final var sp = toJsonPointer((JsonArray) errObj.members().get("schemaPath"));
          return new JtdValidationError(ip, sp);
        })
        .sorted(ERR_CMP)
        .toList();

    final var actual = result.errors().stream()
        .sorted(ERR_CMP)
        .toList();

    assertThat(actual)
        .as("errors for: " + name)
        .containsExactlyElementsOf(expected);
  }

  private static String toJsonPointer(JsonArray tokens) {
    if (tokens.elements().isEmpty()) return "";
    final var sb = new StringBuilder();
    for (final var token : tokens.elements()) {
      sb.append('/');
      sb.append(((JsonString) token).string());
    }
    return sb.toString();
  }

  private static final Comparator<JtdValidationError> ERR_CMP =
      Comparator.comparing(JtdValidationError::instancePath)
          .thenComparing(JtdValidationError::schemaPath);
}
