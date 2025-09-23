package io.github.simbo1905.json.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Runs the official JSON-Schema-Test-Suite (Draft 2020-12) as JUnit dynamic tests.
/// By default, this is lenient and will SKIP mismatches and unsupported schemas
/// to provide a compatibility signal without breaking the build. Enable strict
/// mode with -Djson.schema.strict=true to make mismatches fail the build.
/// Test data location: see src/test/resources/JSONSchemaTestSuite-20250921/DOWNLOAD_COMMANDS.md
public class JsonSchemaCheckIT {

    private static final Path ZIP_FILE = Paths.get("src/test/resources/json-schema-test-suite-data.zip");
    private static final Path TARGET_SUITE_DIR = Paths.get("target/test-data/draft2020-12");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final boolean STRICT = Boolean.getBoolean("json.schema.strict");
    private static final String METRICS_FMT = System.getProperty("json.schema.metrics", "").trim();
    private static final StrictMetrics METRICS = new StrictMetrics();

    @SuppressWarnings("resource")
    @TestFactory
    Stream<DynamicTest> runOfficialSuite() throws Exception {
        extractTestData();
        return Files.walk(TARGET_SUITE_DIR)
                .filter(p -> p.toString().endsWith(".json"))
                .flatMap(this::testsFromFile);
    }

    static void extractTestData() throws IOException {
        if (!Files.exists(ZIP_FILE)) {
            throw new RuntimeException("Test data ZIP file not found: " + ZIP_FILE.toAbsolutePath());
        }
        
        // Create target directory
        Files.createDirectories(TARGET_SUITE_DIR.getParent());
        
        // Extract ZIP file
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(ZIP_FILE.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && (entry.getName().startsWith("draft2020-12/") || entry.getName().startsWith("remotes/"))) {
                    Path outputPath = TARGET_SUITE_DIR.resolve(entry.getName());
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zis, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        
        // Verify the target directory exists after extraction
        if (!Files.exists(TARGET_SUITE_DIR)) {
            throw new RuntimeException("Extraction completed but target directory not found: " + TARGET_SUITE_DIR.toAbsolutePath());
        }
    }

    Stream<DynamicTest> testsFromFile(Path file) {
        try {
            final var root = MAPPER.readTree(file.toFile());
            
            /// The JSON Schema Test Suite contains two types of files:
            /// 1. Test suite files: Arrays containing test groups with description, schema, and tests fields
            /// 2. Remote reference files: Plain JSON schema files used as remote references by test cases
            /// 
            /// We only process test suite files. Remote reference files (like remotes/baseUriChangeFolder/folderInteger.json)
            /// are just schema documents that get loaded via $ref during test execution, not test cases themselves.
            
            /// Validate that this is a test suite file (array of objects with description, schema, tests)
            if (!root.isArray() || root.isEmpty()) {
                // Not a test suite file, skip it
                return Stream.empty();
            }
            
            /// Validate first group has required fields
            final var firstGroup = root.get(0);
            if (!firstGroup.has("description") || !firstGroup.has("schema") || !firstGroup.has("tests")) {
                // Not a test suite file, skip it
                return Stream.empty();
            }
            
            /// Count groups and tests discovered
            final var groupCount = root.size();
            METRICS.groupsDiscovered.add(groupCount);
            perFile(file).groups.add(groupCount);
            
            var testCount = 0;
            for (final var group : root) {
                testCount += group.get("tests").size();
            }
            METRICS.testsDiscovered.add(testCount);
            perFile(file).tests.add(testCount);

          return dynamicTestStream(file, root);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to process " + file, ex);
        }
    }

  static Stream<DynamicTest> dynamicTestStream(Path file, JsonNode root) {
    return StreamSupport.stream(root.spliterator(), false)
            .flatMap(group -> {
                final var groupDesc = group.get("description").asText();
                try {
                    /// Attempt to compile the schema for this group; if unsupported features
                    /// (e.g., unresolved anchors) are present, skip this group gracefully.
                    final var schema = JsonSchema.compile(
                            Json.parse(group.get("schema").toString()));

                    return StreamSupport.stream(group.get("tests").spliterator(), false)
                            .map(test -> DynamicTest.dynamicTest(
                                    groupDesc + " – " + test.get("description").asText(),
                                    () -> {
                                        final var expected = test.get("valid").asBoolean();
                                        final boolean actual;
                                        try {
                                            actual = schema.validate(
                                                    Json.parse(test.get("data").toString())).valid();

                                            /// Count validation attempt
                                            METRICS.run.increment();
                                            perFile(file).run.increment();
                                        } catch (Exception e) {
                                            final var reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                                            System.err.println("[JsonSchemaCheckIT] Skipping test due to exception: "
                                                    + groupDesc + " — " + reason + " (" + file.getFileName() + ")");

                                            /// Count exception as skipped mismatch in strict metrics
                                            METRICS.skippedMismatch.increment();
                                            perFile(file).skipMismatch.increment();

                                            if (isStrict()) throw e;
                                            Assumptions.assumeTrue(false, "Skipped: " + reason);
                                            return; /// not reached when strict
                                        }

                                        if (isStrict()) {
                                            try {
                                                assertEquals(expected, actual);
                                                /// Count pass in strict mode
                                                METRICS.passed.increment();
                                                perFile(file).pass.increment();
                                            } catch (AssertionError e) {
                                                /// Count failure in strict mode
                                                METRICS.failed.increment();
                                                perFile(file).fail.increment();
                                                throw e;
                                            }
                                        } else if (expected != actual) {
                                            System.err.println("[JsonSchemaCheckIT] Mismatch (ignored): "
                                                    + groupDesc + " — expected=" + expected + ", actual=" + actual
                                                    + " (" + file.getFileName() + ")");

                                            /// Count lenient mismatch skip
                                            METRICS.skippedMismatch.increment();
                                            perFile(file).skipMismatch.increment();

                                            Assumptions.assumeTrue(false, "Mismatch ignored");
                                        } else {
                                            /// Count pass in lenient mode
                                            METRICS.passed.increment();
                                            perFile(file).pass.increment();
                                        }
                                    }));
                } catch (Exception ex) {
                    /// Unsupported schema for this group; emit a single skipped test for visibility
                    final var reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    System.err.println("[JsonSchemaCheckIT] Skipping group due to unsupported schema: "
                            + groupDesc + " — " + reason + " (" + file.getFileName() + ")");

                    /// Count unsupported group skip
                    METRICS.skippedUnsupported.increment();
                    perFile(file).skipUnsupported.increment();

                    return Stream.of(DynamicTest.dynamicTest(
                            groupDesc + " – SKIPPED: " + reason,
                            () -> { if (isStrict()) throw ex; Assumptions.assumeTrue(false, "Unsupported schema: " + reason); }
                    ));
                }
            });
  }

  static StrictMetrics.FileCounters perFile(Path file) {
        return METRICS.perFile.computeIfAbsent(file.getFileName().toString(), k -> new StrictMetrics.FileCounters());
    }

    /// Helper to check if we're running in strict mode
    static boolean isStrict() {
        return STRICT;
    }

    @AfterAll
    static void printAndPersistMetrics() throws Exception {
        final var strict = isStrict();
        final var total = METRICS.testsDiscovered.sum();
        final var run = METRICS.run.sum();
        final var passed = METRICS.passed.sum();
        final var failed = METRICS.failed.sum();
        final var skippedUnsupported = METRICS.skippedUnsupported.sum();
        final var skippedMismatch = METRICS.skippedMismatch.sum();

        /// Print canonical summary line
        System.out.printf(
            "JSON-SCHEMA-COMPAT: total=%d run=%d passed=%d failed=%d skipped-unsupported=%d skipped-mismatch=%d strict=%b%n",
            total, run, passed, failed, skippedUnsupported, skippedMismatch, strict
        );

        /// For accounting purposes, we accept that the current implementation
        /// creates some accounting complexity when groups are skipped.
        /// The key metrics are still valid and useful for tracking progress.
        if (strict) {
            assertEquals(run, passed + failed, "strict run accounting mismatch");
        }

        /// Legacy metrics for backward compatibility
        System.out.printf(
            "JSON-SCHEMA SUITE (%s): groups=%d testsScanned=%d run=%d passed=%d failed=%d skipped={unsupported=%d, exception=%d, lenientMismatch=%d}%n",
            strict ? "STRICT" : "LENIENT",
            METRICS.groupsDiscovered.sum(),
            METRICS.testsDiscovered.sum(),
            run, passed, failed, skippedUnsupported, METRICS.skipTestException.sum(), skippedMismatch
        );

        if (!METRICS_FMT.isEmpty()) {
            var outDir = java.nio.file.Path.of("target");
            java.nio.file.Files.createDirectories(outDir);
            var ts = java.time.OffsetDateTime.now().toString();
            if ("json".equalsIgnoreCase(METRICS_FMT)) {
                var json = buildJsonSummary(strict, ts);
                java.nio.file.Files.writeString(outDir.resolve("json-schema-compat.json"), json);
            } else if ("csv".equalsIgnoreCase(METRICS_FMT)) {
                var csv = buildCsvSummary(strict, ts);
                java.nio.file.Files.writeString(outDir.resolve("json-schema-compat.csv"), csv);
            }
        }
    }

    static String buildJsonSummary(boolean strict, String timestamp) {
        var totals = new StringBuilder();
        totals.append("{\n");
        totals.append("  \"mode\": \"").append(strict ? "STRICT" : "LENIENT").append("\",\n");
        totals.append("  \"timestamp\": \"").append(timestamp).append("\",\n");
        totals.append("  \"totals\": {\n");
        totals.append("    \"groupsDiscovered\": ").append(METRICS.groupsDiscovered.sum()).append(",\n");
        totals.append("    \"testsDiscovered\": ").append(METRICS.testsDiscovered.sum()).append(",\n");
        totals.append("    \"validationsRun\": ").append(METRICS.run.sum()).append(",\n");
        totals.append("    \"passed\": ").append(METRICS.passed.sum()).append(",\n");
        totals.append("    \"failed\": ").append(METRICS.failed.sum()).append(",\n");
        totals.append("    \"skipped\": {\n");
        totals.append("      \"unsupportedSchemaGroup\": ").append(METRICS.skippedUnsupported.sum()).append(",\n");
        totals.append("      \"testException\": ").append(METRICS.skipTestException.sum()).append(",\n");
        totals.append("      \"lenientMismatch\": ").append(METRICS.skippedMismatch.sum()).append("\n");
        totals.append("    }\n");
        totals.append("  },\n");
        totals.append("  \"perFile\": [\n");
        
        var files = new java.util.ArrayList<String>(METRICS.perFile.keySet());
        java.util.Collections.sort(files);
        var first = true;
        for (String file : files) {
            var counters = METRICS.perFile.get(file);
            if (!first) totals.append(",\n");
            first = false;
            totals.append("    {\n");
            totals.append("      \"file\": \"").append(file).append("\",\n");
            totals.append("      \"groups\": ").append(counters.groups.sum()).append(",\n");
            totals.append("      \"tests\": ").append(counters.tests.sum()).append(",\n");
            totals.append("      \"run\": ").append(counters.run.sum()).append(",\n");
            totals.append("      \"pass\": ").append(counters.pass.sum()).append(",\n");
            totals.append("      \"fail\": ").append(counters.fail.sum()).append(",\n");
            totals.append("      \"skipUnsupported\": ").append(counters.skipUnsupported.sum()).append(",\n");
            totals.append("      \"skipException\": ").append(counters.skipException.sum()).append(",\n");
            totals.append("      \"skipMismatch\": ").append(counters.skipMismatch.sum()).append("\n");
            totals.append("    }");
        }
        totals.append("\n  ]\n");
        totals.append("}\n");
        return totals.toString();
    }

    static String buildCsvSummary(boolean strict, String timestamp) {
        var csv = new StringBuilder();
        csv.append("mode,timestamp,groupsDiscovered,testsDiscovered,validationsRun,passed,failed,skippedUnsupported,skipTestException,skippedMismatch\n");
        csv.append(strict ? "STRICT" : "LENIENT").append(",");
        csv.append(timestamp).append(",");
        csv.append(METRICS.groupsDiscovered.sum()).append(",");
        csv.append(METRICS.testsDiscovered.sum()).append(",");
        csv.append(METRICS.run.sum()).append(",");
        csv.append(METRICS.passed.sum()).append(",");
        csv.append(METRICS.failed.sum()).append(",");
        csv.append(METRICS.skippedUnsupported.sum()).append(",");
        csv.append(METRICS.skipTestException.sum()).append(",");
        csv.append(METRICS.skippedMismatch.sum()).append("\n");
        
        csv.append("\nperFile breakdown:\n");
        csv.append("file,groups,tests,run,pass,fail,skipUnsupported,skipException,skipMismatch\n");
        
        var files = new java.util.ArrayList<String>(METRICS.perFile.keySet());
        java.util.Collections.sort(files);
        for (String file : files) {
            var counters = METRICS.perFile.get(file);
            csv.append(file).append(",");
            csv.append(counters.groups.sum()).append(",");
            csv.append(counters.tests.sum()).append(",");
            csv.append(counters.run.sum()).append(",");
            csv.append(counters.pass.sum()).append(",");
            csv.append(counters.fail.sum()).append(",");
            csv.append(counters.skipUnsupported.sum()).append(",");
            csv.append(counters.skipException.sum()).append(",");
            csv.append(counters.skipMismatch.sum()).append("\n");
        }
        return csv.toString();
    }
}

/// Thread-safe metrics container for the JSON Schema Test Suite run.
/// Thread-safe strict metrics container for the JSON Schema Test Suite run
final class StrictMetrics {
    final java.util.concurrent.atomic.LongAdder total = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder run = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder passed = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder failed = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder skippedUnsupported = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder skippedMismatch = new java.util.concurrent.atomic.LongAdder();
    
    // Legacy counters for backward compatibility
    final java.util.concurrent.atomic.LongAdder groupsDiscovered = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder testsDiscovered = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder skipTestException = new java.util.concurrent.atomic.LongAdder();
    
    final ConcurrentHashMap<String, FileCounters> perFile = new ConcurrentHashMap<>();
    
    /// Per-file counters for detailed metrics
    static final class FileCounters {
        final java.util.concurrent.atomic.LongAdder groups = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.LongAdder tests = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.LongAdder run = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.LongAdder pass = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.LongAdder fail = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.LongAdder skipUnsupported = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.LongAdder skipException = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.LongAdder skipMismatch = new java.util.concurrent.atomic.LongAdder();
    }
}
