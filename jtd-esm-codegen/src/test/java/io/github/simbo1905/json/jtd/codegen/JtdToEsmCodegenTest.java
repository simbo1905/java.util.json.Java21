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
    void parsesBareAdditionalPropertiesAsEmptyForm() {
        LOG.info(() -> "Running parsesBareAdditionalPropertiesAsEmptyForm");
        final var root = JtdParser.parseString("""
                {"additionalProperties": true}
                """);
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.EmptyNode.class);
        final var rootFalse = JtdParser.parseString("""
                {"additionalProperties": false}
                """);
        assertThat(rootFalse.rootSchema()).isInstanceOf(JtdAst.EmptyNode.class);
    }

    @Test
    void resolvesForwardRefsInDefinitions() {
        LOG.info(() -> "Running resolvesForwardRefsInDefinitions");
        final var root = JtdParser.parseString("""
                {"definitions": {"b": {"ref": "a"}, "a": {"type": "string"}},
                 "properties": {"x": {"ref": "b"}}}
                """);
        assertThat(root.definitions()).containsKeys("a", "b");
        assertThat(root.rootSchema()).isInstanceOf(JtdAst.PropertiesNode.class);
    }

    @Test
    void rejectsUnknownRefWithKnownDefinitionNames() {
        LOG.info(() -> "Running rejectsUnknownRefWithKnownDefinitionNames");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"definitions": {"a": {"type": "string"}}, "ref": "nonexistent"}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nonexistent")
            .hasMessageContaining("a");
    }

    @Test
    void rejectsUnknownRefNestedInProperties() {
        LOG.info(() -> "Running rejectsUnknownRefNestedInProperties");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"definitions": {"a": {"type": "string"}},
                 "properties": {"x": {"elements": {"ref": "missing"}}}}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void rejectsEmptyEnum() {
        LOG.info(() -> "Running rejectsEmptyEnum");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"enum": []}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void rejectsDuplicateEnumValues() {
        LOG.info(() -> "Running rejectsDuplicateEnumValues");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"enum": ["a", "b", "a"]}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate")
            .hasMessageContaining("a");
    }

    @Test
    void rejectsOverlappingRequiredAndOptionalKeys() {
        LOG.info(() -> "Running rejectsOverlappingRequiredAndOptionalKeys");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"properties": {"k": {"type": "string"}},
                 "optionalProperties": {"k": {"type": "string"}}}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("k");
    }

    @Test
    void rejectsDiscriminatorVariantThatIsNotPropertiesForm() {
        LOG.info(() -> "Running rejectsDiscriminatorVariantThatIsNotPropertiesForm");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"discriminator": "t", "mapping": {"v": {"type": "string"}}}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("properties");
    }

    @Test
    void rejectsDiscriminatorVariantThatIsNullable() {
        LOG.info(() -> "Running rejectsDiscriminatorVariantThatIsNullable");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"discriminator": "t", "mapping": {"v": {"nullable": true, "properties": {}}}}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("properties");
    }

    @Test
    void rejectsDiscriminatorTagInsideVariantProperties() {
        LOG.info(() -> "Running rejectsDiscriminatorTagInsideVariantProperties");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"discriminator": "t", "mapping": {"v": {"properties": {"t": {"type": "string"}}}}}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("t");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"discriminator": "t", "mapping": {"v": {"optionalProperties": {"t": {"type": "string"}}}}}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("t");
    }

    @Test
    void rejectsNestedDefinitions() {
        LOG.info(() -> "Running rejectsNestedDefinitions");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"properties": {"x": {"definitions": {"a": {"type": "string"}}, "type": "string"}}}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("definitions");
        assertThatThrownBy(() -> JtdParser.parseString("""
                {"definitions": {"outer": {"definitions": {"inner": {"type": "string"}}, "type": "string"}}}
                """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("definitions");
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

    @Test
    void generatedBareAdditionalPropertiesValidatorAcceptsAnything(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedBareAdditionalPropertiesValidatorAcceptsAnything");
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {"additionalProperties": true, "metadata": {"id": "bare-additional"}}
                """, StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        try (var cx = jsContext()) {
            final var exports = evalModule(cx, outJs);
            final var validate = exports.getMember("validate");

            // Empty form accepts any JSON value per RFC 8927 Section 2.2
            assertThat(errCount(validate, cx.eval("js", "null"))).as("null").isZero();
            assertThat(errCount(validate, true)).as("boolean").isZero();
            assertThat(errCount(validate, 42)).as("number").isZero();
            assertThat(errCount(validate, "hello")).as("string").isZero();
            assertThat(errCount(validate, cx.eval("js", "[]"))).as("array").isZero();
            assertThat(errCount(validate, cx.eval("js", "({a:1})"))).as("object").isZero();
        }
    }

    @Test
    void generatedValidatorEscapesAdversarialPropertyKeys(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedValidatorEscapesAdversarialPropertyKeys");
        final var schemaJson = """
                {"properties":{"a\\"b":{"type":"string"},"e\\nf":{"type":"int8"}},"optionalProperties":{"c\\\\d":{"type":"string"}},"metadata":{"id":"escaping-test"}}
                """;
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, schemaJson.strip(), StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        try (var cx = jsContext()) {
            // Loading the module throws if the generated JS is syntactically invalid
            final var exports = evalModule(cx, outJs);
            final var validate = exports.getMember("validate");

            // Valid: all adversarial keys present with correct types
            assertThat(errCount(validate, cx.eval("js", "({'a\"b':'x','e\\nf':1,'c\\\\d':'y'})")))
                .as("valid-adversarial").isZero();
            // Invalid: wrong type under adversarial key
            assertThat(errCount(validate, cx.eval("js", "({'a\"b':'x','e\\nf':'not-int'})")))
                .as("wrong-type").isGreaterThan(0);
            // Invalid: missing required adversarial key
            assertThat(errCount(validate, cx.eval("js", "({'c\\\\d':'y'})")))
                .as("missing-required").isGreaterThan(0);
            // Invalid: unknown extra key (additionalProperties defaults to false)
            assertThat(errCount(validate, cx.eval("js", "({'a\"b':'x','e\\nf':1,'extra':true})")))
                .as("extra-key").isGreaterThan(0);
        }
    }

    @Test
    void generatedDiscriminatorValidatorEscapesAdversarialNames(@TempDir Path tempDir) throws Exception {
        LOG.info(() -> "Running generatedDiscriminatorValidatorEscapesAdversarialNames");
        final var schemaJson = """
                {"discriminator":"ta\\"g","mapping":{"x\\"y":{"properties":{"p":{"type":"string"}}}},"metadata":{"id":"disc-escaping-test"}}
                """;
        final Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, schemaJson.strip(), StandardCharsets.UTF_8);
        final Path outJs = JtdToEsmCli.run(schemaFile, tempDir);

        try (var cx = jsContext()) {
            final var exports = evalModule(cx, outJs);
            final var validate = exports.getMember("validate");

            // Valid: adversarial tag value with valid variant payload
            assertThat(errCount(validate, cx.eval("js", "({'ta\"g':'x\"y','p':'v'})")))
                .as("valid-adversarial-tag").isZero();
            // Invalid: unknown tag value
            assertThat(errCount(validate, cx.eval("js", "({'ta\"g':'other','p':'v'})")))
                .as("unknown-tag").isGreaterThan(0);
            // Invalid: variant payload wrong type
            assertThat(errCount(validate, cx.eval("js", "({'ta\"g':'x\"y','p':1})")))
                .as("wrong-payload").isGreaterThan(0);
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
