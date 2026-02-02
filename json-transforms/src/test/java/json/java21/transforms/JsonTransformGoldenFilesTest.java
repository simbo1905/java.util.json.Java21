package json.java21.transforms;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public final class JsonTransformGoldenFilesTest extends JsonTransformsLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonTransformGoldenFilesTest.class.getName());

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource("inputs")
    void goldenFiles(String category, String testName) throws IOException {
        LOG.info(() -> "TEST: goldenFiles category=" + category + " testName=" + testName);

        final var base = Path.of(System.getProperty("jsontransforms.test.resources"));
        final var dir = base.resolve("Inputs").resolve(category);

        final var sourceName = testName.substring(0, testName.lastIndexOf('.'));
        final var sourcePath = dir.resolve(sourceName + ".Source.json");
        final var transformPath = dir.resolve(testName + ".Transform.json");
        final var expectedPath = dir.resolve(testName + ".Expected.json");

        final JsonValue source = parseFile(sourcePath);
        final JsonValue transform = parseFile(transformPath);
        final JsonValue expected = parseFile(expectedPath);

        final var program = JsonTransform.parse(transform);
        final var actual = program.run(source);

        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> inputs() throws IOException {
        final var base = Path.of(System.getProperty("jsontransforms.test.resources"));
        final var inputsBase = base.resolve("Inputs");
        final List<String> categories = List.of("Default", "Remove", "Rename", "Replace", "Merge");

        Stream<Arguments> all = Stream.empty();
        for (final var category : categories) {
            final var dir = inputsBase.resolve(category);
            try (var stream = Files.list(dir)) {
                final var testNames = stream
                        .filter(p -> p.getFileName().toString().endsWith(".Transform.json"))
                        .map(p -> p.getFileName().toString())
                        .map(name -> name.substring(0, name.length() - ".Transform.json".length()))
                        .sorted()
                        .toList();
                all = Stream.concat(all, testNames.stream().map(testName -> Arguments.of(category, testName)));
            }
        }
        return all;
    }

    private static JsonValue parseFile(Path path) throws IOException {
        final var text = Files.readString(path);
        return Json.parse(stripBom(text));
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }
}

