package jdk.sandbox.compatibility;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonParseException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Runs the JSON Test Suite against our implementation.
 * Files are categorized:
 * - y_*.json: Valid JSON that MUST parse successfully
 * - n_*.json: Invalid JSON that MUST fail to parse
 * - i_*.json: Implementation-defined (may accept or reject)
 */
public class JsonTestSuiteTest {

    private static final Path TEST_DIR = Paths.get("target/test-resources/JSONTestSuite-master/test_parsing");

    @TestFactory
    Stream<DynamicTest> runJsonTestSuite() throws Exception {
        if (!Files.exists(TEST_DIR)) {
            System.err.println("Test suite not found. Run: mvn test-compile");
            return Stream.empty();
        }

        return Files.walk(TEST_DIR)
            .filter(p -> p.toString().endsWith(".json"))
            .sorted()
            .map(this::createTest);
    }

    private DynamicTest createTest(Path file) {
        String filename = file.getFileName().toString();
        
        return DynamicTest.dynamicTest(filename, () -> {
            String content = Files.readString(file);
            
            if (filename.startsWith("y_")) {
                // Valid JSON - must parse successfully
                assertThatCode(() -> Json.parse(content))
                    .as("File %s should parse successfully", filename)
                    .doesNotThrowAnyException();
                    
            } else if (filename.startsWith("n_")) {
                // Invalid JSON - must fail to parse
                assertThatThrownBy(() -> Json.parse(content))
                    .as("File %s should fail to parse", filename)
                    .isInstanceOf(JsonParseException.class);
                    
            } else if (filename.startsWith("i_")) {
                // Implementation defined - just verify no crash
                try {
                    Json.parse(content);
                    // OK - we accepted it
                } catch (JsonParseException e) {
                    // OK - we rejected it
                } catch (Exception e) {
                    // NOT OK - unexpected exception type
                    fail("Unexpected exception for %s: %s", filename, e);
                }
            }
        });
    }
}
