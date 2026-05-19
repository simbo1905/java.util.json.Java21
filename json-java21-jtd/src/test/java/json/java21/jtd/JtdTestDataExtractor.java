package json.java21.jtd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/// Shared utility for extracting the JTD test suite from the embedded ZIP file.
/// Used by both unit tests and integration tests to avoid committing large JSON files.
final class JtdTestDataExtractor {
  
  private static final Logger LOG = Logger.getLogger("json.java21.jtd");
  private static final Path ZIP_FILE = Paths.get("src/test/resources/jtd-test-suite.zip");
  private static final Path TARGET_DIR = Paths.get("target/test-data");
  private static final Path VALIDATION_FILE = TARGET_DIR.resolve("json-typedef-spec-2025-09-27/tests/validation.json");
  
  private JtdTestDataExtractor() {
    // Utility class
  }
  
  /// Ensures the test suite is extracted and returns the path to validation.json.
  /// Extraction happens at most once per build (target/ is cleaned between builds).
  static synchronized Path ensureValidationTestData() throws IOException {
    if (Files.exists(VALIDATION_FILE)) {
      LOG.fine(() -> "JTD test suite already extracted at: " + VALIDATION_FILE);
      return VALIDATION_FILE;
    }
    
    if (!Files.exists(ZIP_FILE)) {
      throw new RuntimeException("JTD test suite ZIP not found: " + ZIP_FILE.toAbsolutePath());
    }
    
    LOG.info(() -> "Extracting JTD test suite from: " + ZIP_FILE);
    Files.createDirectories(TARGET_DIR);
    
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(ZIP_FILE))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().startsWith("json-typedef-spec-")) {
          Path outputPath = TARGET_DIR.resolve(entry.getName());
          Files.createDirectories(outputPath.getParent());
          Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
        zis.closeEntry();
      }
    }
    
    if (!Files.exists(VALIDATION_FILE)) {
      throw new RuntimeException("Extraction completed but validation.json not found: " + VALIDATION_FILE);
    }
    
    LOG.info(() -> "JTD test suite extracted successfully");
    return VALIDATION_FILE;
  }
  
  /// Returns an InputStream for the validation test data, extracting if necessary.
  /// Suitable for use with classpath-style resource loading patterns.
  static InputStream getValidationTestDataStream() throws IOException {
    Path dataFile = ensureValidationTestData();
    return Files.newInputStream(dataFile);
  }
}
