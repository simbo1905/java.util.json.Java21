package json.java21.jsonpath.codegen;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import jdk.sandbox.java.util.json.JsonValue;
import json.java21.jsonpath.JsonPath;
import json.java21.jsonpath.JsonPathAst;

/// Compiles a JsonPath expression into a bytecode-generated [CompiledJsonPath].
///
/// The generated class targets Java 21 (class file version 65) and
/// contains only the evaluation logic the expression requires.
///
/// Usage:
/// ```java
/// CompiledJsonPath compiled = JsonPathCodegen.compile("$.store.book[*].title");
/// List<JsonValue> results = compiled.query(document);
/// ```
public final class JsonPathCodegen {

    static final Logger LOG = Logger.getLogger(JsonPathCodegen.class.getName());
    private static final AtomicLong COUNTER = new AtomicLong();

    private JsonPathCodegen() {}

    /// Result of compilation including the compiled query and generated class statistics.
    public record CompileResult(CompiledJsonPath query, int classfileBytes) {}

    /// Compiles a JsonPath expression string into a bytecode-generated query.
    ///
    /// @param expression the JsonPath expression (must start with `$`)
    /// @return a compiled query that can be reused across multiple documents
    public static CompiledJsonPath compile(String expression) {
        return compileWithStats(expression).query();
    }

    /// Compiles and returns both the query and the generated classfile size in bytes.
    public static CompileResult compileWithStats(String expression) {
        final var parsed = JsonPath.parse(expression);
        final var ast = parsed.ast();

        final var className = "json/java21/jsonpath/codegen/Generated_" + COUNTER.incrementAndGet();
        final var classDesc = ClassDesc.ofInternalName(className);

        LOG.fine(() -> "Generating JsonPath query class: " + className + " for: " + expression);

        final var bytes = ClassFile.of().build(classDesc, clb -> {
            clb.withVersion(ClassFile.JAVA_21_VERSION, 0);
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            clb.withSuperclass(Descriptors.CD_Object);
            clb.withInterfaceSymbols(Descriptors.CD_CompiledJsonPath);
            clb.with(SourceFileAttribute.of("JsonPathCodegen"));

            clb.withField("expression", Descriptors.CD_String,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

            EmitScaffold.emitConstructor(clb, classDesc);
            EmitScaffold.emitToString(clb, classDesc);
            EmitScaffold.emitQueryMethod(clb, classDesc, ast);
        });

        try {
            final var lookup = MethodHandles.lookup();
            final var clazz = lookup.defineClass(bytes);
            final var ctor = clazz.getConstructor(String.class);
            final var query = (CompiledJsonPath) ctor.newInstance(expression);
            return new CompileResult(query, bytes.length);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load generated JsonPath query: " + className, e);
        }
    }
}
