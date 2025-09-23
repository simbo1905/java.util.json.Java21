package io.github.simbo1905.json.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.simbo1905.json.schema.JsonSchema.LOG;


public class JsonSchemaDraft4Test extends JsonSchemaTestBase {
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
            LOG.fine(() -> "Assertion failed: " + groupDesc + " — expected=" + expected + ", actual=" + actual + " (" + ((Path) null).getFileName() + ")");
            throw e;
          }

        }));
      } catch (Exception ex) {
        /// Unsupported schema for this group; emit a single skipped test for visibility
        final var reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        LOG.fine(()->"Skipping group due to unsupported schema: " + groupDesc + " — " + reason + " (" + ((Path) null).getFileName() + ")");

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
}
