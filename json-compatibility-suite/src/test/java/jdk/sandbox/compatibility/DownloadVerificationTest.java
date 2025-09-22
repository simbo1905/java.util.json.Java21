package jdk.sandbox.compatibility;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

public class DownloadVerificationTest {
    @Test
    void testSuiteDownloaded() {
        // The test data is now extracted from ZIP at runtime
        // Create a summary instance and extract the data manually for testing
        try {
            JsonCompatibilitySummary summary = new JsonCompatibilitySummary();
            summary.extractTestData();
            
            // Verify the target directory exists after extraction
            Path targetDir = Paths.get("target/test-data/json-test-suite/test_parsing");
            assertThat(targetDir)
                .as("JSON Test Suite should be extracted to target directory")
                .exists()
                .isDirectory();
            
            // Verify some test files exist
            assertThat(targetDir.resolve("y_valid_sample.json"))
                .as("Should contain valid test files")
                .exists();
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract JSON test suite data", e);
        }
    }
}
