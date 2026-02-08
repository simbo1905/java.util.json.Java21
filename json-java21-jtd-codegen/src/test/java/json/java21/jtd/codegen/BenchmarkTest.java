package json.java21.jtd.codegen;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import json.java21.jtd.JtdValidator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// Benchmarks codegen vs interpreter paths across schemas of increasing
/// complexity. Reports classfile size and throughput (validations/sec).
///
/// Not a microbenchmark framework -- results are indicative, not definitive.
/// Run with `-Djava.util.logging.ConsoleHandler.level=INFO` to see output.
class BenchmarkTest extends CodegenTestBase {

  private static final int WARMUP_ITERATIONS = 50_000;
  private static final int MEASURED_ITERATIONS = 200_000;

  record Schema(String name, String schemaJson, String validJson, String invalidJson) {}

  static Schema[] schemas() {
    return new Schema[] {
        new Schema("simple-type",
            """
            {"type":"string"}""",
            "\"hello\"",
            "42"),

        new Schema("enum-5",
            """
            {"enum":["alpha","beta","gamma","delta","epsilon"]}""",
            "\"gamma\"",
            "\"zeta\""),

        new Schema("nullable-int",
            """
            {"type":"uint8","nullable":true}""",
            "null",
            "\"nope\""),

        new Schema("properties-3",
            """
            {"properties":{"name":{"type":"string"},"age":{"type":"uint8"},"active":{"type":"boolean"}}}""",
            """
            {"name":"Alice","age":30,"active":true}""",
            """
            {"name":"Alice","age":"old","active":"yes"}"""),

        new Schema("props-with-optional",
            """
            {"properties":{"name":{"type":"string"}},"optionalProperties":{"email":{"type":"string"},"phone":{"type":"string"}}}""",
            """
            {"name":"Alice","email":"a@b.com"}""",
            """
            {"name":42,"email":123}"""),

        new Schema("elements-of-type",
            """
            {"elements":{"type":"string"}}""",
            """
            ["a","b","c","d","e"]""",
            """
            ["a",1,"b",2,"c"]"""),

        new Schema("values-of-type",
            """
            {"values":{"type":"uint32"}}""",
            """
            {"x":1,"y":2,"z":3}""",
            """
            {"x":1,"y":"bad","z":-1}"""),

        new Schema("nested-elements-of-props",
            """
            {"elements":{"properties":{"id":{"type":"uint32"},"label":{"type":"string"}}}}""",
            """
            [{"id":1,"label":"a"},{"id":2,"label":"b"},{"id":3,"label":"c"}]""",
            """
            [{"id":"x","label":42},{"id":2},{"label":"c","extra":true}]"""),

        new Schema("discriminator-2-variants",
            """
            {"discriminator":"kind","mapping":{"dog":{"properties":{"breed":{"type":"string"}}},"cat":{"properties":{"indoor":{"type":"boolean"}}}}}""",
            """
            {"kind":"dog","breed":"poodle"}""",
            """
            {"kind":"dog","breed":42}"""),

        new Schema("ref-with-definitions",
            """
            {"definitions":{"addr":{"properties":{"street":{"type":"string"},"zip":{"type":"string"}}}},"properties":{"home":{"ref":"addr"},"work":{"ref":"addr"}}}""",
            """
            {"home":{"street":"1 Main","zip":"12345"},"work":{"street":"2 Oak","zip":"67890"}}""",
            """
            {"home":{"street":1,"zip":2},"work":"invalid"}"""),

        new Schema("deep-nesting",
            """
            {"elements":{"values":{"properties":{"tags":{"elements":{"type":"string"}},"count":{"type":"uint32"}}}}}""",
            """
            [{"a":{"tags":["x","y"],"count":5}},{"b":{"tags":[],"count":0}}]""",
            """
            [{"a":{"tags":["x",1],"count":-1}}]"""),

        new Schema("worked-example-rfc8927",
            """
            {"properties":{"name":{"type":"string"},"age":{"type":"uint8"},"phones":{"elements":{"properties":{"type":{"enum":["home","work","mobile"]},"number":{"type":"string"}}}},"tags":{"elements":{"type":"string"}}},"optionalProperties":{"email":{"type":"string"},"address":{"properties":{"street":{"type":"string"},"city":{"type":"string"},"zip":{"type":"string"}}}}}""",
            """
            {"name":"Alice","age":30,"phones":[{"type":"home","number":"555-1234"},{"type":"work","number":"555-5678"}],"tags":["vip","active"],"email":"alice@example.com","address":{"street":"1 Main","city":"NY","zip":"10001"}}""",
            """
            {"name":42,"age":300,"phones":[{"type":"fax","number":123}],"tags":[1,2],"email":false,"address":{"street":1,"city":2,"zip":3},"extra":"bad"}""")
    };
  }

  @Test
  void benchmarkAll() {
    LOG.info("========== JTD Benchmark: Codegen vs Interpreter ==========");
    LOG.info(String.format("Warmup: %,d iterations, Measured: %,d iterations",
        WARMUP_ITERATIONS, MEASURED_ITERATIONS));
    LOG.info("");

    final var results = new LinkedHashMap<String, BenchResult>();

    for (final var s : schemas()) {
      LOG.info("--- Schema: " + s.name + " ---");
      LOG.info("  JSON: " + s.schemaJson.substring(0, Math.min(80, s.schemaJson.length())) + "...");

      final var schema = Json.parse(s.schemaJson);
      final var validDoc = Json.parse(s.validJson);
      final var invalidDoc = Json.parse(s.invalidJson);

      final var codegenResult = JtdCodegen.compileWithStats(schema);
      final var codegen = codegenResult.validator();
      final var classfileBytes = codegenResult.classfileBytes();
      final var interpreter = JtdValidator.compile(schema);

      assertThat(codegen.validate(validDoc).isValid()).isTrue();
      assertThat(codegen.validate(invalidDoc).isValid()).isFalse();
      assertThat(interpreter.validate(validDoc).isValid()).isTrue();
      assertThat(interpreter.validate(invalidDoc).isValid()).isFalse();

      LOG.info("  Classfile size: " + classfileBytes + " bytes");
      LOG.info("  Schema JSON size: " + s.schemaJson.length() + " chars");

      final var codegenValidNs = measure(codegen, validDoc);
      final var codegenInvalidNs = measure(codegen, invalidDoc);
      final var interpValidNs = measure(interpreter, validDoc);
      final var interpInvalidNs = measure(interpreter, invalidDoc);

      final var speedupValid = (double) interpValidNs / codegenValidNs;
      final var speedupInvalid = (double) interpInvalidNs / codegenInvalidNs;

      LOG.info(String.format("  Valid doc:   codegen %,d ns/op, interp %,d ns/op  (%.1fx)",
          codegenValidNs, interpValidNs, speedupValid));
      LOG.info(String.format("  Invalid doc: codegen %,d ns/op, interp %,d ns/op  (%.1fx)",
          codegenInvalidNs, interpInvalidNs, speedupInvalid));
      LOG.info("");

      results.put(s.name, new BenchResult(classfileBytes, s.schemaJson.length(),
          codegenValidNs, interpValidNs, codegenInvalidNs, interpInvalidNs));
    }

    LOG.info("========== Summary ==========");
    LOG.info(String.format("%-30s %8s %8s | %10s %10s %6s | %10s %10s %6s",
        "Schema", "Class B", "JSON Ch",
        "CG val ns", "Int val ns", "x",
        "CG inv ns", "Int inv ns", "x"));
    LOG.info("-".repeat(120));

    for (final var entry : results.entrySet()) {
      final var r = entry.getValue();
      LOG.info(String.format("%-30s %,8d %,8d | %,10d %,10d %5.1fx | %,10d %,10d %5.1fx",
          entry.getKey(), r.classfileBytes, r.schemaJsonChars,
          r.codegenValidNs, r.interpValidNs, (double) r.interpValidNs / r.codegenValidNs,
          r.codegenInvalidNs, r.interpInvalidNs, (double) r.interpInvalidNs / r.codegenInvalidNs));
    }

    LOG.info("");
    final var avgSpeedupValid = results.values().stream()
        .mapToDouble(r -> (double) r.interpValidNs / r.codegenValidNs)
        .average().orElse(0);
    final var avgSpeedupInvalid = results.values().stream()
        .mapToDouble(r -> (double) r.interpInvalidNs / r.codegenInvalidNs)
        .average().orElse(0);
    LOG.info(String.format("Average speedup: valid=%.1fx, invalid=%.1fx", avgSpeedupValid, avgSpeedupInvalid));
  }

  private long measure(JtdValidator validator, JsonValue doc) {
    IntStream.range(0, WARMUP_ITERATIONS).forEach(_ -> validator.validate(doc));

    final var start = System.nanoTime();
    IntStream.range(0, MEASURED_ITERATIONS).forEach(_ -> validator.validate(doc));
    final var elapsed = System.nanoTime() - start;

    return elapsed / MEASURED_ITERATIONS;
  }

  record BenchResult(int classfileBytes, int schemaJsonChars,
                     long codegenValidNs, long interpValidNs,
                     long codegenInvalidNs, long interpInvalidNs) {}
}
