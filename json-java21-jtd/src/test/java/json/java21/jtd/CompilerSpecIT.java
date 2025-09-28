package json.java21.jtd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Runs the official JTD Test Suite invalid schema tests as JUnit dynamic tests.
///
/// This test class loads and runs invalid schema tests from the official JTD specification.
/// Each test case is a schema that violates JTD rules and should cause compilation to fail
/// with a deterministic error message.
///
/// Test Format:
/// ```json
/// {
///   "null schema": null,
///   "boolean schema": true,
///   "illegal keyword": {"foo": 123},
///   "discriminator with non-properties mapping": {
///     "discriminator": "kind",
///     "mapping": {
///       "bool": {"type": "boolean"}
///     }
///   }
/// }
/// ```
///
/// The test suite is extracted from the embedded ZIP file and run as dynamic tests.
/// All tests must fail compilation for RFC 8927 compliance.
public class CompilerSpecIT extends JtdTestBase {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path INVALID_SCHEMAS_FILE = Paths.get("target/test-data/json-typedef-spec-2025-09-27/tests/invalid_schemas.json");

  /// Metrics tracking for test results
  private static int totalTests = 0;
  private static int passedTests = 0;
  private static int failedTests = 0;

  @AfterAll
  static void printMetrics() {
    LOG.info(() -> String.format("JTD-COMPILER-SPEC: total=%d passed=%d failed=%d", 
                                 totalTests, passedTests, failedTests));
    
    // RFC compliance: all tests must fail compilation
    assertEquals(totalTests, passedTests + failedTests, "Test accounting mismatch");
  }

  @TestFactory
  Stream<DynamicTest> runInvalidSchemaTests() throws Exception {
    LOG.info(() -> "Running JTD Invalid Schema Compilation Tests");
    
    // Ensure test data is extracted
    extractTestData();
    
    return runInvalidSchemaCompilationTests();
  }

  private Stream<DynamicTest> runInvalidSchemaCompilationTests() throws Exception {
    LOG.info(() -> "Running invalid schema compilation tests from: " + INVALID_SCHEMAS_FILE);
    JsonNode invalidSchemas = loadTestFile(INVALID_SCHEMAS_FILE);
    
    return StreamSupport.stream(((Iterable<Map.Entry<String, JsonNode>>) invalidSchemas::fields).spliterator(), false)
        .map(entry -> {
          String testName = "invalid schema: " + entry.getKey();
          JsonNode schema = entry.getValue();
          return createInvalidSchemaTest(testName, schema);
        });
  }

  private void extractTestData() throws IOException {
    // Check if test data is already extracted
    if (Files.exists(INVALID_SCHEMAS_FILE)) {
      LOG.fine(() -> "JTD invalid schemas test suite already extracted at: " + INVALID_SCHEMAS_FILE);
      return;
    }

    // Extract the embedded test suite
    Path zipFile = Paths.get("src/test/resources/jtd-test-suite.zip");
    Path targetDir = Paths.get("target/test-data");
    
    if (!Files.exists(zipFile)) {
      throw new RuntimeException("JTD test suite ZIP not found: " + zipFile.toAbsolutePath());
    }

    LOG.info(() -> "Extracting JTD test suite from: " + zipFile);
    
    // Create target directory
    Files.createDirectories(targetDir);

    // Extract ZIP file
    try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
      java.util.zip.ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().startsWith("json-typedef-spec-")) {
          Path outputPath = targetDir.resolve(entry.getName());
          Files.createDirectories(outputPath.getParent());
          Files.copy(zis, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        zis.closeEntry();
      }
    }

    if (!Files.exists(INVALID_SCHEMAS_FILE)) {
      throw new RuntimeException("Extraction completed but test file not found: " + INVALID_SCHEMAS_FILE);
    }
  }

  @SuppressWarnings("SameParameterValue")
  private JsonNode loadTestFile(Path testFile) throws IOException {
    if (!Files.exists(testFile)) {
      throw new RuntimeException("JTD test file not found: " + testFile);
    }
    
    LOG.fine(() -> "Loading JTD test file from: " + testFile);
    return MAPPER.readTree(Files.newInputStream(testFile));
  }

  private DynamicTest createInvalidSchemaTest(String testName, JsonNode schemaNode) {
    return DynamicTest.dynamicTest(testName, () -> {
      totalTests++;
      
      // INFO level logging as required by AGENTS.md - announce test execution
      LOG.info(() -> "EXECUTING: " + testName);
      
      try {
        // FINE level logging for test details
        LOG.fine(() -> String.format("Invalid schema test details - schema: %s", schemaNode));
        
        // Convert to java.util.json format
        JsonValue schema = Json.parse(schemaNode.toString());
        
        // Create validator and attempt compilation - this should fail
        Jtd validator = new Jtd();
        
        // Expect compilation to fail with IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> validator.compile(schema),
          "Expected compilation to fail for invalid schema"
        );
        
        // FINE level logging for compilation failure
        LOG.fine(() -> String.format("Compilation failed as expected for %s - error: %s", testName, exception.getMessage()));
        
        passedTests++;
        
      } catch (AssertionError e) {
        failedTests++;
        // Log SEVERE for test failures with full details including the actual schema input
        LOG.severe(() -> String.format("ERROR: Invalid schema test FAILED: %s\nSchema: %s\nExpected: compilation to fail\nActual: compilation succeeded\nAssertionError: %s", 
                                       testName, schemaNode, e.getMessage()));
        throw new RuntimeException("Invalid schema test failed: " + testName, e);
      } catch (Exception e) {
        failedTests++;
        // Log SEVERE for test failures with full details
        LOG.severe(() -> String.format("ERROR: Invalid schema test FAILED: %s\nSchema: %s\nException: %s", 
                                       testName, schemaNode, e.getMessage()));
        throw new RuntimeException("Invalid schema test failed: " + testName, e);
      }
    });
  }
}
