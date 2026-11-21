package json.java21.jsonpath;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/// Compiles a JsonPath AST into a bytecode-generated [JsonPath].
///
/// The generated class targets Java 21 (class file version 65,
/// `ClassFile.JAVA_21_VERSION`) and is produced with the JDK 24 ClassFile
/// API (JEP 484), mirroring `JtdCodegen` / `JtdValidator.compileGenerated`
/// in the jtd-codegen module. This class is only present in artifacts built
/// on JDK 24+; the interpreted engine is always available.
///
/// Use [JsonPathCodegen#compile(JsonPath)] for an on-demand compiled
/// query, or [JsonPathCodegen#compileBytes(String, JsonPathAst.Root)]
/// for ahead-of-time, `jtd2jar`-style build-time tooling.
public final class JsonPathCodegen {

    static final Logger LOG = Logger.getLogger(JsonPathCodegen.class.getName());
    private static final AtomicLong COUNTER = new AtomicLong();
    private static final String PACKAGE_NAME = "json.java21.jsonpath";

    private JsonPathCodegen() {}

    /// Result of compilation including the compiled path and generated class statistics.
    public record CompileResult(JsonPath path, int classfileBytes) {}

    /// Compiles a parsed JsonPath into a bytecode-generated query.
    /// Requires JDK 24+ (ClassFile API, JEP 484). Generated classes target Java 21 bytecode.
    ///
    /// @param path the parsed JsonPath (interpreted or compiled; compiled paths return unchanged)
    /// @return a reusable compiled [JsonPath] implementing the same public surface
    public static JsonPath compile(JsonPath path) {
        return compileWithStats(path).path();
    }

    /// Compiles the path and returns both the compiled path and the generated
    /// classfile size in bytes. Useful for benchmarking and diagnostics.
    ///
    /// @param path the parsed JsonPath
    /// @return the compiled path and the generated classfile size in bytes
    public static CompileResult compileWithStats(JsonPath path) {
        return switch (path) {
            case JsonPathCompiled compiled -> new CompileResult(compiled, 0);
            case JsonPathInterpreted interpreted -> compileInterpreted(interpreted);
        };
    }

    /// Emits the classfile bytes for a query without loading it.
    ///
    /// For ahead-of-time, `jtd2jar`-style build-time tooling. Generated classes are
    /// always emitted in the `json.java21.jsonpath` package so they can implement
    /// the package-private [JsonPathExecutor] interface; the classfile targets
    /// Java 21 bytecode (`ClassFile.JAVA_21_VERSION`).
    ///
    /// @param className simple name for the generated executor class (package-fixed)
    /// @param ast the parsed JsonPath AST to compile
    /// @return the generated classfile bytes
    public static byte[] compileBytes(String className, JsonPathAst.Root ast) {
        return JsonPathBytecodeEmitter.emit(PACKAGE_NAME, className, ast);
    }

    private static CompileResult compileInterpreted(JsonPathInterpreted interpreted) {
        final var className = "GeneratedJsonPath" + COUNTER.incrementAndGet();
        final var ast = interpreted.ast();
        LOG.fine(() -> "Generating JsonPath executor class: " + PACKAGE_NAME + "." + className);

        final var bytes = compileBytes(className, ast);
        try {
            final var clazz = MethodHandles.lookup().defineClass(bytes);
            final var executor = (JsonPathExecutor) clazz.getDeclaredConstructor().newInstance();
            return new CompileResult(new JsonPathCompiled(ast, executor, interpreted.toString()), bytes.length);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to load generated JsonPath executor: " + className, e);
        }
    }
}
