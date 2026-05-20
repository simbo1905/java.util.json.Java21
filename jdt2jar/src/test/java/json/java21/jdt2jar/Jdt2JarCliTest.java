package json.java21.jdt2jar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

class Jdt2JarCliTest extends Jdt2JarTestBase {

  @TempDir
  Path tempDir;

  @Test
  void compilesStandaloneValidatorJar() throws Exception {
    final var schema = tempDir.resolve("user.jtd.json");
    Files.writeString(schema, """
        {"properties":{"name":{"type":"string"}}}
        """, StandardCharsets.UTF_8);
    final var output = tempDir.resolve("user-validator.jar");
    final var payload = tempDir.resolve("payload.json");
    Files.writeString(payload, """
        {"name":"Alice"}
        """, StandardCharsets.UTF_8);

    assertThat(Jdt2Jar.run(new String[] {schema.toString(), "--output", output.toString(), "--main"})).isZero();

    assertThat(output).exists();
    try (final var jar = new JarFile(output.toFile())) {
      assertThat(jar.getEntry("jtd/generated/SchemaValidator.class")).isNotNull();
      assertThat(jar.getEntry("jtd/schema.json")).isNotNull();
      assertThat(jar.getEntry("jdt2jar.properties")).isNotNull();
      assertThat(jar.getEntry("json/java21/jdt2jar/runtime/ValidatorMain.class")).isNotNull();
      assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class"))
          .isEqualTo("json.java21.jdt2jar.runtime.ValidatorMain");
    }

    final var validResult = runJavaJar(output, "--validate", payload.toString());
    assertThat(validResult.exitCode()).isZero();
    assertThat(validResult.output()).contains("valid");

    Files.writeString(payload, """
        {"name":1}
        """, StandardCharsets.UTF_8);
    final var invalidResult = runJavaJar(output, "--validate", payload.toString(), "--format", "json");
    assertThat(invalidResult.exitCode()).isEqualTo(1);
    assertThat(invalidResult.output()).contains("\"instancePath\"");
    assertThat(invalidResult.output()).contains("\"schemaPath\"");
  }

  @Test
  void writesCompanionSourceWhenRequested() throws Exception {
    final var schema = tempDir.resolve("widget.jtd.json");
    Files.writeString(schema, """
        {"type":"string"}
        """, StandardCharsets.UTF_8);
    final var output = tempDir.resolve("widget-validator.jar");

    assertThat(Jdt2Jar.run(new String[] {
        schema.toString(),
        "--output", output.toString(),
        "--package", "demo.validator",
        "--class", "WidgetValidator",
        "--include-sources"
    })).isZero();

    final var source = tempDir.resolve("widget-validator.java");
    assertThat(source).exists();
    assertThat(Files.readString(source, StandardCharsets.UTF_8))
        .contains("package demo.validator;")
        .contains("class WidgetValidator")
        .contains("ValidatorMain.validate");
  }

  private static RunResult runJavaJar(Path jar, String... args) throws IOException, InterruptedException {
    final var javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
    final var builder = new ProcessBuilder();
    final var command = new java.util.ArrayList<String>();
    command.add(javaBin.toString());
    command.add("-jar");
    command.add(jar.toString());
    command.addAll(java.util.List.of(args));
    builder.command(command);
    builder.redirectErrorStream(true);
    final var process = builder.start();
    final var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final var exitCode = process.waitFor();
    return new RunResult(exitCode, output);
  }

  private record RunResult(int exitCode, String output) {}
}
