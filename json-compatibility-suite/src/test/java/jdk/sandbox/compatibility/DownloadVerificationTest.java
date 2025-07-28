package jdk.sandbox.compatibility;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

public class DownloadVerificationTest {
    @Test
    void testSuiteDownloaded() {
        Path testDir = Paths.get("target/test-resources/JSONTestSuite-master/test_parsing");
        assertThat(testDir)
            .as("JSON Test Suite should be downloaded and extracted")
            .exists()
            .isDirectory();
        
        // Verify some test files exist
        assertThat(testDir.resolve("y_structure_whitespace_array.json"))
            .as("Should contain valid test files")
            .exists();
    }
}
