package json.java21.jdt2jar.runtime;

import jdk.incubator.java.util.json.Json;
import jdk.incubator.java.util.json.JsonObject;
import jdk.incubator.java.util.json.JsonParseException;
import jdk.incubator.java.util.json.JsonString;
import jdk.incubator.java.util.json.JsonValue;
import json.java21.jtd.JtdValidationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Properties;

/// Runtime launcher for compiled validator JARs.
///
/// The CLI jar copies this class into the generated output when `--main` is
/// requested and points the manifest `Main-Class` at it.
public final class ValidatorMain {
  private static final String CONFIG_RESOURCE = "jdt2jar.properties";

  private ValidatorMain() {}

  public static void main(String[] args) throws Exception {
    System.exit(run(args));
  }

  public static int run(String[] args) {
    try {
      final var config = loadConfig();
      final var validator = instantiate(config.validatorClass(), readResourceString(config.schemaEntry()));
      return run(validator, args);
    } catch (IllegalArgumentException e) {
      System.err.println("ERROR: " + e.getMessage());
      return 2;
    } catch (IOException e) {
      System.err.println("ERROR: " + e.getMessage());
      return 2;
    } catch (RuntimeException e) {
      System.err.println("ERROR: " + e.getMessage());
      return 1;
    }
  }

  public static int run(json.java21.jtd.codegen.JtdValidator validator, String[] args) throws IOException {
    final CliOptions options;
    try {
      options = parseArgs(args);
    } catch (IllegalArgumentException e) {
      System.err.println("ERROR: " + e.getMessage());
      printUsage();
      return 2;
    }
    if (options.help()) {
      printUsage();
      return 0;
    }
    if (options.input() == null) {
      printUsage();
      return 2;
    }

    final JsonValue instance;
    try {
      instance = Json.parse(Files.readString(options.input(), StandardCharsets.UTF_8));
    } catch (JsonParseException e) {
      System.err.println("ERROR: Failed to parse payload: " + e.getMessage());
      return 2;
    }

    final var result = validator.validate(instance);
    if (options.json()) {
      System.out.println(toJson(result).toString());
    } else if (result.isValid()) {
      System.out.println("valid");
    } else {
      for (final var error : result.errors()) {
        System.out.println(error.instancePath() + ": " + error.schemaPath());
      }
    }
    return result.isValid() ? 0 : 1;
  }

  public static JtdValidationResult validate(String schemaJson, JsonValue instance) {
    return json.java21.jtd.JtdValidator.compileInterpreter(Json.parse(schemaJson)).validate(instance);
  }

  private static RuntimeConfig loadConfig() throws IOException {
    final var props = new Properties();
    try (final var in = ValidatorMain.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
      if (in != null) {
        props.load(in);
      }
    }
    return new RuntimeConfig(
        props.getProperty("validatorClass", "jtd.generated.SchemaValidator"),
        props.getProperty("schemaEntry", "jtd/schema.json"));
  }

  private static json.java21.jtd.codegen.JtdValidator instantiate(String validatorClassName, String schemaJson) {
    try {
      final var clazz = Class.forName(validatorClassName);
      final var ctor = clazz.getConstructor(String.class);
      return (json.java21.jtd.codegen.JtdValidator) ctor.newInstance(schemaJson);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not instantiate validator class: " + validatorClassName, e);
    }
  }

  private static String readResourceString(String name) throws IOException {
    try (final var in = ValidatorMain.class.getClassLoader().getResourceAsStream(name)) {
      if (in == null) {
        throw new IOException("Missing resource: " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static JsonObject toJson(JtdValidationResult result) {
    return JsonObject.of(java.util.Map.of(
        "valid", jdk.incubator.java.util.json.JsonBoolean.of(result.isValid()),
        "errors", jdk.incubator.java.util.json.JsonArray.of(result.errors().stream()
            .map(error -> JsonObject.of(java.util.Map.of(
                "instancePath", JsonString.of(error.instancePath()),
                "schemaPath", JsonString.of(error.schemaPath()))))
            .toList())));
  }

  private static CliOptions parseArgs(String[] args) {
    Path input = null;
    boolean json = false;
    boolean help = false;

    final var remaining = new ArrayDeque<>(java.util.List.of(args));
    while (!remaining.isEmpty()) {
      final var arg = remaining.removeFirst();
      switch (arg) {
        case "--help" -> help = true;
        case "--validate" -> input = Path.of(requireValue(remaining, "--validate"));
        case "--format" -> {
          final var value = requireValue(remaining, "--format");
          if ("json".equalsIgnoreCase(value)) {
            json = true;
          } else {
            throw new IllegalArgumentException("Unsupported format: " + value);
          }
        }
        default -> throw new IllegalArgumentException("Unknown option: " + arg);
      }
    }

    return new CliOptions(input, json, help);
  }

  private static String requireValue(ArrayDeque<String> args, String option) {
    if (args.isEmpty()) {
      throw new IllegalArgumentException("Missing value for " + option);
    }
    return args.removeFirst();
  }

  private static void printUsage() {
    System.out.println("""
        usage: java -jar validator.jar --validate <payload.json> [--format json]

        Options:
          --validate <path>  Validate the JSON payload at the given path
          --format json      Emit RFC 8927 error pairs as JSON
          --help             Show help
        """);
  }

  private record CliOptions(Path input, boolean json, boolean help) {}
  private record RuntimeConfig(String validatorClass, String schemaEntry) {}
}
