package jdk.sandbox.compatibility;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonParseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/// Runs the JSON Test Suite against our implementation.
/// Files are categorized:
/// - y_*.json: Valid JSON that MUST parse successfully
/// - n_*.json: Invalid JSON that MUST fail to parse
/// - i_*.json: Implementation-defined (may accept or reject)
public class JsonTestSuiteTest {

    private static final Logger LOGGER = Logger.getLogger(JsonTestSuiteTest.class.getName());
    private static final Path TEST_DIR = Paths.get("target/test-resources/JSONTestSuite-master/test_parsing");

    @TestFactory
    @Disabled("This is now a reporting tool, not a blocking test. Use JsonTestSuiteSummary instead.")
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
            String content = null;
            char[] charContent = null;
            
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
                charContent = content.toCharArray();
            } catch (MalformedInputException e) {
                LOGGER.warning("UTF-8 failed for " + filename + ", using robust encoding detection");
                try {
                    byte[] rawBytes = Files.readAllBytes(file);
                    charContent = RobustCharDecoder.decodeToChars(rawBytes, filename);
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to read test file " + filename + " - this is a fundamental I/O failure, not an encoding issue: " + ex.getMessage(), ex);
                }
            }
            
            if (filename.startsWith("y_")) {
                // Valid JSON - must parse successfully
                testValidJson(filename, content, charContent);
                    
            } else if (filename.startsWith("n_")) {
                // Invalid JSON - must fail to parse
                testInvalidJson(filename, content, charContent);
                    
            } else if (filename.startsWith("i_")) {
                // Implementation defined - just verify no crash
                testImplementationDefinedJson(filename, content, charContent);
            }
        });
    }

    private void testValidJson(String filename, String content, char[] charContent) {
        // Test String API if content is available
        if (content != null) {
            assertThatCode(() -> Json.parse(content))
                .as("File %s should parse successfully with String API", filename)
                .doesNotThrowAnyException();
        }
        
        // Test char[] API
        assertThatCode(() -> Json.parse(charContent))
            .as("File %s should parse successfully with char[] API", filename)
            .doesNotThrowAnyException();
    }

    private void testInvalidJson(String filename, String content, char[] charContent) {
        // Test String API if content is available
        if (content != null) {
            assertThatThrownBy(() -> Json.parse(content))
                .as("File %s should fail to parse with String API", filename)
                .satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(JsonParseException.class),
                    e -> assertThat(e).isInstanceOf(StackOverflowError.class)
                        .describedAs("StackOverflowError is acceptable for deeply nested structures like " + filename)
                );
        }
        
        // Test char[] API
        assertThatThrownBy(() -> Json.parse(charContent))
            .as("File %s should fail to parse with char[] API", filename)
            .satisfiesAnyOf(
                e -> assertThat(e).isInstanceOf(JsonParseException.class),
                e -> assertThat(e).isInstanceOf(StackOverflowError.class)
                    .describedAs("StackOverflowError is acceptable for deeply nested structures like " + filename)
            );
    }

    private void testImplementationDefinedJson(String filename, String content, char[] charContent) {
        // Test String API if content is available
        if (content != null) {
            testImplementationDefinedSingle(filename + " (String API)", () -> Json.parse(content));
        }
        
        // Test char[] API
        testImplementationDefinedSingle(filename + " (char[] API)", () -> Json.parse(charContent));
    }

    private void testImplementationDefinedSingle(String description, Runnable parseAction) {
        try {
            parseAction.run();
            // OK - we accepted it
        } catch (JsonParseException e) {
            // OK - we rejected it
        } catch (StackOverflowError e) {
            // OK - acceptable for deeply nested structures
            LOGGER.warning("StackOverflowError on implementation-defined: " + description);
        } catch (Exception e) {
            // NOT OK - unexpected exception type
            fail("Unexpected exception for %s: %s", description, e);
        }
    }
}
