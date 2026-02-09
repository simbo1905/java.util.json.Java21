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
    final var jtd = new Jtd();
    final var compiled = jtd.compileToSchema(schema);
    final var schemaJson = schema.toString();

    final var className = "json/java21/jtd/codegen/Generated_" + COUNTER.incrementAndGet();
    final var classDesc = ClassDesc.ofInternalName(className);

    LOG.fine(() -> "Generating validator class: " + className);

    final var bytes = ClassFile.of().build(classDesc, clb -> {
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

    try {
      final var lookup = MethodHandles.lookup();
      final var clazz = lookup.defineClass(bytes);
      final var ctor = clazz.getConstructor(String.class);
      final var validator = (JtdValidator) ctor.newInstance(schemaJson);
      return new CompileResult(validator, bytes.length);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load generated validator: " + className, e);
    }
  }
}
