package json.java21.jdt;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/// Executes the Microsoft json-document-transforms JSON fixtures against this implementation.
///
/// Fixtures are vendored into:
/// `json-java21-jdt/src/test/resources/microsoft-json-document-transforms/Inputs/`
class MicrosoftJdtFixturesTest extends JdtLoggingConfig {

    private static final Logger LOG = Logger.getLogger(MicrosoftJdtFixturesTest.class.getName());

    static Stream<Arguments> microsoftFixtures() throws IOException {
        final Path base = Path.of(System.getProperty("jdt.test.resources"))
            .resolve("microsoft-json-document-transforms")
            .resolve("Inputs");

        if (!Files.isDirectory(base)) {
            throw new IllegalStateException("Missing fixture directory: " + base);
        }

        try (Stream<Path> paths = Files.walk(base)) {
            final var files = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> p.getFileName().toString().contains(".Transform"))
                .filter(p -> !p.toString().contains("/Skipped/"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();

            return files.stream()
                .map(p -> Arguments.of(base.relativize(p).toString(), p));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("microsoftFixtures")
    void microsoftFixture(String fixtureName, Path transformFile) throws Exception {
        LOG.info(() -> "TEST: microsoftFixture - " + fixtureName);

        final Path dir = transformFile.getParent();
        final String transformFileName = transformFile.getFileName().toString();

        final int transformIdx = transformFileName.indexOf(".Transform");
        assertThat(transformIdx)
            .as("transform file name must contain '.Transform': %s", transformFileName)
            .isGreaterThan(0);

        final String testName = transformFileName.substring(0, transformIdx);
        final int dot = testName.indexOf('.');
        final String category = dot >= 0 ? testName.substring(0, dot) : testName;

        final Path sourceFile = dir.resolve(category + ".Source.json");
        final Path expectedFile = dir.resolve(testName + ".Expected.json");

        assertThat(sourceFile)
            .as("missing source fixture for %s", fixtureName)
            .exists();
        assertThat(expectedFile)
            .as("missing expected fixture for %s", fixtureName)
            .exists();

        final JsonValue source = Json.parse(readUtf8(sourceFile));
        final JsonValue transform = Json.parse(readUtf8(transformFile));
        final JsonValue expected = Json.parse(readUtf8(expectedFile));

        final JsonValue actual = Jdt.transform(source, transform);

        assertThat(actual)
            .as(() -> "fixture: " + fixtureName + "\nExpected:\n" + Json.toDisplayString(expected, 2) + 
                "\n\nActual:\n" + Json.toDisplayString(actual, 2))
            .isEqualTo(expected);
    }

    private static String readUtf8(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        // Some upstream fixtures are saved with a UTF-8 BOM; Json.parse does not accept it.
        return text.startsWith("\ufeff") ? text.substring(1) : text;
    }
}
