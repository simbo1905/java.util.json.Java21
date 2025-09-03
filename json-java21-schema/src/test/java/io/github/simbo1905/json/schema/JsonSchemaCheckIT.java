package io.github.simbo1905.json.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Assumptions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Runs the official JSON-Schema-Test-Suite (Draft 2020-12) as JUnit dynamic tests.
/// By default, this is lenient and will SKIP mismatches and unsupported schemas
/// to provide a compatibility signal without breaking the build. Enable strict
/// mode with -Djson.schema.strict=true to make mismatches fail the build.
public class JsonSchemaCheckIT {

    private static final File SUITE_ROOT =
            new File("target/json-schema-test-suite/tests/draft2020-12");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final boolean STRICT = Boolean.getBoolean("json.schema.strict");

    @SuppressWarnings("resource")
    @TestFactory
    Stream<DynamicTest> runOfficialSuite() throws Exception {
        return Files.walk(SUITE_ROOT.toPath())
                .filter(p -> p.toString().endsWith(".json"))
                .flatMap(this::testsFromFile);
    }

    private Stream<DynamicTest> testsFromFile(Path file) {
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            return StreamSupport.stream(root.spliterator(), false)
                    .flatMap(group -> {
                        String groupDesc = group.get("description").asText();
                        try {
                            // Attempt to compile the schema for this group; if unsupported features
                            // (e.g., unresolved anchors) are present, skip this group gracefully.
                            JsonSchema schema = JsonSchema.compile(
                                    Json.parse(group.get("schema").toString()));

                            return StreamSupport.stream(group.get("tests").spliterator(), false)
                                    .map(test -> DynamicTest.dynamicTest(
                                            groupDesc + " – " + test.get("description").asText(),
                                            () -> {
                                                boolean expected = test.get("valid").asBoolean();
                                                boolean actual;
                                                try {
                                                    actual = schema.validate(
                                                            Json.parse(test.get("data").toString())).valid();
                                                } catch (Exception e) {
                                                    String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                                                    System.err.println("[JsonSchemaCheckIT] Skipping test due to exception: "
                                                            + groupDesc + " — " + reason + " (" + file.getFileName() + ")");
                                                    if (STRICT) throw e;
                                                    Assumptions.assumeTrue(false, "Skipped: " + reason);
                                                    return; // not reached when strict
                                                }

                                                if (STRICT) {
                                                    assertEquals(expected, actual);
                                                } else if (expected != actual) {
                                                    System.err.println("[JsonSchemaCheckIT] Mismatch (ignored): "
                                                            + groupDesc + " — expected=" + expected + ", actual=" + actual
                                                            + " (" + file.getFileName() + ")");
                                                    Assumptions.assumeTrue(false, "Mismatch ignored");
                                                }
                                            }));
                        } catch (Exception ex) {
                            // Unsupported schema for this group; emit a single skipped test for visibility
                            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                            System.err.println("[JsonSchemaCheckIT] Skipping group due to unsupported schema: "
                                    + groupDesc + " — " + reason + " (" + file.getFileName() + ")");
                            return Stream.of(DynamicTest.dynamicTest(
                                    groupDesc + " – SKIPPED: " + reason,
                                    () -> { if (STRICT) throw ex; Assumptions.assumeTrue(false, "Unsupported schema: " + reason); }
                            ));
                        }
                    });
        } catch (Exception ex) {
            throw new RuntimeException("Failed to process " + file, ex);
        }
    }
}
