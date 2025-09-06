// Compact single-file Java 25 script to refresh and compare impl sources
// Run: java RefreshFromUpstream.java

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

import javax.tools.*;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

record MethodSig(String modifiers, String returnType, String name, List<String> paramTypes) implements Comparable<MethodSig> {
    String key() { return name + "(" + String.join(",", paramTypes) + ")"; }
    public int compareTo(MethodSig o) { return (key() + ":" + returnType + ":" + modifiers).compareTo(o.key() + ":" + o.returnType + ":" + o.modifiers); }
    public String toString() { return modifiers + " " + returnType + " " + name + "(" + String.join(", ", paramTypes) + ")"; }
}

record ClassReport(String className, SortedSet<MethodSig> localMethods, SortedSet<MethodSig> upstreamMethods) {}

void main() throws Exception {
    var start = Instant.now();

    // Local repo paths
    Path repoRoot = Paths.get("").toAbsolutePath().normalize();
    Path localImplDir = repoRoot.resolve("json-java21/src/main/java/jdk/sandbox/internal/util/json");
    if (!Files.isDirectory(localImplDir)) {
        System.err.println("Local impl dir not found: " + localImplDir);
        System.exit(1);
    }

    // Work dirs (not checked in)
    Path updatesDir = repoRoot.resolve("updates/2025-09-04");
    Path upstreamDir = updatesDir.resolve("upstream/jdk.internal.util.json");
    Path reportDir = updatesDir.resolve("reports");
    Files.createDirectories(upstreamDir);
    Files.createDirectories(reportDir);

    // Upstream raw base for impl package
    String upstreamBase = "https://raw.githubusercontent.com/openjdk/jdk-sandbox/refs/heads/json/src/java.base/share/classes/jdk/internal/util/json/";

    // Discover local impl files
    List<Path> localFiles;
    try (var stream = Files.list(localImplDir)) {
        localFiles = stream
            .filter(p -> p.getFileName().toString().endsWith(".java"))
            .sorted()
            .toList();
    }

    System.out.println("=== Refresh From Upstream (impl) ===");
    System.out.println("Local impl dir: " + localImplDir);
    System.out.println("Upstream base:  " + upstreamBase);
    System.out.println("Files detected:  " + localFiles.size());
    System.out.println();

    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    List<ClassReport> reports = new ArrayList<>();

    for (var localPath : localFiles) {
        var fileName = localPath.getFileName().toString();
        var simpleName = fileName.substring(0, fileName.length() - 5); // strip .java

        // Fetch upstream source
        var url = upstreamBase + fileName;
        var upstreamTarget = upstreamDir.resolve(fileName);
        String upstreamSource = fetch(http, url);
        if (upstreamSource.startsWith("HTTP_ERROR:") || upstreamSource.startsWith("FETCH_ERROR:")) {
            System.err.println("WARN: could not fetch upstream for " + fileName + ": " + upstreamSource);
            // Still write a marker file for traceability
            Files.writeString(upstreamTarget, "// " + upstreamSource + "\n", StandardCharsets.UTF_8);
        } else {
            Files.writeString(upstreamTarget, upstreamSource, StandardCharsets.UTF_8);
        }

        // Read local source
        var localSource = Files.readString(localPath);

        // Normalize package lines away for signature comparison only
        var normLocal = stripPackage(localSource);
        var normUpstream = stripPackage(upstreamSource);

        var localMethods = extractMethods(simpleName, normLocal);
        var upstreamMethods = extractMethods(simpleName, normUpstream);

        reports.add(new ClassReport(simpleName, localMethods, upstreamMethods));
    }

    // Compute diffs and write report
    var sb = new StringBuilder();
    sb.append("# Impl Diff Report\n");
    sb.append("Generated: ").append(Instant.now()).append("\n\n");

    int changed = 0;
    for (var r : reports) {
        var localByKey = r.localMethods.stream().collect(Collectors.toMap(MethodSig::key, m -> m, (a,b) -> a, TreeMap::new));
        var upByKey = r.upstreamMethods.stream().collect(Collectors.toMap(MethodSig::key, m -> m, (a,b) -> a, TreeMap::new));

        var added = new TreeMap<String, MethodSig>();
        var removed = new TreeMap<String, MethodSig>();
        var modifiedReturnOrMods = new TreeMap<String, String>();

        for (var e : upByKey.entrySet()) if (!localByKey.containsKey(e.getKey())) added.put(e.getKey(), e.getValue());
        for (var e : localByKey.entrySet()) if (!upByKey.containsKey(e.getKey())) removed.put(e.getKey(), e.getValue());
        for (var k : localByKey.keySet()) {
            if (upByKey.containsKey(k)) {
                var l = localByKey.get(k); var u = upByKey.get(k);
                if (!Objects.equals(l.returnType(), u.returnType()) || !Objects.equals(l.modifiers(), u.modifiers())) {
                    modifiedReturnOrMods.put(k, "local=[" + l.returnType()+" "+l.modifiers()+"], upstream=["+u.returnType()+" "+u.modifiers()+"]");
                }
            }
        }

        if (!added.isEmpty() || !removed.isEmpty() || !modifiedReturnOrMods.isEmpty()) {
            changed++;
            sb.append("## ").append(r.className).append("\n");
            sb.append("Local:    ").append(localImplDir.resolve(r.className + ".java")).append("\n");
            sb.append("Upstream: ").append(upstreamDir.resolve(r.className + ".java")).append("\n\n");

            if (!added.isEmpty()) {
                sb.append("- Added methods (upstream-only):\n");
                added.values().forEach(m -> sb.append("  + ").append(m).append("\n"));
            }
            if (!removed.isEmpty()) {
                sb.append("- Removed methods (local-only):\n");
                removed.values().forEach(m -> sb.append("  - ").append(m).append("\n"));
            }
            if (!modifiedReturnOrMods.isEmpty()) {
                sb.append("- Modified return/modifiers (same name/params):\n");
                modifiedReturnOrMods.forEach((k,v) -> sb.append("  * ").append(k).append(": ").append(v).append("\n"));
            }
            sb.append("\n");
        }
    }

    if (changed == 0) {
        sb.append("No signature deltas detected across impl files.\n");
    }

    var reportPath = reportDir.resolve("impl-diff-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".md");
    Files.writeString(reportPath, sb.toString(), StandardCharsets.UTF_8);

    System.out.println("Report written: " + reportPath);
    System.out.println("Upstream sources at: " + upstreamDir);
    System.out.println("Elapsed: " + Duration.between(start, Instant.now()).toMillis() + " ms");
}

static String fetch(HttpClient http, String url) {
    try {
        var req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) return res.body();
        if (res.statusCode() == 404) return "HTTP_ERROR:404 Not Found";
        return "HTTP_ERROR:" + res.statusCode();
    } catch (Exception e) {
        return "FETCH_ERROR:" + e.getMessage();
    }
}

static String stripPackage(String source) {
    if (source == null) return "";
    return Arrays.stream(source.split("\n"))
        .filter(l -> !l.startsWith("package "))
        .collect(Collectors.joining("\n"));
}

static SortedSet<MethodSig> extractMethods(String expectedSimpleName, String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) throw new IllegalStateException("JavaCompiler not available");
    var fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
    var cu = List.of(new StringJavaFileObject(expectedSimpleName, source));
    var options = List.of("-proc:none", "-Xlint:none");
    var task = (JavacTask) compiler.getTask(null, fm, null, options, null, cu);
    var trees = task.parse();

    var sigs = new TreeSet<MethodSig>();
    for (var tree : trees) {
        new TreeScanner<Void, Void>(){
            @Override public Void visitClass(ClassTree node, Void p) {
                return super.visitClass(node, p);
            }
            @Override public Void visitMethod(MethodTree m, Void p) {
                // Skip synthetic or initializer blocks (no name)
                var name = m.getName() == null ? "" : m.getName().toString();
                if (name.isEmpty() || name.equals("<init>")) {
                    // Treat constructors as methods with name == class
                    name = expectedSimpleName;
                }
                var mods = m.getModifiers() == null ? "" : String.join(" ", m.getModifiers().getFlags().stream().map(Enum::name).map(String::toLowerCase).toList());
                var ret = m.getReturnType() == null ? expectedSimpleName : m.getReturnType().toString();
                var params = m.getParameters().stream().map(v -> v.getType().toString()).toList();
                sigs.add(new MethodSig(mods, ret, name, params));
                return null;
            }
        }.scan(tree, null);
    }
    return sigs;
}

static final class StringJavaFileObject extends SimpleJavaFileObject {
    private final String code;
    StringJavaFileObject(String name, String code) {
        super(URI.create("string:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
        this.code = code;
    }
    @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
}
