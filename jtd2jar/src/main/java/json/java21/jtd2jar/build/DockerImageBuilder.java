package json.java21.jtd2jar.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Builds the minimal jlink runtime used in the container image.
///
/// The Dockerfile invokes this helper so the container build stays in exec form.
public final class DockerImageBuilder {

  private DockerImageBuilder() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: DockerImageBuilder <jtd2jar.jar> <jre-output-dir>");
    }
    final var jar = Path.of(args[0]);
    final var jreOutput = Path.of(args[1]);
    final var modules = jdepsModules(jar);
    jlink(modules, jreOutput);
  }

  private static String jdepsModules(Path jar) throws IOException, InterruptedException {
    final var command = List.of(
        Path.of(System.getProperty("java.home"), "bin", "jdeps").toString(),
        "--ignore-missing-deps",
        "--recursive",
        "--multi-release",
        "21",
        "--print-module-deps",
        jar.toString());
    final var result = run(command);
    if (result.exitCode() != 0) {
      throw new IllegalStateException("jdeps failed:\n" + result.output());
    }
    return result.output().trim();
  }

  private static void jlink(String modules, Path jreOutput) throws IOException, InterruptedException {
    final var parent = jreOutput.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    final var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "jlink").toString());
    command.add("--add-modules");
    command.add(modules);
    command.add("--strip-debug");
    command.add("--compress=2");
    command.add("--no-header-files");
    command.add("--no-man-pages");
    command.add("--output");
    command.add(jreOutput.toString());
    final var result = run(command);
    if (result.exitCode() != 0) {
      throw new IllegalStateException("jlink failed:\n" + result.output());
    }
  }

  private static Result run(List<String> command) throws IOException, InterruptedException {
    final var process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .start();
    final var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final var exitCode = process.waitFor();
    return new Result(exitCode, output);
  }

  private record Result(int exitCode, String output) {}
}
