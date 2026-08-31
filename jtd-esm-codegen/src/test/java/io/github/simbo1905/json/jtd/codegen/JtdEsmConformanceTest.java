package io.github.simbo1905.json.jtd.codegen;

import jdk.incubator.java.util.json.Json;
import jdk.incubator.java.util.json.JsonArray;
import jdk.incubator.java.util.json.JsonBoolean;
import jdk.incubator.java.util.json.JsonNull;
import jdk.incubator.java.util.json.JsonNumber;
import jdk.incubator.java.util.json.JsonObject;
import jdk.incubator.java.util.json.JsonString;
import jdk.incubator.java.util.json.JsonValue;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Runs the official json-typedef-spec validation test suite against
/// generated ESM validators executed in GraalVM Polyglot JS.
///
/// Test data: `validation.json` from
/// <https://github.com/jsontypedef/json-typedef-spec/blob/master/tests/validation.json>
class JtdEsmConformanceTest extends JtdEsmCodegenLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JtdEsmConformanceTest.class.getName());

    private static Context context;
    private static Map<String, JsonValue> casesByName;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setupAll() throws IOException {
        final var raw = JtdSpecTestExtractor.getValidationTestDataStream();
        final var jsonText = new String(raw.readAllBytes(), StandardCharsets.UTF_8);
        raw.close();
        final var root = Json.parse(jsonText);
        assert root instanceof JsonObject : "expected top-level object";
        final var obj = (JsonObject) root;
        casesByName = obj.asMap();
        context = GraalJsRunner.createContext();
    }

    @AfterAll
    static void tearDownAll() {
        if (context != null) {
            context.close(true);
        }
    }

    static Stream<Arguments> cases() {
        return casesByName.entrySet().stream()
            .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void generatedEsmMatchesSpecSuite(String name, JsonValue caseValue) throws IOException {
        LOG.info(() -> "SPEC-ESM: " + name);

        final var caseObj = (JsonObject) caseValue;
        final var schema = caseObj.asMap().get("schema");
        final var instance = caseObj.asMap().get("instance");
        final var expectedErrors = (JsonArray) caseObj.asMap().get("errors");

        // Generate the ESM validator exactly as the CLI would
        final var rootNode = JtdParser.parseValue(schema);
        final var nameBytes = name.getBytes(StandardCharsets.UTF_8);
        final var digest = Sha256.digest(new ByteArrayInputStream(nameBytes));
        final String shaHex = Sha256.hex(digest);
        final String shaPrefix8 = Sha256.hexPrefix8(digest);
        final String js = EsmRenderer.render(rootNode, shaHex, shaPrefix8);
        final String safeName = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        final Path modulePath = tempDir.resolve(safeName + ".js");
        Files.writeString(modulePath, js, StandardCharsets.UTF_8);

        // Load and execute in GraalVM JS
        final var exports = GraalJsRunner.loadValidatorModule(context, modulePath);
        final var jsInstance = context.eval("js", "(" + toJsonText(instance) + ")");
        final var actual = GraalJsRunner.validate(exports, jsInstance).stream()
            .sorted(ERR_CMP)
            .toList();

        final var expected = expectedErrors.asList().stream()
            .map(e -> {
                final var errObj = (JsonObject) e;
                final var ip = toJsonPointer((JsonArray) errObj.asMap().get("instancePath"));
                final var sp = toJsonPointer((JsonArray) errObj.asMap().get("schemaPath"));
                return Map.of("instancePath", ip, "schemaPath", sp);
            })
            .sorted(ERR_CMP)
            .toList();

        assertThat(actual)
            .as("errors for: " + name)
            .containsExactlyElementsOf(expected);
    }

    /// Converts fixture error path token arrays into JSON-pointer strings,
    /// matching the conversion used by the bytecode codegen conformance test.
    private static String toJsonPointer(JsonArray tokens) {
        if (tokens.asList().isEmpty()) return "";
        final var sb = new StringBuilder();
        for (final var token : tokens.asList()) {
            sb.append('/');
            sb.append(((JsonString) token).asString());
        }
        return sb.toString();
    }

    private static final Comparator<Map<String, String>> ERR_CMP =
        Comparator.comparing((Map<String, String> e) -> e.get("instancePath"))
            .thenComparing(e -> e.get("schemaPath"));

    /// Serializes a parsed JSON value back to strict JSON text with full
    /// string escaping, safe for evaluation as a JavaScript expression.
    static String toJsonText(JsonValue v) {
        return switch (v) {
            case JsonNull ignored -> "null";
            case JsonBoolean b -> Boolean.toString(b.asBoolean());
            case JsonNumber n -> n.toString();
            case JsonString s -> quote(s.asString());
            case JsonArray a -> join(a.asList().stream().map(JtdEsmConformanceTest::toJsonText).toList(), "[", "]");
            case JsonObject o -> join(o.asMap().entrySet().stream()
                .map(e -> quote(e.getKey()) + ":" + toJsonText(e.getValue())).toList(), "{", "}");
        };
    }

    private static String join(List<String> parts, String open, String close) {
        final var sb = new StringBuilder(open);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(parts.get(i));
        }
        return sb.append(close).toString();
    }

    private static String quote(String s) {
        final var sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
