package json.java21.jtd.codegen;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import jdk.sandbox.java.util.json.JsonValue;
import json.java21.jtd.*;

/// Compiles a JTD schema into a bytecode-generated [JtdValidator].
///
/// The generated class targets Java 21 (class file version 65) and
/// contains only the checks the schema requires.
///
/// Entry point for the `JtdValidator.compileGenerated()` reflection call.
public final class JtdCodegen {

  static final Logger LOG = Logger.getLogger(JtdCodegen.class.getName());
  private static final AtomicLong COUNTER = new AtomicLong();
  private static final String DEFAULT_PACKAGE = "json.java21.jtd.codegen";

  private JtdCodegen() {}

  /// Result of compilation including the validator and generated class statistics.
  public record CompileResult(JtdValidator validator, int classfileBytes) {}

  /// Public factory invoked by [JtdValidator.compileGenerated] via reflection.
  public static JtdValidator compile(JsonValue schema) {
    return compileWithStats(schema).validator();
  }

  /// Compiles the schema and returns both the validator and the generated
  /// classfile size in bytes. Useful for benchmarking and diagnostics.
  public static CompileResult compileWithStats(JsonValue schema) {
    final var className = "Generated_" + COUNTER.incrementAndGet();
    final var bytes = buildBytes(schema, DEFAULT_PACKAGE, className);
    return instantiate(schema, bytes, DEFAULT_PACKAGE, className);
  }

  /// Compiles the schema into a named validator class and returns the raw bytes.
  ///
  /// @param schema the JTD schema as a parsed [JsonValue]
  /// @param packageName Java package for the generated validator
  /// @param className Generated validator class name
  /// @return the generated class bytes
  public static byte[] compileBytes(JsonValue schema, String packageName, String className) {
    return buildBytes(schema, packageName, className);
  }

  private static CompileResult instantiate(JsonValue schema, byte[] bytes, String packageName, String className) {
    final var internalName = packageName.replace('.', '/') + "/" + className;
    final var schemaJson = schema.toString();
    try {
      final var lookup = MethodHandles.lookup();
      final var clazz = lookup.defineClass(bytes);
      final var ctor = clazz.getConstructor(String.class);
      final var validator = (JtdValidator) ctor.newInstance(schemaJson);
      return new CompileResult(validator, bytes.length);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load generated validator: " + internalName, e);
    }
  }

  private static byte[] buildBytes(JsonValue schema, String packageName, String className) {
    final var jtd = new Jtd();
    final var compiled = jtd.compileToSchema(schema);
    final var internalName = packageName.replace('.', '/') + "/" + className;
    final var classDesc = ClassDesc.ofInternalName(internalName);

    LOG.fine(() -> "Generating validator class: " + internalName);

    return ClassFile.of().build(classDesc, clb -> {
      clb.withVersion(ClassFile.JAVA_21_VERSION, 0);
      clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
      clb.withSuperclass(Descriptors.CD_Object);
      clb.withInterfaceSymbols(Descriptors.CD_JtdValidator);
      clb.with(SourceFileAttribute.of("JtdCodegen"));

      clb.withField("schemaJson", Descriptors.CD_String,
          ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

      EmitScaffold.emitConstructor(clb, classDesc);
      EmitScaffold.emitToString(clb, classDesc);
      EmitScaffold.emitValidateMethod(clb, classDesc, compiled);
    });
  }
}
