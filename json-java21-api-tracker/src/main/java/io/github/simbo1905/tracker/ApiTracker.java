package io.github.simbo1905.tracker;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonBoolean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;

/// API Tracker module for comparing local and upstream JSON APIs
///
/// This module provides functionality to:
/// - Discover local JSON API classes via reflection
/// - Fetch corresponding upstream sources from GitHub
/// - Compare public APIs using compiler parsing
/// - Generate structured diff reports
///
/// Modular design supports different extraction strategies:
/// - Binary reflection for quick class introspection
/// - Source parsing for accurate parameter names and signatures
///
/// All functionality is exposed as static methods following functional programming principles
public sealed interface ApiTracker permits ApiTracker.Nothing {

    /// Local source root for source-based extraction
    String LOCAL_SOURCE_ROOT = "json-java21/src/main/java";

    /// Empty enum to seal the interface - no instances allowed
    enum Nothing implements ApiTracker {}

    // Package-private logger shared across the module
    Logger LOGGER = Logger.getLogger(ApiTracker.class.getName());

    // Cache for HTTP responses to avoid repeated fetches
    Map<String, String> FETCH_CACHE = new ConcurrentHashMap<>();

    // GitHub base URL for upstream sources
    String GITHUB_BASE_URL = "https://raw.githubusercontent.com/openjdk/jdk-sandbox/refs/heads/json/src/java.base/share/classes/";

    /// Fetches content from a URL
    static String fetchFromUrl(String url) {
        final var httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        try {
            final var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 404) {
                return "NOT_FOUND: Upstream file not found (possibly deleted or renamed)";
            } else {
                return "HTTP_ERROR: Status " + response.statusCode();
            }
        } catch (Exception e) {
            return "FETCH_ERROR: " + e.getMessage();
        }
    }

    /// Discovers all classes in the local JSON API packages
    /// @return sorted set of classes from jdk.sandbox.java.util.json and jdk.sandbox.internal.util.json
    static Set<Class<?>> discoverLocalJsonClasses() {
        LOGGER.info(() -> "Starting class discovery for JSON API packages");
        final var classes = new TreeSet<Class<?>>(Comparator.comparing(Class::getName));

        // Packages to scan - only public API, not internal implementation
        final var packages = List.of(
            "jdk.sandbox.java.util.json"
        );

        final var classLoader = Thread.currentThread().getContextClassLoader();

        for (final var packageName : packages) {
            try {
                final var path = packageName.replace('.', '/');
                final var resources = classLoader.getResources(path);

                while (resources.hasMoreElements()) {
                    final var url = resources.nextElement();
                    LOGGER.fine(() -> "Scanning resource: " + url);

                    if ("file".equals(url.getProtocol())) {
                        // Handle directory scanning
                        scanDirectory(new java.io.File(url.toURI()), packageName, classes);
                    } else if ("jar".equals(url.getProtocol())) {
                        // Handle JAR scanning
                        scanJar(url, packageName, classes);
                    }
                }
            } catch (Exception e) {
                LOGGER.warning(() -> "ERROR: Error scanning package: " + packageName + " - " + e.getMessage());
            }
        }

        LOGGER.info(() -> "Discovered " + classes.size() + " classes in JSON API packages: " +
            classes.stream().map(Class::getName).sorted().collect(Collectors.joining(", ")));
        return Collections.unmodifiableSet(classes);
    }

    /// Scans a directory for class files
    static void scanDirectory(java.io.File directory, String packageName, Set<Class<?>> classes) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        final var files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (final var file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                final var className = packageName + '.' +
                    file.getName().substring(0, file.getName().length() - 6);
                try {
                    final var clazz = Class.forName(className);
                    classes.add(clazz);
                    LOGGER.info(() -> "Found class: " + className);
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    LOGGER.fine(() -> "Could not load class: " + className);
                }
            }
        }
    }

    /// Scans a JAR file for classes in the specified package
    static void scanJar(java.net.URL jarUrl, String packageName, Set<Class<?>> classes) {
        try {
            final var jarPath = jarUrl.getPath();
            final var exclamation = jarPath.indexOf('!');
            if (exclamation < 0) {
                return;
            }

            final var jarFilePath = jarPath.substring(5, exclamation); // Remove "file:"
            final var packagePath = packageName.replace('.', '/');

            try (final var jarFile = new java.util.jar.JarFile(jarFilePath)) {
                final var entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    final var entry = entries.nextElement();
                    final var entryName = entry.getName();

                    if (entryName.startsWith(packagePath) &&
                        entryName.endsWith(".class") &&
                        !entryName.contains("$")) {

                        final var className = entryName
                            .substring(0, entryName.length() - 6)
                            .replace('/', '.');

                        try {
                            final var clazz = Class.forName(className);
                            classes.add(clazz);
                            LOGGER.info(() -> "Found class in JAR: " + className);
                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            LOGGER.fine(() -> "Could not load class from JAR: " + className);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "ERROR: Error scanning JAR: " + jarUrl + " - " + e.getMessage());
        }
    }

    /// Fetches upstream source files from GitHub for the given local classes
    /// @param localClasses set of local classes to fetch upstream sources for
    /// @return map of className to source code (or error message if fetch failed)
    static Map<String, String> fetchUpstreamSources(Set<Class<?>> localClasses) {
        Objects.requireNonNull(localClasses, "localClasses must not be null");
        LOGGER.info(() -> "Fetching upstream sources for " + localClasses.size() + " classes");

        final var results = new LinkedHashMap<String, String>();
        final var httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        for (final var clazz : localClasses) {
            final var className = clazz.getName();
            final var cachedSource = FETCH_CACHE.get(className);

            if (cachedSource != null) {
                LOGGER.fine(() -> "Using cached source for: " + className);
                results.put(className, cachedSource);
                continue;
            }

            // Map package name from jdk.sandbox.* to standard java.*
            final var upstreamPath = mapToUpstreamPath(className);
            final var url = GITHUB_BASE_URL + upstreamPath;

            LOGGER.info(() -> "Fetching upstream source: " + url);

            try {
                final var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

                final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    final var body = response.body();
                    FETCH_CACHE.put(className, body);
                    results.put(className, body);
                    LOGGER.info(() -> "Successfully fetched " + body.length() + " chars for: " + className);
                } else if (response.statusCode() == 404) {
                    final var error = "NOT_FOUND: Upstream file not found (possibly deleted or renamed)";
                    results.put(className, error);
                    LOGGER.info(() -> "404 Not Found for upstream: " + className + " at " + url);
                } else {
                    final var error = "HTTP_ERROR: Status " + response.statusCode();
                    results.put(className, error);
                    LOGGER.info(() -> "HTTP error " + response.statusCode() + " for " + className + " at " + url);
                }
            } catch (Exception e) {
                final var error = "FETCH_ERROR: " + e.getMessage();
                results.put(className, error);
                LOGGER.info(() -> "Fetch error for " + className + " at " + url + ": " + e.getMessage());
            }
        }

        return Collections.unmodifiableMap(results);
    }

    /// Maps local class name to upstream GitHub path
    static String mapToUpstreamPath(String className) {
        // Remove jdk.sandbox prefix and map to standard packages
        String path = className
            .replace("jdk.sandbox.java.util.json", "java/util/json")
            .replace("jdk.sandbox.internal.util.json", "jdk/internal/util/json")
            .replace('.', '/');

        return path + ".java";
    }

    /// Extracts local API from source file
    static JsonObject extractLocalApiFromSource(String className) {
        final var path = LOCAL_SOURCE_ROOT + "/" + className.replace('.', '/') + ".java";
        try {
            final var sourceCode = java.nio.file.Files.readString(java.nio.file.Paths.get(path));
            return extractApiFromSource(sourceCode, className);
        } catch (Exception e) {
            return JsonObject.of(Map.of(
                "error", JsonString.of("LOCAL_FILE_NOT_FOUND: " + e.getMessage()),
                "className", JsonString.of(className)
            ));
        }
    }

    /// Extracts public API from source code using compiler parsing
    /// @param sourceCode the source code to parse
    /// @param className the expected class name
    /// @return JSON representation of the parsed API
    static JsonObject extractApiFromSource(String sourceCode, String className) {
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        Objects.requireNonNull(className, "className must not be null");

        // Check for fetch errors
        if (sourceCode.startsWith("NOT_FOUND:") ||
            sourceCode.startsWith("HTTP_ERROR:") ||
            sourceCode.startsWith("FETCH_ERROR:")) {
            final var errorMap = Map.of(
                "error", JsonString.of(sourceCode),
                "className", JsonString.of(className)
            );
            return JsonObject.of(errorMap);
        }

        LOGGER.info(() -> "Extracting upstream API for: " + className);

        final var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return JsonObject.of(Map.of(
                "error", JsonString.of("JavaCompiler not available"),
                "className", JsonString.of(className)
            ));
        }

        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);

        try {
            // Extract simple class name from fully qualified name
            final var simpleClassName = className.substring(className.lastIndexOf('.') + 1);

            // Create compilation units
            final var compilationUnits = new ArrayList<JavaFileObject>();
            compilationUnits.add(new InMemoryJavaFileObject(className, sourceCode));

            // Add minimal stubs for common dependencies
            addCommonStubs(compilationUnits);

            // Parse-only compilation with relaxed settings
            final var options = List.of(
                "-proc:none",
                "-XDignore.symbol.file",
                "-Xlint:none",
                "--enable-preview",
                "--release", "24"
            );

            final var task = (JavacTask) compiler.getTask(
                null,
                fileManager,
                diagnostics,
                options,
                null,
                compilationUnits
            );

            final var trees = task.parse();

            // Extract API using visitor
            for (final var tree : trees) {
                final var fileName = tree.getSourceFile().getName();
                if (fileName.contains(simpleClassName)) {
                    final var visitor = new ApiExtractorVisitor();
                    visitor.scan(tree, null);
                    return visitor.getExtractedApi();
                }
            }

            // If we get here, parsing failed
            return JsonObject.of(Map.of(
                "error", JsonString.of("Failed to parse source"),
                "className", JsonString.of(className)
            ));

        } catch (Exception e) {
            LOGGER.warning(() -> "ERROR: Error parsing upstream source for " + className + " - " + e.getMessage());
            return JsonObject.of(Map.of(
                "error", JsonString.of("Parse error: " + e.getMessage()),
                "className", JsonString.of(className)
            ));
        } finally {
            try {
                fileManager.close();
            } catch (IOException e) {
                LOGGER.fine(() -> "Error closing file manager: " + e.getMessage());
            }
        }
    }

    /// Adds common stub dependencies for JSON API parsing
    static void addCommonStubs(List<JavaFileObject> compilationUnits) {
        // PreviewFeature annotation stub
        compilationUnits.add(new InMemoryJavaFileObject("jdk.internal.javac.PreviewFeature", """
            package jdk.internal.javac;
            import java.lang.annotation.*;
            @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
            @Retention(RetentionPolicy.RUNTIME)
            public @interface PreviewFeature {
                Feature feature();
                enum Feature { JSON }
            }
            """));

        // JsonValue base interface stub
        compilationUnits.add(new InMemoryJavaFileObject("java.util.json.JsonValue", """
            package java.util.json;
            public sealed interface JsonValue permits JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean, JsonNull {}
            """));

        // Basic JSON type stubs
        final var jsonTypes = List.of("JsonObject", "JsonArray", "JsonString", "JsonNumber", "JsonBoolean", "JsonNull");
        for (final var type : jsonTypes) {
            compilationUnits.add(new InMemoryJavaFileObject("java.util.json." + type,
                "package java.util.json; public non-sealed interface " + type + " extends JsonValue {}"));
        }

        // Internal implementation stubs
        compilationUnits.add(new InMemoryJavaFileObject("jdk.internal.util.json.JsonObjectImpl", """
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
            """));
    }

    /// In-memory JavaFileObject for creating stub classes
    class InMemoryJavaFileObject extends SimpleJavaFileObject {
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

    /// Visitor to extract API information from AST
    class ApiExtractorVisitor extends TreePathScanner<Void, Void> {
        private final Map<String, JsonValue> apiMap = new LinkedHashMap<>();
        private final Map<String, JsonValue> methodsMap = new LinkedHashMap<>();
        private final Map<String, JsonValue> fieldsMap = new LinkedHashMap<>();
        private final List<JsonValue> constructors = new ArrayList<>();

        JsonObject getExtractedApi() {
            apiMap.put("methods", JsonObject.of(methodsMap));
            apiMap.put("fields", JsonObject.of(fieldsMap));
            apiMap.put("constructors", JsonArray.of(constructors));
            return JsonObject.of(apiMap);
        }

        @Override
        public Void visitClass(ClassTree node, Void p) {
            // Basic class information
            apiMap.put("className", JsonString.of(node.getSimpleName().toString()));
            apiMap.put("modifiers", extractTreeModifiers(node.getModifiers()));

            // Type information
            final var kind = node.getKind();
            apiMap.put("isInterface", JsonBoolean.of(kind == Tree.Kind.INTERFACE));
            apiMap.put("isEnum", JsonBoolean.of(kind == Tree.Kind.ENUM));
            apiMap.put("isRecord", JsonBoolean.of(kind == Tree.Kind.RECORD));

            // Package name (from compilation unit)
            final var compilationUnit = getCurrentPath().getCompilationUnit();
            final var packageTree = compilationUnit.getPackage();
            if (packageTree != null) {
                apiMap.put("packageName", JsonString.of(packageTree.getPackageName().toString()));
            } else {
                apiMap.put("packageName", JsonString.of(""));
            }

            // Check if sealed
            final var modifiers = node.getModifiers();
            final var isSealed = modifiers.getFlags().stream()
                .anyMatch(m -> m.toString().equals("SEALED"));
            apiMap.put("isSealed", JsonBoolean.of(isSealed));

            // Inheritance
            final var superTypes = new ArrayList<JsonValue>();
            if (node.getExtendsClause() != null) {
                superTypes.add(JsonString.of(extractSimpleName(node.getExtendsClause().toString())));
            }
            node.getImplementsClause().stream()
                .map(tree -> JsonString.of(extractSimpleName(tree.toString())))
                .forEach(superTypes::add);
            apiMap.put("extends", JsonArray.of(superTypes));

            // Permitted subclasses (approximation - would need full symbol resolution)
            if (isSealed) {
                apiMap.put("permits", JsonArray.of(List.of()));
            }

            return super.visitClass(node, p);
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            // Check if public
            final var isPublic = isPublicMember(node.getModifiers());

            if (isPublic) {
                final var methodInfo = new LinkedHashMap<String, JsonValue>();
                methodInfo.put("modifiers", extractTreeModifiers(node.getModifiers()));
                methodInfo.put("returnType", JsonString.of(extractSimpleName(
                    node.getReturnType() != null ? node.getReturnType().toString() : "void")));
                methodInfo.put("genericReturnType", JsonString.of(
                    node.getReturnType() != null ? node.getReturnType().toString() : "void"));

                final var params = node.getParameters().stream()
                    .map(param -> JsonString.of(extractSimpleName(param.getType().toString()) + " " + param.getName()))
                    .collect(Collectors.<JsonValue>toList());
                methodInfo.put("parameters", JsonArray.of(params));

                final var exceptions = node.getThrows().stream()
                    .map(ex -> JsonString.of(extractSimpleName(ex.toString())))
                    .collect(Collectors.<JsonValue>toList());
                methodInfo.put("throws", JsonArray.of(exceptions));

                // Handle constructors separately
                if (node.getName().toString().equals("<init>")) {
                    constructors.add(JsonObject.of(methodInfo));
                } else {
                    methodsMap.put(node.getName().toString(), JsonObject.of(methodInfo));
                }
            }

            return super.visitMethod(node, p);
        }

        @Override
        public Void visitVariable(VariableTree node, Void p) {
            // Only process fields (not method parameters or local variables)
            if (getCurrentPath().getParentPath().getLeaf().getKind() == Tree.Kind.CLASS) {
                final var isPublic = isPublicMember(node.getModifiers());

                if (isPublic) {
                    final var fieldInfo = new LinkedHashMap<String, JsonValue>();
                    fieldInfo.put("modifiers", extractTreeModifiers(node.getModifiers()));
                    fieldInfo.put("type", JsonString.of(extractSimpleName(node.getType().toString())));
                    fieldInfo.put("genericType", JsonString.of(node.getType().toString()));

                    fieldsMap.put(node.getName().toString(), JsonObject.of(fieldInfo));
                }
            }

            return super.visitVariable(node, p);
        }

        private JsonArray extractTreeModifiers(ModifiersTree modifiers) {
            final var modList = modifiers.getFlags().stream()
                .map(m -> JsonString.of(m.toString().toLowerCase()))
                .collect(Collectors.<JsonValue>toList());
            return JsonArray.of(modList);
        }

        private boolean isPublicMember(ModifiersTree modifiers) {
            // In interfaces, methods without private/default are implicitly public
            final var parent = getCurrentPath().getParentPath();
            if (parent != null && parent.getLeaf().getKind() == Tree.Kind.INTERFACE) {
                return !modifiers.getFlags().contains(javax.lang.model.element.Modifier.PRIVATE) &&
                       !modifiers.getFlags().contains(javax.lang.model.element.Modifier.DEFAULT);
            }
            return modifiers.getFlags().contains(javax.lang.model.element.Modifier.PUBLIC);
        }

        private String extractSimpleName(String typeName) {
            // Remove generic parameters and package prefixes
            var name = typeName;
            final var genericIndex = name.indexOf('<');
            if (genericIndex >= 0) {
                name = name.substring(0, genericIndex);
            }
            final var lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                name = name.substring(lastDot + 1);
            }
            return name;
        }
    }

    /// Compares local and upstream APIs to identify differences
    /// @param local the local API structure
    /// @param upstream the upstream API structure
    /// @return JSON object describing the differences
    static JsonObject compareApis(JsonObject local, JsonObject upstream) {
        Objects.requireNonNull(local, "local must not be null");
        Objects.requireNonNull(upstream, "upstream must not be null");

        final var diffMap = new LinkedHashMap<String, JsonValue>();

        // Extract class name safely
        final var localClassName = local.members().get("className");
        final var className = localClassName instanceof JsonString js ?
            js.value() : "Unknown";

        diffMap.put("className", JsonString.of(className));

        // Check for upstream errors
        if (upstream.members().containsKey("error")) {
            diffMap.put("status", JsonString.of("UPSTREAM_ERROR"));
            diffMap.put("error", upstream.members().get("error"));
            return JsonObject.of(diffMap);
        }

        // Check if status is NOT_IMPLEMENTED (from parsing)
        if (upstream.members().containsKey("status")) {
            final var status = ((JsonString) upstream.members().get("status")).value();
            if ("NOT_IMPLEMENTED".equals(status)) {
                diffMap.put("status", JsonString.of("PARSE_NOT_IMPLEMENTED"));
                return JsonObject.of(diffMap);
            }
        }

        // Perform detailed comparison
        final var differences = new ArrayList<JsonValue>();
        var hasChanges = false;

        // Compare basic class attributes
        hasChanges |= compareAttribute("isInterface", local, upstream, differences);
        hasChanges |= compareAttribute("isEnum", local, upstream, differences);
        hasChanges |= compareAttribute("isRecord", local, upstream, differences);
        hasChanges |= compareAttribute("isSealed", local, upstream, differences);

        // Compare modifiers
        hasChanges |= compareModifiers(local, upstream, differences);

        // Compare inheritance
        hasChanges |= compareInheritance(local, upstream, differences);

        // Compare methods
        hasChanges |= compareMethods(local, upstream, differences);

        // Compare fields
        hasChanges |= compareFields(local, upstream, differences);

        // Compare constructors
        hasChanges |= compareConstructors(local, upstream, differences);

        // Set status based on findings
        if (!hasChanges) {
            diffMap.put("status", JsonString.of("MATCHING"));
        } else {
            diffMap.put("status", JsonString.of("DIFFERENT"));
            diffMap.put("differences", JsonArray.of(differences));
        }

        return JsonObject.of(diffMap);
    }

    /// Compares a simple boolean attribute
    static boolean compareAttribute(String attrName, JsonObject local, JsonObject upstream, List<JsonValue> differences) {
        final var localValue = local.members().get(attrName);
        final var upstreamValue = upstream.members().get(attrName);

        if (!Objects.equals(localValue, upstreamValue)) {
            differences.add(JsonObject.of(Map.of(
                "type", JsonString.of("attributeChanged"),
                "attribute", JsonString.of(attrName),
                "local", localValue != null ? localValue : JsonBoolean.of(false),
                "upstream", upstreamValue != null ? upstreamValue : JsonBoolean.of(false)
            )));
            return true;
        }
        return false;
    }

    /// Compares class modifiers
    static boolean compareModifiers(JsonObject local, JsonObject upstream, List<JsonValue> differences) {
        final var localMods = (JsonArray) local.members().get("modifiers");
        final var upstreamMods = (JsonArray) upstream.members().get("modifiers");

        if (localMods == null || upstreamMods == null) {
            return false;
        }

        final var localSet = localMods.values().stream()
            .map(v -> ((JsonString) v).value())
            .collect(Collectors.toSet());
        final var upstreamSet = upstreamMods.values().stream()
            .map(v -> ((JsonString) v).value())
            .collect(Collectors.toSet());

        if (!localSet.equals(upstreamSet)) {
            differences.add(JsonObject.of(Map.of(
                "type", JsonString.of("modifiersChanged"),
                "local", localMods,
                "upstream", upstreamMods
            )));
            return true;
        }
        return false;
    }

    /// Compares inheritance hierarchy
    static boolean compareInheritance(JsonObject local, JsonObject upstream, List<JsonValue> differences) {
        final var localExtends = (JsonArray) local.members().get("extends");
        final var upstreamExtends = (JsonArray) upstream.members().get("extends");

        if (localExtends == null || upstreamExtends == null) {
            return false;
        }

        final var localTypes = localExtends.values().stream()
            .map(v -> normalizeTypeName(((JsonString) v).value()))
            .collect(Collectors.toSet());
        final var upstreamTypes = upstreamExtends.values().stream()
            .map(v -> normalizeTypeName(((JsonString) v).value()))
            .collect(Collectors.toSet());

        if (!localTypes.equals(upstreamTypes)) {
            differences.add(JsonObject.of(Map.of(
                "type", JsonString.of("inheritanceChanged"),
                "local", localExtends,
                "upstream", upstreamExtends
            )));
            return true;
        }
        return false;
    }

    /// Compares methods between local and upstream
    static boolean compareMethods(JsonObject local, JsonObject upstream, List<JsonValue> differences) {
        final var localMethods = (JsonObject) local.members().get("methods");
        final var upstreamMethods = (JsonObject) upstream.members().get("methods");

        if (localMethods == null || upstreamMethods == null) {
            return false;
        }

        var hasChanges = false;

        // Check for removed methods (in local but not upstream)
        for (final var entry : localMethods.members().entrySet()) {
            if (!upstreamMethods.members().containsKey(entry.getKey())) {
                differences.add(JsonObject.of(Map.of(
                    "type", JsonString.of("methodRemoved"),
                    "method", JsonString.of(entry.getKey()),
                    "details", entry.getValue()
                )));
                hasChanges = true;
            }
        }

        // Check for added methods (in upstream but not local)
        for (final var entry : upstreamMethods.members().entrySet()) {
            if (!localMethods.members().containsKey(entry.getKey())) {
                differences.add(JsonObject.of(Map.of(
                    "type", JsonString.of("methodAdded"),
                    "method", JsonString.of(entry.getKey()),
                    "details", entry.getValue()
                )));
                hasChanges = true;
            }
        }

        // Check for changed methods
        for (final var entry : localMethods.members().entrySet()) {
            final var methodName = entry.getKey();
            if (upstreamMethods.members().containsKey(methodName)) {
                final var localMethod = (JsonObject) entry.getValue();
                final var upstreamMethod = (JsonObject) upstreamMethods.members().get(methodName);

                if (!compareMethodSignature(localMethod, upstreamMethod)) {
                    differences.add(JsonObject.of(Map.of(
                        "type", JsonString.of("methodChanged"),
                        "method", JsonString.of(methodName),
                        "local", localMethod,
                        "upstream", upstreamMethod
                    )));
                    hasChanges = true;
                }
            }
        }

        return hasChanges;
    }

    /// Compares method signatures
    static boolean compareMethodSignature(JsonObject localMethod, JsonObject upstreamMethod) {
        // Compare return types
        final var localReturn = normalizeTypeName(((JsonString) localMethod.members().get("returnType")).value());
        final var upstreamReturn = normalizeTypeName(((JsonString) upstreamMethod.members().get("returnType")).value());
        if (!localReturn.equals(upstreamReturn)) {
            return false;
        }

        // Compare parameters
        final var localParams = (JsonArray) localMethod.members().get("parameters");
        final var upstreamParams = (JsonArray) upstreamMethod.members().get("parameters");

        if (localParams.values().size() != upstreamParams.values().size()) {
            return false;
        }

        // Compare each parameter
        for (int i = 0; i < localParams.values().size(); i++) {
            final var localParam = normalizeTypeName(((JsonString) localParams.values().get(i)).value());
            final var upstreamParam = normalizeTypeName(((JsonString) upstreamParams.values().get(i)).value());
            if (!localParam.equals(upstreamParam)) {
                return false;
            }
        }

        return true;
    }

    /// Compares fields between local and upstream
    static boolean compareFields(JsonObject local, JsonObject upstream, List<JsonValue> differences) {
        final var localFields = (JsonObject) local.members().get("fields");
        final var upstreamFields = (JsonObject) upstream.members().get("fields");

        if (localFields == null || upstreamFields == null) {
            return false;
        }

        var hasChanges = false;

        // Check for field differences
        final var localFieldNames = localFields.members().keySet();
        final var upstreamFieldNames = upstreamFields.members().keySet();

        if (!localFieldNames.equals(upstreamFieldNames)) {
            differences.add(JsonObject.of(Map.of(
                "type", JsonString.of("fieldsChanged"),
                "local", JsonArray.of(localFieldNames.stream().map(JsonString::of).collect(Collectors.<JsonValue>toList())),
                "upstream", JsonArray.of(upstreamFieldNames.stream().map(JsonString::of).collect(Collectors.<JsonValue>toList()))
            )));
            hasChanges = true;
        }

        return hasChanges;
    }

    /// Compares constructors between local and upstream
    static boolean compareConstructors(JsonObject local, JsonObject upstream, List<JsonValue> differences) {
        final var localConstructors = (JsonArray) local.members().get("constructors");
        final var upstreamConstructors = (JsonArray) upstream.members().get("constructors");

        if (localConstructors == null || upstreamConstructors == null) {
            return false;
        }

        if (localConstructors.values().size() != upstreamConstructors.values().size()) {
            differences.add(JsonObject.of(Map.of(
                "type", JsonString.of("constructorsChanged"),
                "localCount", JsonNumber.of(localConstructors.values().size()),
                "upstreamCount", JsonNumber.of(upstreamConstructors.values().size())
            )));
            return true;
        }

        return false;
    }

    /// Normalizes type names by removing package prefixes
    static String normalizeTypeName(String typeName) {
        // Handle generic types
        var normalized = typeName;

        // Replace jdk.sandbox.* with standard packages
        normalized = normalized.replace("jdk.sandbox.java.util.json", "java.util.json");
        normalized = normalized.replace("jdk.sandbox.internal.util.json", "jdk.internal.util.json");

        // Remove any remaining package prefixes for comparison
        if (normalized.contains(".")) {
            final var parts = normalized.split("\\.");
            normalized = parts[parts.length - 1];
        }

        return normalized;
    }

    /// Runs source-to-source comparison for fair parameter name comparison
    /// @return complete comparison report as JSON
    static JsonObject runFullComparison() {
        LOGGER.info(() -> "Starting full API comparison");
        final var startTime = Instant.now();

        final var reportMap = new LinkedHashMap<String, JsonValue>();
        reportMap.put("timestamp", JsonString.of(startTime.toString()));
        reportMap.put("localPackage", JsonString.of("jdk.sandbox.java.util.json"));
        reportMap.put("upstreamPackage", JsonString.of("java.util.json"));

        // Discover local classes
        final var localClasses = discoverLocalJsonClasses();
        LOGGER.info(() -> "Found " + localClasses.size() + " local classes");

        // Extract and compare APIs
        final var differences = new ArrayList<JsonValue>();
        var matchingCount = 0;
        var missingUpstream = 0;
        var differentApi = 0;

        for (final var clazz : localClasses) {
            final var className = clazz.getName();
            final var localApi = extractLocalApiFromSource(className);
            final var upstreamSource = fetchUpstreamSource(className);
            final var upstreamApi = extractApiFromSource(upstreamSource, className);
            final var diff = compareApis(localApi, upstreamApi);
            differences.add(diff);

            // Count statistics
            final var status = ((JsonString) diff.members().get("status")).value();
            switch (status) {
                case "MATCHING" -> matchingCount++;
                case "UPSTREAM_ERROR" -> missingUpstream++;
                case "DIFFERENT" -> differentApi++;
            }
        }

        // Build summary
        final var summary = JsonObject.of(Map.of(
            "totalClasses", JsonNumber.of(localClasses.size()),
            "matchingClasses", JsonNumber.of(matchingCount),
            "missingUpstream", JsonNumber.of(missingUpstream),
            "differentApi", JsonNumber.of(differentApi)
        ));

        reportMap.put("summary", summary);
        reportMap.put("differences", JsonArray.of(differences));


        final var duration = Duration.between(startTime, Instant.now());
        reportMap.put("durationMs", JsonNumber.of(duration.toMillis()));

        LOGGER.info(() -> "Comparison completed in " + duration.toMillis() + "ms");

        return JsonObject.of(reportMap);
    }

    /// Fetches single upstream source file
    static String fetchUpstreamSource(String className) {
        final var cached = FETCH_CACHE.get(className);
        if (cached != null) {
            return cached;
        }

        final var upstreamPath = mapToUpstreamPath(className);
        final var url = GITHUB_BASE_URL + upstreamPath;
        final var source = fetchFromUrl(url);
        FETCH_CACHE.put(className, source);
        return source;
    }
}