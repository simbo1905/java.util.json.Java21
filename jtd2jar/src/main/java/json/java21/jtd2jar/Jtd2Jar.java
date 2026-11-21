package json.java21.jtd2jar;

import jdk.incubator.java.util.json.Json;
import json.java21.jtd.codegen.JtdCodegen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Logger;

/// Offline JTD-to-JAR compiler.
///
/// Builds a standalone validator JAR from a schema file and can optionally
/// include a `java -jar` entry point and a companion source file.
public final class Jtd2Jar {

  static final Logger LOG = Logger.getLogger(Jtd2Jar.class.getName());

  private static final int DEFAULT_RUNTIME = 21;
  private static final String DEFAULT_PACKAGE = "jtd.generated";
  private static final String DEFAULT_CLASS = "SchemaValidator";
  private static final String PROPERTIES_ENTRY = "jtd2jar.properties";
  private static final String SCHEMA_ENTRY = "jtd/schema.json";
  private static final String MAIN_CLASS = "json.java21.jtd2jar.runtime.ValidatorMain";

  private Jtd2Jar() {}

  public static void main(String[] args) {
    System.exit(run(args));
  }

  public static int run(String[] args) {
    try {
      return new Jtd2Jar().execute(args);
    } catch (UsageException e) {
      System.err.println(e.getMessage());
      System.err.println();
      printUsage();
      return 2;
    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      return 1;
    }
  }

  int execute(String[] args) throws IOException {
    final var options = parseOptions(args);
    if (options.help()) {
      printUsage();
      return 0;
    }

    final var schemaJson = Files.readString(options.schemaPath());
    final var schema = Json.parse(schemaJson);
    final var generatedBytes = JtdCodegen.compileBytes(schema, options.packageName(), options.className());

    if (options.runtime() != DEFAULT_RUNTIME) {
      throw new UsageException("Unsupported runtime version: " + options.runtime()
          + " (only 21 is currently emitted)");
    }

    final var output = options.output() != null ? options.output() : defaultOutput(options.schemaPath());
    writeValidatorJar(output, options, schemaJson, generatedBytes);

    if (options.includeSources()) {
      writeSourceFile(sourcePathFor(output), options);
    }

    LOG.info(() -> "Wrote validator jar: " + output.toAbsolutePath());
    return 0;
  }

  private static void writeValidatorJar(Path output, Options options, String schemaJson, byte[] validatorBytes)
      throws IOException {
    final var manifest = new Manifest();
    final var attrs = manifest.getMainAttributes();
    attrs.putValue("Manifest-Version", "1.0");
    attrs.putValue("Created-By", "jtd2jar");
    if (options.main()) {
      attrs.putValue("Main-Class", MAIN_CLASS);
    }

    createParentDirectories(output);

    final var written = new HashSet<String>();
    try (final var jar = new JarOutputStream(Files.newOutputStream(output), manifest)) {
      copyRuntimeEntries(jar, written);
      writeEntry(jar, written, toInternalName(options.packageName(), options.className()) + ".class", validatorBytes);
      writeEntry(jar, written, SCHEMA_ENTRY, schemaJson.getBytes(StandardCharsets.UTF_8));
      writeEntry(jar, written, PROPERTIES_ENTRY, propertiesBytes(options));
    }
  }

  private static void copyRuntimeEntries(JarOutputStream out, Set<String> written) throws IOException {
    final var classPath = System.getProperty("java.class.path", "");
    if (classPath.isBlank()) {
      return;
    }

    for (final var entry : classPath.split(java.io.File.pathSeparator)) {
      if (entry.isBlank()) {
        continue;
      }
      final var path = Path.of(entry);
      if (Files.isDirectory(path)) {
        copyDirectoryEntries(out, written, path);
      } else if (entry.endsWith(".jar")) {
        copyJarEntries(out, written, path);
      }
    }
  }

  private static void copyDirectoryEntries(JarOutputStream out, Set<String> written, Path root) throws IOException {
    try (final var stream = Files.walk(root)) {
      stream.filter(Files::isRegularFile)
          .forEach(path -> {
            final var rel = root.relativize(path).toString().replace('\\', '/');
            if (!shouldCopyRuntime(rel)) {
              return;
            }
            try {
              writeEntry(out, written, rel, Files.readAllBytes(path));
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (UncheckedIOException e) {
      final var cause = e.getCause();
      throw cause instanceof IOException io ? io : new IOException(cause);
    }
  }

  private static void copyJarEntries(JarOutputStream out, Set<String> written, Path jarPath) throws IOException {
    try (final var jar = new JarFile(jarPath.toFile())) {
      final var entries = jar.entries();
      while (entries.hasMoreElements()) {
        final var entry = entries.nextElement();
        final var name = entry.getName();
        if (!shouldCopyRuntime(name) || entry.isDirectory()) {
          continue;
        }
        try (final var in = jar.getInputStream(entry)) {
          writeEntry(out, written, name, in.readAllBytes());
        }
      }
    }
  }

  private static boolean shouldCopyRuntime(String path) {
    return (path.startsWith("jdk/incubator/java/util/json/")
        || path.startsWith("jdk/incubator/internal/util/json/")
        || path.startsWith("json/java21/jtd/"))
        && !path.startsWith("json/java21/jtd/codegen/")
        || path.startsWith("json/java21/jtd/codegen/JtdValidator.class")
        || path.startsWith("json/java21/jtd2jar/runtime/");
  }

  private static void writeEntry(JarOutputStream out, Set<String> written, String name, byte[] bytes)
      throws IOException {
    if (!written.add(name)) {
      return;
    }
    final var entry = new JarEntry(name);
    entry.setTime(0L);
    out.putNextEntry(entry);
    out.write(bytes);
    out.closeEntry();
  }

  private static byte[] propertiesBytes(Options options) {
    final var props = new Properties();
    props.setProperty("validatorClass", toQualifiedName(options.packageName(), options.className()));
    props.setProperty("schemaEntry", SCHEMA_ENTRY);
    props.setProperty("runtime", Integer.toString(options.runtime()));
    try (final var out = new ByteArrayOutputStream()) {
      props.store(out, "jtd2jar");
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void createParentDirectories(Path output) throws IOException {
    final var parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }

  private static void writeSourceFile(Path sourcePath, Options options) throws IOException {
    final var source = """
        package %s;

        import jdk.incubator.java.util.json.JsonValue;
        import json.java21.jtd.JtdValidationResult;
        import json.java21.jtd2jar.runtime.ValidatorMain;

        public final class %s implements json.java21.jtd.JtdValidator {
          private final String schemaJson;

          public %s(String schemaJson) {
            this.schemaJson = schemaJson;
          }

          @Override
          public JtdValidationResult validate(JsonValue instance) {
            return ValidatorMain.validate(schemaJson, instance);
          }

          @Override
          public String toString() {
            return schemaJson;
          }
        }
        """.formatted(options.packageName(), options.className(), options.className());
    Files.writeString(sourcePath, source, StandardCharsets.UTF_8);
  }

  private static Path sourcePathFor(Path output) {
    final var name = output.getFileName().toString();
    final var sourceName = name.endsWith(".jar") ? name.substring(0, name.length() - 4) + ".java" : name + ".java";
    final var parent = output.getParent();
    return parent == null ? Path.of(sourceName) : parent.resolve(sourceName);
  }

  private static Path defaultOutput(Path schemaPath) {
    final var fileName = schemaPath.getFileName().toString();
    final var base = fileName.endsWith(".jtd.json")
        ? fileName.substring(0, fileName.length() - ".jtd.json".length())
        : fileName.endsWith(".json")
            ? fileName.substring(0, fileName.length() - ".json".length())
            : fileName;
    final var parent = schemaPath.getParent();
    final var output = base + "-validator.jar";
    return parent == null ? Path.of(output) : parent.resolve(output);
  }

  private static Options parseOptions(String[] args) {
    var schema = (Path) null;
    var output = (Path) null;
    var packageName = DEFAULT_PACKAGE;
    var className = DEFAULT_CLASS;
    var main = false;
    var runtime = DEFAULT_RUNTIME;
    var includeSources = false;
    var help = false;

    final var remaining = new ArrayDeque<>(java.util.List.of(args));
    while (!remaining.isEmpty()) {
      final var arg = remaining.removeFirst();
      switch (arg) {
        case "--help" -> help = true;
        case "--main" -> main = true;
        case "--include-sources" -> includeSources = true;
        case "--output" -> output = Path.of(requireValue(remaining, "--output"));
        case "--package" -> packageName = requireValue(remaining, "--package");
        case "--class" -> className = requireValue(remaining, "--class");
        case "--runtime" -> runtime = Integer.parseInt(requireValue(remaining, "--runtime"));
        default -> {
          if (arg.startsWith("--")) {
            throw new UsageException("Unknown option: " + arg);
          }
          if (schema != null) {
            throw new UsageException("Multiple schema paths provided: " + schema + " and " + arg);
          }
          schema = Path.of(arg);
        }
      }
    }

    if (help) {
      return new Options(null, null, packageName, className, main, runtime, includeSources, true);
    }
    if (schema == null) {
      throw new UsageException("Missing schema path");
    }
    if (!Files.exists(schema)) {
      throw new UsageException("Schema file not found: " + schema.toAbsolutePath());
    }
    return new Options(schema, output, packageName, className, main, runtime, includeSources, false);
  }

  private static String requireValue(ArrayDeque<String> args, String option) {
    if (args.isEmpty()) {
      throw new UsageException("Missing value for " + option);
    }
    return args.removeFirst();
  }

  private static String toInternalName(String packageName, String className) {
    return packageName.replace('.', '/') + "/" + className;
  }

  private static String toQualifiedName(String packageName, String className) {
    return packageName + "." + className;
  }

  private static void printUsage() {
    System.out.println("""
        jtd2jar <schema.json> [options]

        Options:
          --output <path>       Output JAR path (default: <schema-name>-validator.jar)
          --package <name>      Java package for generated classes (default: jtd.generated)
          --class <name>        Validator class name (default: SchemaValidator)
          --main                Include a main() for standalone CLI validation
          --runtime <version>   Target bytecode version (default: 21)
          --include-sources     Also output generated .java files alongside the JAR
          --help                Show help
        """);
  }

  record Options(Path schemaPath, Path output, String packageName, String className,
                 boolean main, int runtime, boolean includeSources, boolean help) {}

  static final class UsageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    UsageException(String message) {
      super(message);
    }
  }
}
