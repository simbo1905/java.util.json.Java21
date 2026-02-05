package io.github.simbo1905.json.jtd.codegen;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class JtdToEsmCodegenTest extends JtdEsmCodegenLoggingConfig {
    private static final Logger LOG = Logger.getLogger(JtdToEsmCodegenTest.class.getName());

    @Test
    void parsesFlatSchemaSubset() throws Exception {
        LOG.info(() -> "Running parsesFlatSchemaSubset");

        final var json = Files.readString(resource("odc-chart-event-v1.jtd.json"), StandardCharsets.UTF_8);
        final var schema = JtdParser.parseString(json);

        assertThat(schema.id()).isEqualTo("odc-chart-event-v1");
        assertThat(schema.properties().keySet()).containsExactlyInAnyOrder("src", "action", "domain", "data");
        assertThat(schema.optionalProperties().keySet()).containsExactly("ts");
    }

    @Test
    void rejectsUnsupportedFeaturesWithRequiredMessage() {
        LOG.info(() -> "Running rejectsUnsupportedFeaturesWithRequiredMessage");

        final var bad = """
                {
                  "properties": { "x": { "type": "string" } },
                  "elements": { "type": "string" },
                  "metadata": { "id": "bad" }
                }
                """;

        assertThatThrownBy(() -> JtdParser.parseString(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unsupported JTD feature: elements. This experimental tool only supports flat schemas with properties, optionalProperties, type, and enum.");
    }

    @Test
    void rendersEsmModuleWithValidateExport() throws Exception {
        LOG.info(() -> "Running rendersEsmModuleWithValidateExport");

        final var json = Files.readString(resource("odc-chart-event-v1.jtd.json"), StandardCharsets.UTF_8);
        final var schema = JtdParser.parseString(json);
        final var digest = Sha256.digest(resource("odc-chart-event-v1.jtd.json"));
        final var shaHex = Sha256.hex(digest);
        final var shaPrefix8 = Sha256.hexPrefix8(digest);

        final var js = EsmRenderer.render(schema, shaHex, shaPrefix8);

        assertThat(js).contains("export function validate(instance)");
        assertThat(js).contains("const SCHEMA_ID = \"odc-chart-event-v1\"");
        assertThat(js).contains("on_click");
        assertThat(js).contains("schemaPath: \"/properties/src/type\"");
        assertThat(js).contains("schemaPath: \"/properties/action/enum\"");
        assertThat(js).contains("schemaPath: \"/optionalProperties/ts/type\"");
    }

    @Test
    void generatedValidateMatchesExampleCasesWhenNodeAvailable() throws Exception {
        LOG.info(() -> "Running generatedValidateMatchesExampleCasesWhenNodeAvailable");

        assumeTrue(isNodeAvailable(), "Node.js is required for executing generated ES modules in tests");

        final Path temp = Files.createTempDirectory("jtd-esm-codegen-test-");
        Files.writeString(temp.resolve("package.json"), "{ \"type\": \"module\" }", StandardCharsets.UTF_8);

        final Path schemaFile = resource("odc-chart-event-v1.jtd.json");
        final Path outJs = JtdToEsmCli.run(schemaFile, temp);

        final var runner = """
                import { validate } from %s;

                const cases = [
                  { name: "valid_string_data", instance: { src: "bump_chart", action: "on_click", domain: "mbl_comparison.lender", data: "LEEDS" } },
                  { name: "valid_number_data", instance: { src: "bump_chart", action: "on_click", domain: "mbl_comparison.lender", data: 123 } },
                  { name: "valid_with_ts", instance: { src: "bump_chart", action: "on_click", domain: "mbl_comparison.lender", data: "LEEDS", ts: "2025-02-05T10:30:00Z" } },

                  { name: "missing_src", instance: { action: "on_click", domain: "mbl_comparison.lender", data: "LEEDS" } },
                  { name: "src_wrong_type", instance: { src: 123, action: "on_click", domain: "mbl_comparison.lender", data: "LEEDS" } },
                  { name: "action_invalid", instance: { src: "bump_chart", action: "INVALID", domain: "mbl_comparison.lender", data: "LEEDS" } },
                  { name: "ts_invalid", instance: { src: "bump_chart", action: "on_click", domain: "mbl_comparison.lender", data: "LEEDS", ts: "not-a-date" } },
                ];

                const results = cases.map(c => ({ name: c.name, errors: validate(c.instance) }));
                console.log(JSON.stringify(results));
                """.formatted(jsImportSpecifier(outJs));

        Files.writeString(temp.resolve("runner.mjs"), runner, StandardCharsets.UTF_8);

        final var p = new ProcessBuilder("node", temp.resolve("runner.mjs").toString())
                .directory(temp.toFile())
                .redirectErrorStream(true)
                .start();
        final var output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        final int code = p.waitFor();

        assertThat(code).as("node exit code; output:\n%s", output).isEqualTo(0);

        final JsonArray results = (JsonArray) Json.parse(output);
        assertThat(findErrors(results, "valid_string_data")).isEmpty();
        assertThat(findErrors(results, "valid_number_data")).isEmpty();
        assertThat(findErrors(results, "valid_with_ts")).isEmpty();

        assertThat(findErrors(results, "missing_src"))
                .containsExactly(Map.of("instancePath", "", "schemaPath", "/properties/src"));

        assertThat(findErrors(results, "src_wrong_type"))
                .containsExactly(Map.of("instancePath", "/src", "schemaPath", "/properties/src/type"));

        assertThat(findErrors(results, "action_invalid"))
                .containsExactly(Map.of("instancePath", "/action", "schemaPath", "/properties/action/enum"));

        assertThat(findErrors(results, "ts_invalid"))
                .containsExactly(Map.of("instancePath", "/ts", "schemaPath", "/optionalProperties/ts/type"));
    }

    private static Path resource(String name) {
        return Path.of("src", "test", "resources", name).toAbsolutePath();
    }

    private static boolean isNodeAvailable() {
        try {
            final var p = new ProcessBuilder("node", "--version")
                    .redirectErrorStream(true)
                    .start();
            final int code = p.waitFor();
            return code == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String jsImportSpecifier(Path outJs) {
        // With `package.json` { type: "module" } present, .js is treated as ESM.
        final var rel = "./" + outJs.getFileName();
        return JsonString.of(rel).toString();
    }

    private static List<Map<String, String>> findErrors(JsonArray results, String caseName) {
        for (JsonValue v : results.elements()) {
            final var obj = (JsonObject) v;
            if (obj.get("name") instanceof JsonString js && js.string().equals(caseName)) {
                final var errors = (JsonArray) obj.get("errors");
                return errors.elements().stream()
                        .map(e -> (JsonObject) e)
                        .map(e -> Map.of(
                                "instancePath", ((JsonString) e.get("instancePath")).string(),
                                "schemaPath", ((JsonString) e.get("schemaPath")).string()
                        ))
                        .toList();
            }
        }
        throw new AssertionError("Case not found: " + caseName);
    }
}

