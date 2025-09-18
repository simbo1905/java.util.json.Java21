package jdk.sandbox.compatibility;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonParseException;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/// Generates a conformance summary report.
/// Run with: mvn exec:java -pl json-compatibility-suite
public class JsonTestSuiteSummary {

    private static final Logger LOGGER = Logger.getLogger(JsonTestSuiteSummary.class.getName());
    private static final Path TEST_DIR = Paths.get("json-compatibility-suite/target/test-resources/JSONTestSuite-master/test_parsing");

    public static void main(String[] args) throws Exception {
        boolean jsonOutput = args.length > 0 && "--json".equals(args[0]);
        JsonTestSuiteSummary summary = new JsonTestSuiteSummary();
        if (jsonOutput) {
            summary.generateJsonReport();
        } else {
            summary.generateConformanceReport();
        }
    }

    void generateConformanceReport() throws Exception {
        TestResults results = runTests();
        
        System.out.println("\n=== JSON Test Suite Conformance Report ===");
        System.out.println("Repository: java.util.json backport");
        System.out.printf("Test files analyzed: %d%n", results.totalFiles);
        System.out.printf("Files skipped (could not read): %d%n%n", results.skippedFiles);
        
        System.out.println("Valid JSON (y_ files):");
        System.out.printf("  Passed: %d%n", results.yPass);
        System.out.printf("  Failed: %d%n", results.yFail);
        System.out.printf("  Success rate: %.1f%%%n%n", 100.0 * results.yPass / (results.yPass + results.yFail));
        
        System.out.println("Invalid JSON (n_ files):");
        System.out.printf("  Correctly rejected: %d%n", results.nPass);
        System.out.printf("  Incorrectly accepted: %d%n", results.nFail);
        System.out.printf("  Success rate: %.1f%%%n%n", 100.0 * results.nPass / (results.nPass + results.nFail));
        
        System.out.println("Implementation-defined (i_ files):");
        System.out.printf("  Accepted: %d%n", results.iAccept);
        System.out.printf("  Rejected: %d%n%n", results.iReject);
        
        double conformance = 100.0 * (results.yPass + results.nPass) / (results.yPass + results.yFail + results.nPass + results.nFail);
        System.out.printf("Overall Conformance: %.1f%%%n", conformance);
        
        if (!results.shouldPassButFailed.isEmpty()) {
            System.out.println("\n⚠️  Valid JSON that failed to parse:");
            results.shouldPassButFailed.forEach(f -> System.out.println("  - " + f));
        }
        
        if (!results.shouldFailButPassed.isEmpty()) {
            System.out.println("\n⚠️  Invalid JSON that was incorrectly accepted:");
            results.shouldFailButPassed.forEach(f -> System.out.println("  - " + f));
        }
        
        if (results.shouldPassButFailed.isEmpty() && results.shouldFailButPassed.isEmpty()) {
            System.out.println("\n✅ Perfect conformance!");
        }
    }

    void generateJsonReport() throws Exception {
        TestResults results = runTests();
        JsonObject report = createJsonReport(results);
        System.out.println(Json.toDisplayString(report, 2));
    }

    private TestResults runTests() throws Exception {
        if (!Files.exists(TEST_DIR)) {
            throw new RuntimeException("Test suite not downloaded. Run: ./mvnw clean compile generate-test-resources -pl json-compatibility-suite");
        }
        
        List<String> shouldPassButFailed = new ArrayList<>();
        List<String> shouldFailButPassed = new ArrayList<>();
        List<String> skippedFiles = new ArrayList<>();
        
        int yPass = 0, yFail = 0;
        int nPass = 0, nFail = 0;
        int iAccept = 0, iReject = 0;
        
        var files = Files.walk(TEST_DIR)
            .filter(p -> p.toString().endsWith(".json"))
            .sorted()
            .toList();
            
        for (Path file : files) {
            String filename = file.getFileName().toString();
            String content = null;
            char[] charContent = null;
            
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
                charContent = content.toCharArray();
            } catch (MalformedInputException e) {
                LOGGER.warning(() -> "UTF-8 failed for " + filename + ", using robust encoding detection");
                try {
                    byte[] rawBytes = Files.readAllBytes(file);
                    charContent = RobustCharDecoder.decodeToChars(rawBytes, filename);
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to read test file " + filename + " - this is a fundamental I/O failure, not an encoding issue: " + ex.getMessage(), ex);
                }
            }
            
            // Test with char[] API (always available)
            boolean parseSucceeded = false;
            try {
                Json.parse(charContent);
                parseSucceeded = true;
            } catch (JsonParseException e) {
                parseSucceeded = false;
            } catch (StackOverflowError e) {
                LOGGER.severe(() -> "ERROR: StackOverflowError security vulnerability on file: " + filename);
                parseSucceeded = false; // Treat as parse failure
            }
            
            // Update counters based on results
            if (parseSucceeded) {
                if (filename.startsWith("y_")) {
                    yPass++;
                } else if (filename.startsWith("n_")) {
                    nFail++;
                    shouldFailButPassed.add(filename);
                } else if (filename.startsWith("i_")) {
                    iAccept++;
                }
            } else {
                if (filename.startsWith("y_")) {
                    yFail++;
                    shouldPassButFailed.add(filename);
                } else if (filename.startsWith("n_")) {
                    nPass++;
                } else if (filename.startsWith("i_")) {
                    iReject++;
                }
            }
        }
        
        return new TestResults(files.size(), skippedFiles.size(), 
            yPass, yFail, nPass, nFail, iAccept, iReject,
            shouldPassButFailed, shouldFailButPassed, skippedFiles);
    }

    private JsonObject createJsonReport(TestResults results) {
        double ySuccessRate = 100.0 * results.yPass / (results.yPass + results.yFail);
        double nSuccessRate = 100.0 * results.nPass / (results.nPass + results.nFail);
        double conformance = 100.0 * (results.yPass + results.nPass) / (results.yPass + results.yFail + results.nPass + results.nFail);

        return JsonObject.of(java.util.Map.of(
            "repository", JsonString.of("java.util.json backport"),
            "filesAnalyzed", JsonNumber.of(results.totalFiles),
            "filesSkipped", JsonNumber.of(results.skippedFiles),
            "validJson", JsonObject.of(java.util.Map.of(
                "passed", JsonNumber.of(results.yPass),
                "failed", JsonNumber.of(results.yFail),
                "successRate", JsonNumber.of(Math.round(ySuccessRate * 10) / 10.0)
            )),
            "invalidJson", JsonObject.of(java.util.Map.of(
                "correctlyRejected", JsonNumber.of(results.nPass),
                "incorrectlyAccepted", JsonNumber.of(results.nFail),
                "successRate", JsonNumber.of(Math.round(nSuccessRate * 10) / 10.0)
            )),
            "implementationDefined", JsonObject.of(java.util.Map.of(
                "accepted", JsonNumber.of(results.iAccept),
                "rejected", JsonNumber.of(results.iReject)
            )),
            "overallConformance", JsonNumber.of(Math.round(conformance * 10) / 10.0),
            "shouldPassButFailed", JsonArray.of(results.shouldPassButFailed.stream()
                .map(JsonString::of)
                .toList()),
            "shouldFailButPassed", JsonArray.of(results.shouldFailButPassed.stream()
                .map(JsonString::of)
                .toList())
        ));
    }

    private record TestResults(
        int totalFiles, int skippedFiles,
        int yPass, int yFail, int nPass, int nFail, int iAccept, int iReject,
        List<String> shouldPassButFailed, List<String> shouldFailButPassed, List<String> skippedFiles2
    ) {}
}
