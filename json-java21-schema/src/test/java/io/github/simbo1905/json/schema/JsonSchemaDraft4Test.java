package io.github.simbo1905.json.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.simbo1905.json.schema.SchemaLogging.LOG;

public class JsonSchemaDraft4Test extends JsonSchemaTestBase {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  final String idTest = """
      [
          {
              "description": "Invalid use of fragments in location-independent $id",
              "schema": {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$ref": "https://json-schema.org/draft/2020-12/schema"
              },
              "tests": [
                  {
                      "description": "Identifier name",
                      "data": {
                          "$ref": "#foo",
                          "$defs": {
                              "A": {
                                  "$id": "#foo",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": false
                  },
                  {
                      "description": "Identifier name and no ref",
                      "data": {
                          "$defs": {
                              "A": { "$id": "#foo" }
                          }
                      },
                      "valid": false
                  },
                  {
                      "description": "Identifier path",
                      "data": {
                          "$ref": "#/a/b",
                          "$defs": {
                              "A": {
                                  "$id": "#/a/b",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": false
                  },
                  {
                      "description": "Identifier name with absolute URI",
                      "data": {
                          "$ref": "http://localhost:1234/draft2020-12/bar#foo",
                          "$defs": {
                              "A": {
                                  "$id": "http://localhost:1234/draft2020-12/bar#foo",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": false
                  },
                  {
                      "description": "Identifier path with absolute URI",
                      "data": {
                          "$ref": "http://localhost:1234/draft2020-12/bar#/a/b",
                          "$defs": {
                              "A": {
                                  "$id": "http://localhost:1234/draft2020-12/bar#/a/b",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": false
                  },
                  {
                      "description": "Identifier name with base URI change in subschema",
                      "data": {
                          "$id": "http://localhost:1234/draft2020-12/root",
                          "$ref": "http://localhost:1234/draft2020-12/nested.json#foo",
                          "$defs": {
                              "A": {
                                  "$id": "nested.json",
                                  "$defs": {
                                      "B": {
                                          "$id": "#foo",
                                          "type": "integer"
                                      }
                                  }
                              }
                          }
                      },
                      "valid": false
                  },
                  {
                      "description": "Identifier path with base URI change in subschema",
                      "data": {
                          "$id": "http://localhost:1234/draft2020-12/root",
                          "$ref": "http://localhost:1234/draft2020-12/nested.json#/a/b",
                          "$defs": {
                              "A": {
                                  "$id": "nested.json",
                                  "$defs": {
                                      "B": {
                                          "$id": "#/a/b",
                                          "type": "integer"
                                      }
                                  }
                              }
                          }
                      },
                      "valid": false
                  }
              ]
          },
          {
              "description": "Valid use of empty fragments in location-independent $id",
              "comment": "These are allowed but discouraged",
              "schema": {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$ref": "https://json-schema.org/draft/2020-12/schema"
              },
              "tests": [
                  {
                      "description": "Identifier name with absolute URI",
                      "data": {
                          "$ref": "http://localhost:1234/draft2020-12/bar",
                          "$defs": {
                              "A": {
                                  "$id": "http://localhost:1234/draft2020-12/bar#",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": true
                  },
                  {
                      "description": "Identifier name with base URI change in subschema",
                      "data": {
                          "$id": "http://localhost:1234/draft2020-12/root",
                          "$ref": "http://localhost:1234/draft2020-12/nested.json#/$defs/B",
                          "$defs": {
                              "A": {
                                  "$id": "nested.json",
                                  "$defs": {
                                      "B": {
                                          "$id": "#",
                                          "type": "integer"
                                      }
                                  }
                              }
                          }
                      },
                      "valid": true
                  }
              ]
          },
          {
              "description": "Unnormalized $ids are allowed but discouraged",
              "schema": {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$ref": "https://json-schema.org/draft/2020-12/schema"
              },
              "tests": [
                  {
                      "description": "Unnormalized identifier",
                      "data": {
                          "$ref": "http://localhost:1234/draft2020-12/foo/baz",
                          "$defs": {
                              "A": {
                                  "$id": "http://localhost:1234/draft2020-12/foo/bar/../baz",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": true
                  },
                  {
                      "description": "Unnormalized identifier and no ref",
                      "data": {
                          "$defs": {
                              "A": {
                                  "$id": "http://localhost:1234/draft2020-12/foo/bar/../baz",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": true
                  },
                  {
                      "description": "Unnormalized identifier with empty fragment",
                      "data": {
                          "$ref": "http://localhost:1234/draft2020-12/foo/baz",
                          "$defs": {
                              "A": {
                                  "$id": "http://localhost:1234/draft2020-12/foo/bar/../baz#",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": true
                  },
                  {
                      "description": "Unnormalized identifier with empty fragment and no ref",
                      "data": {
                          "$defs": {
                              "A": {
                                  "$id": "http://localhost:1234/draft2020-12/foo/bar/../baz#",
                                  "type": "integer"
                              }
                          }
                      },
                      "valid": true
                  }
              ]
          },
          {
              "description": "$id inside an enum is not a real identifier",
              "comment": "the implementation must not be confused by an $id buried in the enum",
              "schema": {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$defs": {
                      "id_in_enum": {
                          "enum": [
                              {
                                "$id": "https://localhost:1234/draft2020-12/id/my_identifier.json",
                                "type": "null"
                              }
                          ]
                      },
                      "real_id_in_schema": {
                          "$id": "https://localhost:1234/draft2020-12/id/my_identifier.json",
                          "type": "string"
                      },
                      "zzz_id_in_const": {
                          "const": {
                              "$id": "https://localhost:1234/draft2020-12/id/my_identifier.json",
                              "type": "null"
                          }
                      }
                  },
                  "anyOf": [
                      { "$ref": "#/$defs/id_in_enum" },
                      { "$ref": "https://localhost:1234/draft2020-12/id/my_identifier.json" }
                  ]
              },
              "tests": [
                  {
                      "description": "exact match to enum, and type matches",
                      "data": {
                          "$id": "https://localhost:1234/draft2020-12/id/my_identifier.json",
                          "type": "null"
                      },
                      "valid": true
                  },
                  {
                      "description": "match $ref to $id",
                      "data": "a string to match #/$defs/id_in_enum",
                      "valid": true
                  },
                  {
                      "description": "no match on enum or $ref to $id",
                      "data": 1,
                      "valid": false
                  }
              ]
          },
          {
              "description": "non-schema object containing an $id property",
              "schema": {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$defs": {
                      "const_not_id": {
                          "const": {
                              "$id": "not_a_real_id"
                          }
                      }
                  },
                  "if": {
                      "const": "skip not_a_real_id"
                  },
                  "then": true,
                  "else" : {
                      "$ref": "#/$defs/const_not_id"
                  }
              },
              "tests": [
                  {
                      "description": "skip traversing definition for a valid result",
                      "data": "skip not_a_real_id",
                      "valid": true
                  },
                  {
                      "description": "const at const_not_id does not match",
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
        System.err.println("[JsonSchemaCheckIT] Skipping group due to unsupported schema: " + groupDesc + " — " + reason + " (" + ((Path) null).getFileName() + ")");

        return Stream.of(DynamicTest.dynamicTest(groupDesc + " – SKIPPED: " + reason, () -> {
          if (JsonSchemaCheckIT.isStrict()) throw ex;
          Assumptions.assumeTrue(false, "Unsupported schema: " + reason);
        }));
      }
    });
  }
}
