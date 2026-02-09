package io.github.simbo1905.json.jtd.codegen;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests for the stack-based JTD to ESM code generator.
/// Uses GraalVM Polyglot JS for in-process JavaScript execution - no external runtime needed.
final class JtdToEsmCodegenTest extends JtdEsmCodegenLoggingConfig {
    private static final Logger LOG = Logger.getLogger(JtdToEsmCodegenTest.class.getName());

    // --- Parser tests (pure Java, no JS execution) ---

    @Test
    void parsesSimpleBooleanTypeSchema() {
        LOG.info(() -> "Running parsesSimpleBooleanTypeSchema");
        final var root = JtdParser.parseString("""
                {"type": "boolean"}
                """);
        assertThat(root.id()).isEqualTo("JtdSchema");
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.TypeNode.class);
        final var typeNode = (JtdAst.TypeNode) root.rootSchema();
        assertThat(typeNode.type()).isEqualTo("boolean");
    }

    @Test
    void parsesSchemaWithMetadataId() {
        LOG.info(() -> "Running parsesSchemaWithMetadataId");
        final var root = JtdParser.parseString("""
                {"type": "string", "metadata": {"id": "my-schema-v1"}}
                """);
        assertThat(root.id()).isEqualTo("my-schema-v1");
    }

    @Test
    void parsesEnumSchema() {
        LOG.info(() -> "Running parsesEnumSchema");
        final var root = JtdParser.parseString("""
                {"enum": ["active", "inactive", "pending"]}
                """);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.EnumNode.class);
        final var enumNode = (JtdAst.EnumNode) root.rootSchema();
        assertThat(enumNode.values()).containsExactly("active", "inactive", "pending");
    }

    @Test
    void parsesElementsArraySchema() {
        LOG.info(() -> "Running parsesElementsArraySchema");
        final var root = JtdParser.parseString("""
                {"elements": {"type": "string"}, "metadata": {"id": "string-array"}}
                """);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.ElementsNode.class);
        final var elementsNode = (JtdAst.ElementsNode) root.rootSchema();
        assertThat(elementsNode.schema()).isInstanceOf(JtdAst.TypeNode.class);
    }

    @Test
    void parsesNestedElementsSchema() {
        LOG.info(() -> "Running parsesNestedElementsSchema");
        final var root = JtdParser.parseString("""
                {"elements": {"elements": {"type": "int32"}}, "metadata": {"id": "matrix"}}
                """);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.ElementsNode.class);
        final var outer = (JtdAst.ElementsNode) root.rootSchema();
        assertThat(outer.schema()).isInstanceOf(JtdAst.ElementsNode.class);
    }

    @Test
    void parsesValuesMapSchema() {
        LOG.info(() -> "Running parsesValuesMapSchema");
        final var root = JtdParser.parseString("""
                {"values": {"type": "string"}, "metadata": {"id": "string-map"}}
                """);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.ValuesNode.class);
    }

    @Test
    void parsesDiscriminatorUnionSchema() {
        LOG.info(() -> "Running parsesDiscriminatorUnionSchema");
        final var root = JtdParser.parseString("""
                {
                    "discriminator": "type",
                    "mapping": {
                        "cat": {"properties": {"name": {"type": "string"}, "meow": {"type": "boolean"}}},
                        "dog": {"properties": {"name": {"type": "string"}, "bark": {"type": "boolean"}}}
                    },
                    "metadata": {"id": "animal-union"}
                }
                """);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.DiscriminatorNode.class);
        final var discNode = (JtdAst.DiscriminatorNode) root.rootSchema();
        assertThat(discNode.discriminator()).isEqualTo("type");
        assertThat(discNode.mapping()).containsKeys("cat", "dog");
    }

    @Test
    void parsesNullableWrapperSchema() {
        LOG.info(() -> "Running parsesNullableWrapperSchema");
        final var root = JtdParser.parseString("""
                {"type": "string", "nullable": true, "metadata": {"id": "nullable-string"}}
                """);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.NullableNode.class);
        final var nullableNode = (JtdAst.NullableNode) root.rootSchema();
        assertThat(nullableNode.wrapped()).isInstanceOf(JtdAst.TypeNode.class);
    }

    @Test
    void parsesRefAndDefinitions() {
        LOG.info(() -> "Running parsesRefAndDefinitions");
        final var root = JtdParser.parseString("""
                {
                    "definitions": {"dataValue": {"type": "string"}},
                    "properties": {"data": {"ref": "dataValue"}},
                    "metadata": {"id": "ref-test"}
                }
                """);
        assertThat(root.definitions()).containsKey("dataValue");
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.PropertiesNode.class);
    }

    @Test
    void rejectsUnknownType() {
        LOG.info(() -> "Running rejectsUnknownType");
        assertThatThrownBy(() -> JtdParser.parseString("{\"type\": \"unknown\"}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown type");
    }

    @Test
    void rejectsInvalidEnum() {
        LOG.info(() -> "Running rejectsInvalidEnum");
        assertThatThrownBy(() -> JtdParser.parseString("{\"enum\": [\"a\", 123, \"c\"]}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("to be a string");
    }

    // --- Generated code content tests (no JS execution) ---

    @Test
    void generatedValidatorIncludesOnlyNeededHelpers(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedValidatorIncludesOnlyNeededHelpers");
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {"type": "boolean", "metadata": {"id": "simple"}}
                """, StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);
        final String generated = Files.readString(outJs, StandardCharsets.UTF_8);

        assertThat(generated).doesNotContain("isTimestamp");
        assertThat(generated).doesNotContain("isIntInRange");
        assertThat(generated).doesNotContain("isFloat");
        assertThat(generated).contains("typeof");
    }

    @Test
    void generatedTimestampValidatorIncludesTimestampHelper(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedTimestampValidatorIncludesTimestampHelper");
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {"type": "timestamp", "metadata": {"id": "ts-test"}}
                """, StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);
        final String generated = Files.readString(outJs, StandardCharsets.UTF_8);

        // Spec-compliant: timestamp check is inlined (no helper function)
        assertThat(generated).contains("/type");
        assertThat(generated).contains("errors.push");
    }

    // --- GraalVM Polyglot JS execution tests ---

    @Test
    void generatedBooleanValidatorPassesValidCases(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedBooleanValidatorPassesValidCases");
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {"type": "boolean", "metadata": {"id": "bool-test"}}
                """, StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        try (var cx = jsContext()) {
            final var exports = evalModule(cx, outJs);
            final var validate = exports.getMember("validate");

            // Valid cases
            assertThat(errCount(validate, true)).as("true").isZero();
            assertThat(errCount(validate, false)).as("false").isZero();

            // Invalid cases
            assertThat(errCount(validate, "hello")).as("string").isGreaterThan(0);
            assertThat(errCount(validate, 42)).as("number").isGreaterThan(0);
            assertThat(errCount(validate, cx.eval("js", "null"))).as("null").isGreaterThan(0);
        }
    }

    @Test
    void generatedStringArrayValidatorWorks(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedStringArrayValidatorWorks");
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {"elements": {"type": "string"}, "metadata": {"id": "string-array-test"}}
                """, StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        try (var cx = jsContext()) {
            final var exports = evalModule(cx, outJs);
            final var validate = exports.getMember("validate");

            // Valid: empty array
            assertThat(errCount(validate, cx.eval("js", "[]"))).as("empty-array").isZero();
            // Valid: string array
            assertThat(errCount(validate, cx.eval("js", "['a','b','c']"))).as("string-array").isZero();
            // Invalid: not an array
            assertThat(errCount(validate, "hello")).as("not-array").isGreaterThan(0);
            // Invalid: mixed
            assertThat(errCount(validate, cx.eval("js", "['a',123,'c']"))).as("mixed").isGreaterThan(0);
        }
    }

    @Test
    void generatedObjectValidatorChecksRequiredAndOptional(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedObjectValidatorChecksRequiredAndOptional");
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {
                    "properties": {"id": {"type": "int32"}, "name": {"type": "string"}},
                    "optionalProperties": {"email": {"type": "string"}},
                    "metadata": {"id": "user-schema"}
                }
                """, StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        try (var cx = jsContext()) {
            final var exports = evalModule(cx, outJs);
            final var validate = exports.getMember("validate");

            // Valid: complete object
            assertThat(errCount(validate, cx.eval("js", "({id:1,name:'Alice',email:'a@b.com'})")))
                .as("complete").isZero();
            // Valid: without optional
            assertThat(errCount(validate, cx.eval("js", "({id:1,name:'Alice'})")))
                .as("without-optional").isZero();
            // Invalid: missing required
            assertThat(errCount(validate, cx.eval("js", "({name:'Alice'})")))
                .as("missing-required").isGreaterThan(0);
            // Invalid: wrong type
            assertThat(errCount(validate, cx.eval("js", "({id:'not-int',name:'Alice'})")))
                .as("wrong-type").isGreaterThan(0);
            // Invalid: not an object
            assertThat(errCount(validate, "hello")).as("not-object").isGreaterThan(0);
        }
    }

    @Test
    void generatedDiscriminatorValidatorWorks(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedDiscriminatorValidatorWorks");
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {
                    "discriminator": "kind",
                    "mapping": {
                        "cat": {"properties": {"name": {"type": "string"}, "meow": {"type": "boolean"}}},
                        "dog": {"properties": {"name": {"type": "string"}, "bark": {"type": "boolean"}}}
                    },
                    "metadata": {"id": "animal-disc"}
                }
                """, StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        try (var cx = jsContext()) {
            final var exports = evalModule(cx, outJs);
            final var validate = exports.getMember("validate");

            // Valid: cat
            assertThat(errCount(validate, cx.eval("js", "({kind:'cat',name:'Whiskers',meow:true})")))
                .as("valid-cat").isZero();
            // Valid: dog
            assertThat(errCount(validate, cx.eval("js", "({kind:'dog',name:'Rex',bark:true})")))
                .as("valid-dog").isZero();
            // Invalid: unknown discriminator value
            assertThat(errCount(validate, cx.eval("js", "({kind:'fish',name:'Nemo'})")))
                .as("unknown-kind").isGreaterThan(0);
            // Invalid: missing discriminator
            assertThat(errCount(validate, cx.eval("js", "({name:'Rex',bark:true})")))
                .as("missing-disc").isGreaterThan(0);
            // Invalid: not an object
            assertThat(errCount(validate, "hello")).as("not-object").isGreaterThan(0);
        }
    }

    // --- Helpers ---

    private static Context jsContext() {
        return Context.newBuilder("js")
            .allowIO(IOAccess.ALL)
            .option("js.esm-eval-returns-exports", "true")
            .option("js.ecmascript-version", "2020")
            .build();
    }

    private static Value evalModule(Context cx, Path modulePath) throws Exception {
        final var source = Source.newBuilder("js", modulePath.toFile())
            .mimeType("application/javascript+module")
            .build();
        return cx.eval(source);
    }

    private static int errCount(Value validateFn, Object value) {
        return (int) validateFn.execute(value).getArraySize();
    }
}
