package io.github.simbo1905.tracker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.SimpleJavaFileObject;

import com.sun.source.tree.*;
import com.sun.source.util.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import javax.lang.model.element.Modifier;
import java.util.logging.Logger;
import java.util.logging.Level;

public class CompilerApiLearningTest {

    /// In-memory JavaFileObject for creating stub classes
    static class InMemoryJavaFileObject extends SimpleJavaFileObject {
        private final String content;

        InMemoryJavaFileObject(String className, String content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }
    private static final Logger LOGGER = Logger.getLogger(CompilerApiLearningTest.class.getName());
    private static final String JSON_OBJECT_SOURCE_PATH = "src/test/resources/JsonObject.java";

    @BeforeAll
    static void setupLogging() {
        LoggingControl.setupCleanLogging();
    }

    @Test
    @DisplayName("Test 1: Source-Level Analysis with JavaParser API (Parse-Only)")
    void testSourceLevelAnalysisWithJavaParser() throws IOException {
        Instant start = Instant.now();
        LOGGER.info("\n--- Running Test 1: Source-Level Analysis with JavaParser API (Parse-Only) ---");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JavaCompiler should be available").isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);

        File jsonObjectFile = new File(JSON_OBJECT_SOURCE_PATH);
        LOGGER.fine("JsonObject file path: " + jsonObjectFile.getAbsolutePath());
        LOGGER.fine("JsonObject file exists: " + jsonObjectFile.exists());
        LOGGER.fine("JsonObject file canonical path: " + jsonObjectFile.getCanonicalPath());

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(jsonObjectFile));

        // Create stub sources for internal dependencies
      List<JavaFileObject> allCompilationUnits = new ArrayList<>((List<? extends JavaFileObject>) compilationUnits);

        // Add stub for PreviewFeature annotation
        String previewFeatureStub = """
            package jdk.internal.javac;
            import java.lang.annotation.*;
            @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
            @Retention(RetentionPolicy.RUNTIME)
            public @interface PreviewFeature {
                Feature feature();
                enum Feature { JSON }
            }
            """;
        allCompilationUnits.add(new InMemoryJavaFileObject("jdk.internal.javac.PreviewFeature", previewFeatureStub));

        // Add stub for JsonObjectImpl
        String jsonObjectImplStub = """
            package jdk.internal.util.json;
            import java.util.Map;
            import java.util.json.JsonObject;
            import java.util.json.JsonValue;
            public class JsonObjectImpl implements JsonObject {
                public JsonObjectImpl(Map<String, JsonValue> map) {}
                public Map<String, JsonValue> members() { return null; }
                public boolean equals(Object obj) { return false; }
                public int hashCode() { return 0; }
            }
            """;
        allCompilationUnits.add(new InMemoryJavaFileObject("jdk.internal.util.json.JsonObjectImpl", jsonObjectImplStub));

        // Add stub for JsonValue interface (parent of JsonObject)
        String jsonValueStub = """
            package java.util.json;
            public sealed interface JsonValue permits JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean, JsonNull {}
            """;
        allCompilationUnits.add(new InMemoryJavaFileObject("java.util.json.JsonValue", jsonValueStub));

        // Add minimal stubs for other JSON types referenced in permits clause
        String[] jsonTypes = {"JsonArray", "JsonString", "JsonNumber", "JsonBoolean", "JsonNull"};
        for (String type : jsonTypes) {
            String stub = "package java.util.json; public non-sealed interface " + type + " extends JsonValue {}";
            allCompilationUnits.add(new InMemoryJavaFileObject("java.util.json." + type, stub));
        }

        // Create a CompilationTask in parse-only mode with relaxed compilation
        List<String> options = List.of(
            "-proc:none",  // Disable annotation processing
            "-XDignore.symbol.file",  // Ignore internal API restrictions
            "-Xlint:none",  // Disable all warnings
            "--enable-preview",  // Enable preview features
            "--release", "24"  // Target Java 24
        );

        JavacTask task = (JavacTask) compiler.getTask(
                null, // no output writer
                fileManager,
                diagnostics,
                options,
                null, // no classes
                allCompilationUnits);

        try {
            Iterable<? extends CompilationUnitTree> trees = task.parse();
            assertThat(trees).as("Should parse at least one compilation unit").isNotEmpty();

            JsonObject extractedApi = null;

            for (CompilationUnitTree tree : trees) {
                String fileName = tree.getSourceFile().getName();
                LOGGER.info("Parsed Compilation Unit: " + fileName);

                // Only process the JsonObject.java file, skip stub files
                if (!fileName.endsWith("JsonObject.java")) {
                    continue;
                }

                // Visitor to extract API information
                ApiExtractorVisitor visitor = new ApiExtractorVisitor(Trees.instance(task));
                visitor.scan(tree, null);

                extractedApi = visitor.getExtractedApi();
                LOGGER.info("Extracted API: " + Json.toDisplayString(extractedApi, 2));
                LOGGER.fine("Raw extracted API map keys: " + extractedApi.members().keySet());
                LOGGER.finer("Full extracted API: " + extractedApi.members());
            }

            assertThat(extractedApi).as("Should have extracted API from JsonObject.java").isNotNull();

            // Basic assertions for expected content
            JsonString className = (JsonString) extractedApi.members().get("className");
            assertThat(className.value()).isEqualTo("JsonObject");

            JsonArray modifiers = (JsonArray) extractedApi.members().get("modifiers");
            assertThat(modifiers).as("modifiers should be present").isNotNull();
            Set<String> modifierStrings = modifiers.values().stream()
                    .map(v -> ((JsonString) v).value())
                    .collect(Collectors.toSet());
            assertThat(modifierStrings).contains("public");

            JsonArray extendsList = (JsonArray) extractedApi.members().get("extends");
            assertThat(extendsList).as("extends should be present").isNotNull();
            Set<String> extendsStrings = extendsList.values().stream()
                    .map(v -> ((JsonString) v).value())
                    .collect(Collectors.toSet());
            assertThat(extendsStrings).contains("JsonValue");

            JsonObject methods = (JsonObject) extractedApi.members().get("methods");
            assertThat(methods).as("methods should be present").isNotNull();
            assertThat(methods.members()).containsKey("members");
            assertThat(methods.members()).containsKey("of");
            assertThat(methods.members()).containsKey("equals");
            assertThat(methods.members()).containsKey("hashCode");

            // Log diagnostics (errors/warnings from parsing)
            diagnostics.getDiagnostics().forEach(d -> LOGGER.warning("Diagnostic: " + d));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during parsing: " + e.getMessage(), e);
            throw e; // Re-throw to fail the test
        } finally {
            fileManager.close();
            Instant end = Instant.now();
            LOGGER.info("Test 1 finished in: " + Duration.between(start, end).toMillis() + " ms");
        }
    }

    // Helper class to extract API information from the AST
    static class ApiExtractorVisitor extends TreePathScanner<Void, Void> {
        private final Map<String, JsonValue> classMap = new LinkedHashMap<>();
        private final Map<String, JsonValue> methodsMap = new LinkedHashMap<>();

        public ApiExtractorVisitor(Trees trees) {
            // Trees parameter kept for future use if needed
        }

        public JsonObject getExtractedApi() {
            classMap.put("methods", JsonObject.of(methodsMap));
            return JsonObject.of(classMap);
        }

        @Override
        public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
            LOGGER.fine("Visiting compilation unit with " + node.getTypeDecls().size() + " type declarations");
            for (Tree member : node.getTypeDecls()) {
                LOGGER.fine("Type declaration kind: " + member.getKind());
                scan(member, p);
            }
            return super.visitCompilationUnit(node, p);
        }

        @Override
        public Void visitClass(ClassTree node, Void p) {
            LOGGER.fine("Visiting class: " + node.getSimpleName());
            LOGGER.finer("Class kind: " + node.getKind());
            LOGGER.finer("Number of members: " + node.getMembers().size());

            classMap.put("className", JsonString.of(node.getSimpleName().toString()));
            classMap.put("modifiers", JsonArray.of(node.getModifiers().getFlags().stream()
                    .map(Object::toString)
                    .map(JsonString::of)
                    .collect(Collectors.<JsonValue>toList())));

            List<JsonValue> extendsList = new ArrayList<>();
            if (node.getExtendsClause() != null) {
                extendsList.add(JsonString.of(node.getExtendsClause().toString()));
            }
            if (!node.getImplementsClause().isEmpty()) {
                node.getImplementsClause().forEach(impl -> extendsList.add(JsonString.of(impl.toString())));
            }
            classMap.put("extends", JsonArray.of(extendsList));

            // Log members
            for (Tree member : node.getMembers()) {
                LOGGER.finer("Member kind: " + member.getKind() + ", toString: " + member.toString().substring(0, Math.min(50, member.toString().length())));
            }

            return super.visitClass(node, p);
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            LOGGER.finer("Visiting method: " + node.getName() + ", modifiers: " + node.getModifiers().getFlags());

            // In interfaces, methods without private/default modifiers are implicitly public
            boolean isPublic = node.getModifiers().getFlags().contains(Modifier.PUBLIC) ||
                              (getCurrentPath().getParentPath() != null &&
                               getCurrentPath().getParentPath().getLeaf().getKind() == Tree.Kind.INTERFACE &&
                               !node.getModifiers().getFlags().contains(Modifier.PRIVATE) &&
                               !node.getModifiers().getFlags().contains(Modifier.DEFAULT));

            if (isPublic) {
                LOGGER.fine("Processing public method: " + node.getName());
                Map<String, JsonValue> methodMap = new LinkedHashMap<>();
                methodMap.put("modifiers", JsonArray.of(node.getModifiers().getFlags().stream()
                        .map(Object::toString)
                        .map(JsonString::of)
                        .collect(Collectors.<JsonValue>toList())));
                methodMap.put("returnType", JsonString.of(node.getReturnType() != null ? node.getReturnType().toString() : "void"));

                List<JsonValue> parameters = new ArrayList<>();
                node.getParameters().forEach(param -> parameters.add(JsonString.of(param.getType() + " " + param.getName())));
                methodMap.put("parameters", JsonArray.of(parameters));

                List<JsonValue> throwsList = new ArrayList<>();
                node.getThrows().forEach(throwable -> throwsList.add(JsonString.of(throwable.toString())));
                methodMap.put("throws", JsonArray.of(throwsList));

                methodsMap.put(node.getName().toString(), JsonObject.of(methodMap));
            }
            return super.visitMethod(node, p);
        }
    }
}
