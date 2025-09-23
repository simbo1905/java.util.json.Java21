package io.github.simbo1905.json.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;

import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.simbo1905.json.schema.JsonSchema.LOG;
import static org.junit.jupiter.api.Assertions.fail;


public class JsonSchemaDraft4Test extends JsonSchemaLoggingConfig {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  final String idTest = """
      [
          {
              "description": "id inside an enum is not a real identifier",
              "comment": "the implementation must not be confused by an id buried in the enum",
              "schema": {
                  "definitions": {
                      "id_in_enum": {
                          "enum": [
                              {
                                "id": "https://localhost:1234/my_identifier.json",
                                "type": "null"
                              }
                          ]
                      },
                      "real_id_in_schema": {
                          "id": "https://localhost:1234/my_identifier.json",
                          "type": "string"
                      },
                      "zzz_id_in_const": {
                          "const": {
                              "id": "https://localhost:1234/my_identifier.json",
                              "type": "null"
                          }
                      }
                  },
                  "anyOf": [
                      { "$ref": "#/definitions/id_in_enum" },
                      { "$ref": "https://localhost:1234/my_identifier.json" }
                  ]
              },
              "tests": [
                  {
                      "description": "exact match to enum, and type matches",
                      "data": {
                          "id": "https://localhost:1234/my_identifier.json",
                          "type": "null"
                      },
                      "valid": true
                  },
                  {
                      "description": "match $ref to id",
                      "data": "a string to match #/definitions/id_in_enum",
                      "valid": true
                  },
                  {
                      "description": "no match on enum or $ref to id",
                      "data": 1,
                      "valid": false
                  }
              ]
          }
      
      ]
      """;

  @TestFactory
  @Disabled("This test is for debugging schema compatibility issues with Draft4. It contains remote references that fail with RemoteResolutionException when remote fetching is disabled. Use this to debug reference resolution problems.")
  public Stream<DynamicTest> testId() throws JsonProcessingException {
    final var root = MAPPER.readTree(idTest);
    return StreamSupport.stream(root.spliterator(), false).flatMap(group -> {
      final var groupDesc = group.get("description").asText();
      try {
        final var schema = JsonSchema.compile(Json.parse(group.get("schema").toString()));

        return StreamSupport.stream(group.get("tests").spliterator(), false).map(test -> DynamicTest.dynamicTest(groupDesc + " – " + test.get("description").asText(), () -> {
          final var expected = test.get("valid").asBoolean();
          final boolean actual = schema.validate(Json.parse(test.get("data").toString())).valid();
          try {
            Assertions.assertEquals(expected, actual);
          } catch (AssertionError e) {
            LOG.fine(() -> "Assertion failed: " + groupDesc + " — expected=" + expected + ", actual=" + actual + " (JsonSchemaDraft4Test.java)");
            throw e;
          }

        }));
      } catch (Exception ex) {
        /// Unsupported schema for this group; emit a single skipped test for visibility
        final var reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        LOG.fine(()->"Skipping group due to unsupported schema: " + groupDesc + " — " + reason + " (JsonSchemaDraft4Test.java)");

        return Stream.of(DynamicTest.dynamicTest(groupDesc + " – SKIPPED: " + reason, () -> {
          if (JsonSchemaDraft4Test.isStrict()) throw ex;
          Assumptions.assumeTrue(false, "Unsupported schema: " + reason);
        }));
      }
    });
  }

  private static boolean isStrict() {
    return true;
  }

  /// Test for JSON parsing issues with escaped characters in required fields
  /// This is the simplest failing test case from the Draft4 Test Suite
  @Test
  void testRequiredWithEscapedCharacters() {
    LOG.info("TEST: JsonSchemaDraft4Test#testRequiredWithEscapedCharacters");
    
    // This is the exact test from the official JSON Schema Draft 4 test suite
    // that was previously failing due to a bug in the JSON parser
    final String requiredEscapedCharsTest = """
        {
            "description": "required with escaped characters",
            "schema": {
                "required": [
                    "foo\\nbar",
                    "foo\\"bar",
                    "foo\\\\bar",
                    "foo\\rbar",
                    "foo\\tbar",
                    "foo\\fbar"
                ]
            },
            "tests": [
                {
                    "description": "object with all properties present is valid",
                    "data": {
                        "foo\\nbar": 1,
                        "foo\\"bar": 1,
                        "foo\\\\bar": 1,
                        "foo\\rbar": 1,
                        "foo\\tbar": 1,
                        "foo\\fbar": 1
                    },
                    "valid": true
                },
                {
                    "description": "object with some properties missing is invalid",
                    "data": {
                        "foo\\nbar": 1,
                        "foo\\"bar": 1
                    },
                    "valid": false
                }
            ]
        }
        """;

    try {
      final var testGroup = MAPPER.readTree(requiredEscapedCharsTest);
      final var schema = JsonSchema.compile(Json.parse(testGroup.get("schema").toString()));
      LOG.fine(() -> "Compiled schema for escaped characters test: " + schema);

      final var tests = testGroup.get("tests");
      for (var test : tests) {
        final var description = test.get("description").asText();
        final var expected = test.get("valid").asBoolean();
        final var data = test.get("data").toString();
        
        LOG.finer(() -> "Running test: " + description + " with data: " + data);
        
        try {
          final var jsonData = Json.parse(data);
          LOG.finest(() -> "Parsed JSON data: " + jsonData);
          
          final var result = schema.validate(jsonData);
          final var actual = result.valid();
          
          LOG.fine(() -> "Validation result: expected=" + expected + ", actual=" + actual + 
                         ", errors=" + (result.errors().isEmpty() ? "none" : result.errors()));
          
          Assertions.assertEquals(expected, actual, 
            "Test failed: " + description + " - expected=" + expected + ", actual=" + actual + 
            ", validation errors: " + result.errors());
            
        } catch (Exception e) {
          LOG.severe(() -> "Exception parsing or validating data for test '" + description + "': " + e.getMessage());
          throw new AssertionError("Failed to parse/validate test data: " + e.getMessage(), e);
        }
      }
      
      LOG.info("All escaped characters tests passed successfully");
      
    } catch (JsonProcessingException e) {
      throw new AssertionError("Failed to parse test JSON: " + e.getMessage(), e);
    }
  }
}
