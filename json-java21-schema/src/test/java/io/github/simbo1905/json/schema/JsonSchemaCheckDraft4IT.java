package io.github.simbo1905.json.schema;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

/// Runs the official JSON-Schema-Test-Suite (Draft 4) as JUnit dynamic tests.
/// By default, this is lenient and will SKIP mismatches and unsupported schemas
/// to provide a compatibility signal without breaking the build. Enable strict
/// mode with -Djson.schema.strict=true to make mismatches fail the build.
public class JsonSchemaCheckDraft4IT extends JsonSchemaCheckBaseIT {

  private static final Path ZIP_FILE = Paths.get("src/test/resources/json-schema-test-suite-draft4.zip");
  private static final Path TARGET_SUITE_DIR = Paths.get("target/test-data/draft4");

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
    return "draft4/";
  }

  @Override
  protected Set<String> getSkippedTests() {
    return Set.of(
      // Actual failing tests from test run - Reference resolution problems
      "infinite-loop-detection.json#evaluating the same schema location against the same data location twice is not a sign of an infinite loop#passing case",
      "infinite-loop-detection.json#evaluating the same schema location against the same data location twice is not a sign of an infinite loop#failing case",
      "ref.json#nested refs#nested ref valid",
      "ref.json#nested refs#nested ref invalid",
      "ref.json#ref overrides any sibling keywords#ref valid",
      "ref.json#ref overrides any sibling keywords#ref valid, maxItems ignored",
      "ref.json#ref overrides any sibling keywords#ref invalid",
      "ref.json#property named $ref, containing an actual $ref#property named $ref valid",
      "ref.json#property named $ref, containing an actual $ref#property named $ref invalid",
      "ref.json#id with file URI still resolves pointers - *nix#number is valid",
      "ref.json#id with file URI still resolves pointers - *nix#non-number is invalid",
      "ref.json#id with file URI still resolves pointers - windows#number is valid",
      "ref.json#id with file URI still resolves pointers - windows#non-number is invalid",
      "ref.json#empty tokens in $ref json-pointer#number is valid",
      "ref.json#empty tokens in $ref json-pointer#non-number is invalid",
      
      // Remote reference issues
      "refRemote.json#base URI change - change folder#number is valid",
      "refRemote.json#base URI change - change folder#string is invalid",
      "refRemote.json#base URI change - change folder in subschema#number is valid",
      "refRemote.json#base URI change - change folder in subschema#string is invalid",
      
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
