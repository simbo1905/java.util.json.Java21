package json.java21.jsonpath;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/// Package-private runtime compiler for `JsonPath`.
///
/// This compiles AST-backed JsonPath instances into generated Java classes using the JDK compiler tools.
final class JsonPathCompiler {

    private static final Logger LOG = Logger.getLogger(JsonPathCompiler.class.getName());
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String PACKAGE_NAME = "json.java21.jsonpath";

    private JsonPathCompiler() {
    }

    static JsonPath compile(JsonPath path) {
        Objects.requireNonNull(path, "path must not be null");
        if (path instanceof JsonPathCompiled) {
            return path;
        }
        if (path instanceof JsonPathAstBacked astBacked) {
            final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                LOG.info(() -> "Runtime compiler unavailable; returning AST-backed JsonPath for: " + path);
                return path;
            }
            return compileAst(compiler, astBacked.ast());
        }
        return path;
    }

    static String toJavaSourceForTests(JsonPathAst.Root ast) {
        final var className = "JsonPathGenerated_TEST";
        final var expression = JsonPathAstInterpreter.reconstruct(ast);
        return generateSource(PACKAGE_NAME, className, expression, ast);
    }

    private static JsonPath compileAst(JavaCompiler compiler, JsonPathAst.Root ast) {
        final var expression = JsonPathAstInterpreter.reconstruct(ast);
        final var className = "JsonPathGenerated_" + COUNTER.incrementAndGet();
        final var fqcn = PACKAGE_NAME + "." + className;

        final var javaSource = generateSource(PACKAGE_NAME, className, expression, ast);
        final var bytecode = compileToBytes(compiler, fqcn, javaSource);
        final var instance = instantiate(fqcn, bytecode);
        return new JsonPathCompiledPath(expression, fqcn, javaSource, instance);
    }

    private static JsonPath instantiate(String fqcn, Map<String, byte[]> bytecode) {
        try {
            final var loader = new InMemoryClassLoader(JsonPathCompiler.class.getClassLoader(), bytecode);
            final Class<?> clazz = loader.loadClass(fqcn);
            if (!JsonPath.class.isAssignableFrom(clazz)) {
                throw new IllegalStateException("Generated class does not implement JsonPath: " + fqcn);
            }
            @SuppressWarnings("unchecked")
            final Class<? extends JsonPath> typed = (Class<? extends JsonPath>) clazz;
            final Constructor<? extends JsonPath> ctor = typed.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to load/instantiate generated JsonPath class: " + fqcn, ex);
        }
    }

    private static Map<String, byte[]> compileToBytes(JavaCompiler compiler, String fqcn, String javaSource) {
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();

        try (StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, null)) {
            final var memManager = new MemoryFileManager(standard);

            final var classpath = System.getProperty("java.class.path");
            final List<String> options = new ArrayList<>();
            options.add("--release");
            options.add("21");
            if (classpath != null && !classpath.isBlank()) {
                options.add("-classpath");
                options.add(classpath);
            }
            options.add("-Xlint:none");

            final var sources = List.of(new StringJavaFileObject(fqcn, javaSource));
            final var task = compiler.getTask(null, memManager, diagnostics, options, null, sources);
            final Boolean ok = task.call();
            if (!Boolean.TRUE.equals(ok)) {
                throw new IllegalStateException("Failed to compile generated JsonPath:\n" + formatDiagnostics(diagnostics, javaSource));
            }

            return memManager.bytecode();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed compiling generated JsonPath source for: " + fqcn, ex);
        }
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics, String javaSource) {
        final var sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            sb.append(d.getKind())
                    .append(" at ")
                    .append(d.getLineNumber())
                    .append(":")
                    .append(d.getColumnNumber())
                    .append(" ")
                    .append(d.getMessage(null))
                    .append("\n");
        }
        sb.append("\n--- Generated Source ---\n").append(javaSource).append("\n");
        return sb.toString();
    }

    private static String generateSource(String pkg, String className, String expression, JsonPathAst.Root ast) {
        final var sb = new StringBuilder(8_192);
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import jdk.sandbox.java.util.json.*;\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.util.stream.*;\n\n");
        sb.append("final class ").append(className).append(" implements JsonPath {\n");
        sb.append("  public ").append(className).append("() {}\n\n");
        sb.append("  @Override public List<JsonValue> query(JsonValue json) {\n");
        sb.append("    Objects.requireNonNull(json, \"json must not be null\");\n");
        sb.append("    Stream<JsonValue> s = Stream.of(json);\n");

        for (final var segment : ast.segments()) {
            emitSegment(sb, segment);
        }

        sb.append("    return s.toList();\n");
        sb.append("  }\n\n");
        sb.append("  @Override public String toString() { return ").append(javaStringLiteral(expression)).append("; }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void emitSegment(StringBuilder sb, JsonPathAst.Segment segment) {
        switch (segment) {
            case JsonPathAst.PropertyAccess prop ->
                    sb.append("    s = s.flatMap(v -> JsonPathRuntime.selectProperty(v, ").append(javaStringLiteral(prop.name())).append("));\n");
            case JsonPathAst.ArrayIndex arr ->
                    sb.append("    s = s.flatMap(v -> JsonPathRuntime.selectIndex(v, ").append(arr.index()).append("));\n");
            case JsonPathAst.ArraySlice slice -> {
                sb.append("    s = s.flatMap(v -> JsonPathRuntime.selectSlice(v, ")
                        .append(slice.start() == null ? "null" : "Integer.valueOf(" + slice.start() + ")")
                        .append(", ")
                        .append(slice.end() == null ? "null" : "Integer.valueOf(" + slice.end() + ")")
                        .append(", ")
                        .append(slice.step() == null ? "null" : "Integer.valueOf(" + slice.step() + ")")
                        .append("));\n");
            }
            case JsonPathAst.Wildcard ignored ->
                    sb.append("    s = s.flatMap(JsonPathRuntime::selectWildcard);\n");
            case JsonPathAst.RecursiveDescent desc -> emitRecursive(sb, desc);
            case JsonPathAst.Filter filter -> {
                sb.append("    s = s.flatMap(v -> JsonPathRuntime.filterArray(v, e -> ");
                sb.append(emitFilterExpression(filter.expression(), "e"));
                sb.append("));\n");
            }
            case JsonPathAst.Union union -> emitUnion(sb, union);
            case JsonPathAst.ScriptExpression script ->
                    sb.append("    s = s.flatMap(v -> JsonPathRuntime.selectScript(v, ").append(javaStringLiteral(script.script())).append("));\n");
        }
    }

    private static void emitRecursive(StringBuilder sb, JsonPathAst.RecursiveDescent desc) {
        switch (desc.target()) {
            case JsonPathAst.PropertyAccess prop ->
                    sb.append("    s = s.flatMap(v -> JsonPathRuntime.selectRecursiveProperty(v, ").append(javaStringLiteral(prop.name())).append("));\n");
            case JsonPathAst.Wildcard ignored ->
                    sb.append("    s = s.flatMap(JsonPathRuntime::selectRecursiveWildcard);\n");
            default ->
                    sb.append("    s = Stream.empty();\n");
        }
    }

    private static void emitUnion(StringBuilder sb, JsonPathAst.Union union) {
        final var selectors = union.selectors();
        final var first = selectors.getFirst();
        if (first instanceof JsonPathAst.PropertyAccess) {
            sb.append("    s = s.flatMap(v -> JsonPathRuntime.selectUnionProperties(v, new String[]{");
            for (int i = 0; i < selectors.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(javaStringLiteral(((JsonPathAst.PropertyAccess) selectors.get(i)).name()));
            }
            sb.append("}));\n");
            return;
        }
        if (first instanceof JsonPathAst.ArrayIndex) {
            sb.append("    s = s.flatMap(v -> JsonPathRuntime.selectUnionIndices(v, new int[]{");
            for (int i = 0; i < selectors.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(((JsonPathAst.ArrayIndex) selectors.get(i)).index());
            }
            sb.append("}));\n");
            return;
        }
        sb.append("    s = Stream.empty();\n");
    }

    private static String emitFilterExpression(JsonPathAst.FilterExpression expr, String currentVar) {
        return switch (expr) {
            case JsonPathAst.ExistsFilter exists -> "JsonPathRuntime.resolvePropertyPath(" + currentVar + ", new String[]{" +
                    joinStringLiterals(exists.path().properties()) + "}) != null";
            case JsonPathAst.ComparisonFilter comp -> "JsonPathRuntime.compareComparable(" +
                    emitFilterOperand(comp.left(), currentVar) + ", JsonPathAst.ComparisonOp." + comp.op().name() + ", " +
                    emitFilterOperand(comp.right(), currentVar) + ")";
            case JsonPathAst.LogicalFilter logical -> switch (logical.op()) {
                case NOT -> "(!(" + emitFilterExpression(logical.left(), currentVar) + "))";
                case AND -> "((" + emitFilterExpression(logical.left(), currentVar) + ") && (" + emitFilterExpression(logical.right(), currentVar) + "))";
                case OR -> "((" + emitFilterExpression(logical.left(), currentVar) + ") || (" + emitFilterExpression(logical.right(), currentVar) + "))";
            };
            case JsonPathAst.CurrentNode ignored -> "true";
            case JsonPathAst.PropertyPath path -> "JsonPathRuntime.resolvePropertyPath(" + currentVar + ", new String[]{" +
                    joinStringLiterals(path.properties()) + "}) != null";
            case JsonPathAst.LiteralValue ignored -> "true";
        };
    }

    private static String emitFilterOperand(JsonPathAst.FilterExpression expr, String currentVar) {
        return switch (expr) {
            case JsonPathAst.PropertyPath path -> "JsonPathRuntime.toComparable(JsonPathRuntime.resolvePropertyPath(" + currentVar + ", new String[]{" +
                    joinStringLiterals(path.properties()) + "}))";
            case JsonPathAst.LiteralValue lit -> literalToJava(lit.value());
            case JsonPathAst.CurrentNode ignored -> "JsonPathRuntime.toComparable(" + currentVar + ")";
            default -> "null";
        };
    }

    private static String joinStringLiterals(List<String> strings) {
        final var sb = new StringBuilder();
        for (int i = 0; i < strings.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(javaStringLiteral(strings.get(i)));
        }
        return sb.toString();
    }

    private static String literalToJava(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return javaStringLiteral(s);
        if (value instanceof Boolean b) return b ? "true" : "false";
        if (value instanceof Integer i) return Integer.toString(i);
        if (value instanceof Long l) return l + "L";
        if (value instanceof Double d) return Double.toString(d) + "d";
        if (value instanceof Float f) return Float.toString(f) + "f";
        if (value instanceof Number n) return n.toString();
        // Fallback: should not occur for filter literals
        return javaStringLiteral(String.valueOf(value));
    }

    private static String javaStringLiteral(String s) {
        return "\"" + escapeJava(s) + "\"";
    }

    private static String escapeJava(String s) {
        final var out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        StringJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class BytecodeJavaFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        BytecodeJavaFileObject(String className, Kind kind) {
            super(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return out;
        }

        byte[] bytes() {
            return out.toByteArray();
        }
    }

    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, BytecodeJavaFileObject> compiled = new ConcurrentHashMap<>();

        MemoryFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            final var file = new BytecodeJavaFileObject(className, kind);
            compiled.put(className, file);
            return file;
        }

        Map<String, byte[]> bytecode() {
            final var out = new ConcurrentHashMap<String, byte[]>();
            compiled.forEach((k, v) -> out.put(k, v.bytes()));
            return out;
        }
    }

    private static final class InMemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> bytecode;

        InMemoryClassLoader(ClassLoader parent, Map<String, byte[]> bytecode) {
            super(parent);
            this.bytecode = bytecode;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            final var bytes = bytecode.get(name);
            if (bytes == null) {
                return super.findClass(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}

