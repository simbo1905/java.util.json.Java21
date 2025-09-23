package io.github.simbo1905.json.schema;

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

import static io.github.simbo1905.json.schema.JsonSchema.LOG;
import static org.assertj.core.api.Assertions.assertThat;

/// Integration tests: validate OpenRPC documents using a minimal embedded meta-schema.
/// Resources:
/// - Schema: src/test/resources/openrpc/schema.json
/// - Examples: src/test/resources/openrpc/examples/*.json
///   Files containing "-bad-" are intentionally invalid and must fail validation.
class OpenRPCSchemaValidationIT extends JsonSchemaTestBase {

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
        JsonSchema schema = OpenRPCTestSupport.loadOpenRpcSchema();

        // Discover example files
        List<String> names = OpenRPCTestSupport.exampleNames();
        assertThat(names).isNotEmpty();
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            LOG.info(() -> "TEST: " + getClass().getSimpleName() + "#" + name);
            boolean expectedValid = !name.contains("-bad-");
            boolean actualValid = OpenRPCTestSupport.validateExample(name).valid();
            Assertions.assertThat(actualValid).as("validation of %s", name).isEqualTo(expectedValid);
        }));
    }
}
