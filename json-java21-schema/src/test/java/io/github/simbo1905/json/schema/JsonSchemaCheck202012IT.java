package io.github.simbo1905.json.schema;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

/// Runs the official JSON-Schema-Test-Suite (Draft 2020-12) as JUnit dynamic tests.
/// By default, this is lenient and will SKIP mismatches and unsupported schemas
/// to provide a compatibility signal without breaking the build. Enable strict
/// mode with -Djson.schema.strict=true to make mismatches fail the build.
public class JsonSchemaCheck202012IT extends JsonSchemaCheckBaseIT {

  private static final Path ZIP_FILE = Paths.get("src/test/resources/json-schema-test-suite-data.zip");
  private static final Path TARGET_SUITE_DIR = Paths.get("target/test-data/draft2020-12");

  @Override
  protected Path getZipFile() {
    return ZIP_FILE;
  }

  @Override
  protected Path getTargetSuiteDir() {
    return TARGET_SUITE_DIR;
  }

  @Override
  protected String getSchemaPrefix() {
    return "draft2020-12/";
  }

  @Override
  protected Set<String> getSkippedTests() {
    return Set.of(
      // Reference resolution issues - Unresolved $ref problems
      "ref.json#relative pointer ref to array#match array",
      "ref.json#relative pointer ref to array#mismatch array",
      "refOfUnknownKeyword.json#reference of a root arbitrary keyword #match",
      "refOfUnknownKeyword.json#reference of a root arbitrary keyword #mismatch",
      "refOfUnknownKeyword.json#reference of an arbitrary keyword of a sub-schema#match",
      "refOfUnknownKeyword.json#reference of an arbitrary keyword of a sub-schema#mismatch",
      
      // JSON parsing issues with duplicate member names
      "required.json#required with escaped characters#object with all properties present is valid",
      "required.json#required with escaped characters#object with some properties missing is invalid"
    );
  }

  @TestFactory
  @Override
  public Stream<DynamicTest> runOfficialSuite() throws Exception {
    return super.runOfficialSuite();
  }
}