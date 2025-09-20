package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.simbo1905.json.schema.SchemaLogging.LOG;

/// Integration tests: validate OpenRPC documents using a minimal embedded meta-schema.
/// Resources:
/// - Schema: src/test/resources/openrpc/schema.json
/// - Examples: src/test/resources/openrpc/examples/*.json
///   Files containing "-bad-" are intentionally invalid and must fail validation.
class OpenRPCSchemaValidationIT extends JsonSchemaLoggingConfig {

    private static String readResource(String name) throws IOException {
        try {
            URL url = Objects.requireNonNull(OpenRPCSchemaValidationIT.class.getClassLoader().getResource(name), name);
            return Files.readString(Path.of(url.toURI()), StandardCharsets.UTF_8);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @TestFactory
    Stream<DynamicTest> validateOpenRPCExamples() throws Exception {
        LOG.info(() -> "TEST: " + getClass().getSimpleName() + "#validateOpenRPCExamples");
        // Compile the minimal OpenRPC schema (self-contained, no remote $ref)
        String schemaJson = readResource("openrpc/schema.json");
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));

        // Discover example files
        URL dirUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("openrpc/examples"),
                "missing openrpc examples directory");
        Path dir = Path.of(dirUrl.toURI());

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jsons = files
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();

            assertThat(jsons).isNotEmpty();

            return jsons.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
                LOG.info(() -> "TEST: " + getClass().getSimpleName() + "#" + path.getFileName());
                String doc = Files.readString(path, StandardCharsets.UTF_8);
                boolean expectedValid = !path.getFileName().toString().contains("-bad-");
                boolean actualValid = schema.validate(Json.parse(doc)).valid();
                Assertions.assertThat(actualValid)
                        .as("validation of %s", path.getFileName())
                        .isEqualTo(expectedValid);
            }));
        }
    }
}
