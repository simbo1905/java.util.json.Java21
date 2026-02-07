package io.github.simbo1905.json.jtd.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Tests for the new stack-based JTD to ESM code generator.
/// Uses bun to execute generated JavaScript validators.
final class JtdToEsmCodegenTest extends JtdEsmCodegenLoggingConfig {
    private static final Logger LOG = Logger.getLogger(JtdToEsmCodegenTest.class.getName());

    @Test
    void parsesSimpleBooleanTypeSchema() {
        LOG.info(() -> "Running parsesSimpleBooleanTypeSchema");

        final var schema = """
                {"type": "boolean"}
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.id()).isEqualTo("JtdSchema");
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.TypeNode.class);

        final var typeNode = (JtdAst.TypeNode) root.rootSchema();
        assertThat(typeNode.type()).isEqualTo("boolean");
    }

    @Test
    void parsesSchemaWithMetadataId() {
        LOG.info(() -> "Running parsesSchemaWithMetadataId");

        final var schema = """
                {
                    "type": "string",
                    "metadata": {"id": "my-schema-v1"}
                }
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.id()).isEqualTo("my-schema-v1");
    }

    @Test
    void parsesEnumSchema() {
        LOG.info(() -> "Running parsesEnumSchema");

        final var schema = """
                {"enum": ["active", "inactive", "pending"]}
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.EnumNode.class);

        final var enumNode = (JtdAst.EnumNode) root.rootSchema();
        assertThat(enumNode.values()).containsExactly("active", "inactive", "pending");
    }

    @Test
    void parsesElementsArraySchema() {
        LOG.info(() -> "Running parsesElementsArraySchema");

        final var schema = """
                {
                    "elements": {"type": "string"},
                    "metadata": {"id": "string-array"}
                }
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.ElementsNode.class);

        final var elementsNode = (JtdAst.ElementsNode) root.rootSchema();
        assertThat(elementsNode.schema()).isInstanceOf(JtdAst.TypeNode.class);
    }

    @Test
    void parsesNestedElementsSchema() {
        LOG.info(() -> "Running parsesNestedElementsSchema");

        final var schema = """
                {
                    "elements": {
                        "elements": {"type": "int32"}
                    },
                    "metadata": {"id": "matrix"}
                }
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.ElementsNode.class);

        final var outer = (JtdAst.ElementsNode) root.rootSchema();
        assertThat(outer.schema()).isInstanceOf(JtdAst.ElementsNode.class);
    }

    @Test
    void parsesValuesMapSchema() {
        LOG.info(() -> "Running parsesValuesMapSchema");

        final var schema = """
                {
                    "values": {"type": "string"},
                    "metadata": {"id": "string-map"}
                }
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.ValuesNode.class);
    }

    @Test
    void parsesDiscriminatorUnionSchema() {
        LOG.info(() -> "Running parsesDiscriminatorUnionSchema");

        final var schema = """
                {
                    "discriminator": "type",
                    "mapping": {
                        "cat": {
                            "properties": {
                                "name": {"type": "string"},
                                "meow": {"type": "boolean"}
                            }
                        },
                        "dog": {
                            "properties": {
                                "name": {"type": "string"},
                                "bark": {"type": "boolean"}
                            }
                        }
                    },
                    "metadata": {"id": "animal-union"}
                }
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.DiscriminatorNode.class);

        final var discNode = (JtdAst.DiscriminatorNode) root.rootSchema();
        assertThat(discNode.discriminator()).isEqualTo("type");
        assertThat(discNode.mapping()).containsKeys("cat", "dog");
    }

    @Test
    void parsesNullableWrapperSchema() {
        LOG.info(() -> "Running parsesNullableWrapperSchema");

        final var schema = """
                {
                    "type": "string",
                    "nullable": true,
                    "metadata": {"id": "nullable-string"}
                }
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.NullableNode.class);

        final var nullableNode = (JtdAst.NullableNode) root.rootSchema();
        assertThat(nullableNode.wrapped()).isInstanceOf(JtdAst.TypeNode.class);
    }

    @Test
    void parsesRefAndDefinitions() {
        LOG.info(() -> "Running parsesRefAndDefinitions");

        final var schema = """
                {
                    "definitions": {
                        "dataValue": {"type": "string"}
                    },
                    "properties": {
                        "data": {"ref": "dataValue"}
                    },
                    "metadata": {"id": "ref-test"}
                }
                """;

        final var root = JtdParser.parseString(schema);
        assertThat(root.definitions()).containsKey("dataValue");
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.PropertiesNode.class);
    }

    @Test
    void rejectsUnknownType() {
        LOG.info(() -> "Running rejectsUnknownType");

        final var schema = """
                {"type": "unknown"}
                """;

        assertThatThrownBy(() -> JtdParser.parseString(schema))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown type");
    }

    @Test
    void rejectsInvalidEnum() {
        LOG.info(() -> "Running rejectsInvalidEnum");

        final var schema = """
                {"enum": ["a", 123, "c"]}
                """;

        assertThatThrownBy(() -> JtdParser.parseString(schema))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("to be a string");
    }

    @Test
    void generatedBooleanValidatorPassesValidCases(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedBooleanValidatorPassesValidCases");
        assumeTrue(isBunAvailable(), "bun is required for JavaScript execution tests");

        final var schema = """
                {"type": "boolean", "metadata": {"id": "bool-test"}}
                """;

        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);

        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        // Create test runner
        final var runner = """
            import { validate } from '%s';

            const results = [];

            // Valid cases
            results.push({ name: 'true', errors: validate(true), expectEmpty: true });
            results.push({ name: 'false', errors: validate(false), expectEmpty: true });

            // Invalid cases
            results.push({ name: 'string', errors: validate('hello'), expectEmpty: false });
            results.push({ name: 'number', errors: validate(42), expectEmpty: false });
            results.push({ name: 'null', errors: validate(null), expectEmpty: false });
            results.push({ name: 'object', errors: validate({}), expectEmpty: false });
            results.push({ name: 'array', errors: validate([]), expectEmpty: false });

            console.log(JSON.stringify(results));
            """.formatted(outJs.toUri());

        final Path runnerFile = tempDir.resolve("runner.mjs");
        Files.writeString(runnerFile, runner, StandardCharsets.UTF_8);

        final var p = new ProcessBuilder("bun", "run", runnerFile.toString())
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start();

        final var output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        final int code = p.waitFor();

        assertThat(code).as("bun exit code; output:\n%s", output).isEqualTo(0);

        // Parse and verify results
        final var json = jdk.sandbox.java.util.json.Json.parse(output);
        final var results = (jdk.sandbox.java.util.json.JsonArray) json;

        for (jdk.sandbox.java.util.json.JsonValue v : results.elements()) {
            final var obj = (jdk.sandbox.java.util.json.JsonObject) v;
            final String name = ((jdk.sandbox.java.util.json.JsonString) obj.get("name")).string();
            final boolean expectEmpty = ((jdk.sandbox.java.util.json.JsonBoolean) obj.get("expectEmpty")).bool();
            final var errors = (jdk.sandbox.java.util.json.JsonArray) obj.get("errors");

            if (expectEmpty) {
                assertThat(errors.elements()).as("Test case '%s' should have no errors", name).isEmpty();
            } else {
                assertThat(errors.elements()).as("Test case '%s' should have errors", name).isNotEmpty();
            }
        }
    }

    @Test
    void generatedStringArrayValidatorWorks(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedStringArrayValidatorWorks");
        assumeTrue(isBunAvailable(), "bun is required for JavaScript execution tests");

        final var schema = """
                {
                    "elements": {"type": "string"},
                    "metadata": {"id": "string-array-test"}
                }
                """;

        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);

        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        final var runner = """
            import { validate } from '%s';

            const results = [];

            // Valid cases
            results.push({ name: 'empty-array', errors: validate([]), expectEmpty: true });
            results.push({ name: 'string-array', errors: validate(["a", "b", "c"]), expectEmpty: true });

            // Invalid cases
            results.push({ name: 'not-array', errors: validate("hello"), expectEmpty: false });
            results.push({ name: 'mixed-array', errors: validate(["a", 123, "c"]), expectEmpty: false });
            results.push({ name: 'number-array', errors: validate([1, 2, 3]), expectEmpty: false });

            console.log(JSON.stringify(results));
            """.formatted(outJs.toUri());

        final Path runnerFile = tempDir.resolve("runner.mjs");
        Files.writeString(runnerFile, runner, StandardCharsets.UTF_8);

        final var p = new ProcessBuilder("bun", "run", runnerFile.toString())
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start();

        final var output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        final int code = p.waitFor();

        assertThat(code).as("bun exit code; output:\n%s", output).isEqualTo(0);
    }

    @Test
    void generatedObjectValidatorChecksRequiredAndOptional(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedObjectValidatorChecksRequiredAndOptional");
        assumeTrue(isBunAvailable(), "bun is required for JavaScript execution tests");

        final var schema = """
                {
                    "properties": {
                        "id": {"type": "int32"},
                        "name": {"type": "string"}
                    },
                    "optionalProperties": {
                        "email": {"type": "string"}
                    },
                    "metadata": {"id": "user-schema"}
                }
                """;

        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);

        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        final var runner = """
            import { validate } from '%s';

            const results = [];

            // Valid cases
            results.push({ name: 'complete', errors: validate({id: 1, name: "Alice", email: "a@b.com"}), expectEmpty: true });
            results.push({ name: 'without-optional', errors: validate({id: 1, name: "Alice"}), expectEmpty: true });

            // Invalid cases
            results.push({ name: 'missing-required', errors: validate({name: "Alice"}), expectEmpty: false });
            results.push({ name: 'wrong-type', errors: validate({id: "not-a-number", name: "Alice"}), expectEmpty: false });
            results.push({ name: 'not-object', errors: validate("hello"), expectEmpty: false });

            console.log(JSON.stringify(results));
            """.formatted(outJs.toUri());

        final Path runnerFile = tempDir.resolve("runner.mjs");
        Files.writeString(runnerFile, runner, StandardCharsets.UTF_8);

        final var p = new ProcessBuilder("bun", "run", runnerFile.toString())
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start();

        final var output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        final int code = p.waitFor();

        assertThat(code).as("bun exit code; output:\n%s", output).isEqualTo(0);
    }

    @Test
    void generatedValidatorIncludesOnlyNeededHelpers(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedValidatorIncludesOnlyNeededHelpers");
        assumeTrue(isBunAvailable(), "bun is required for JavaScript execution tests");

        // Schema that only needs basic type checks
        final var simpleSchema = """
                {"type": "boolean", "metadata": {"id": "simple"}}
                """;

        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, simpleSchema, StandardCharsets.UTF_8);

        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);
        final String generated = Files.readString(outJs, StandardCharsets.UTF_8);

        // Should NOT include unused helpers
        assertThat(generated).doesNotContain("isTimestamp");
        assertThat(generated).doesNotContain("isIntInRange");
        assertThat(generated).doesNotContain("isFloat");

        // Should use typeof directly
        assertThat(generated).contains("typeof");
    }

    @Test
    void generatedTimestampValidatorIncludesTimestampHelper(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedTimestampValidatorIncludesTimestampHelper");
        assumeTrue(isBunAvailable(), "bun is required for JavaScript execution tests");

        final var schema = """
                {"type": "timestamp", "metadata": {"id": "ts-test"}}
                """;

        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);

        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);
        final String generated = Files.readString(outJs, StandardCharsets.UTF_8);

        // Should include timestamp helper since it's needed
        assertThat(generated).contains("isTimestamp");
        assertThat(generated).contains("Date.parse");
    }

    private static boolean isBunAvailable() {
        try {
            final var p = new ProcessBuilder("bun", "--version")
                .redirectErrorStream(true)
                .start();
            final int code = p.waitFor();
            return code == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
