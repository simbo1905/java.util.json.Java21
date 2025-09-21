package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.github.simbo1905.json.schema.SchemaLogging.LOG;

/// Base class for all schema tests.
/// - Emits an INFO banner per test.
/// - Provides common helpers for loading resources and assertions.
class JsonSchemaTestBase extends JsonSchemaLoggingConfig {

    @BeforeEach
    void announce(TestInfo testInfo) {
        final String cls = testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownTest");
        final String name = testInfo.getTestMethod().map(java.lang.reflect.Method::getName)
                .orElseGet(testInfo::getDisplayName);
        LOG.info(() -> "TEST: " + cls + "#" + name);
    }

    protected final JsonValue readJson(String resourcePath) {
        return Json.parse(readText(resourcePath));
    }

    protected final String readText(String resourcePath) {
        try {
            Path p = Path.of(Objects.requireNonNull(
                    getClass().getClassLoader().getResource(resourcePath), resourcePath
            ).toURI());
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }

    protected final URI uriOf(String relativeResourcePath) {
        return TestResourceUtils.getTestResourceUri(relativeResourcePath);
    }

    protected final JsonSchema.ValidationResult validate(JsonSchema schema, JsonValue instance) {
        return schema.validate(instance);
    }

    protected final void assertValid(JsonSchema schema, String instanceJson) {
        final var res = schema.validate(Json.parse(instanceJson));
        org.assertj.core.api.Assertions.assertThat(res.valid()).isTrue();
    }

    protected final void assertInvalid(JsonSchema schema, String instanceJson) {
        final var res = schema.validate(Json.parse(instanceJson));
        org.assertj.core.api.Assertions.assertThat(res.valid()).isFalse();
    }

    protected static CapturedLogs captureLogs(java.util.logging.Level level) {
        return new CapturedLogs(level);
    }

    static final class CapturedLogs implements AutoCloseable {
        private final java.util.logging.Handler handler;
        private final List<String> lines = new ArrayList<>();
        private final java.util.logging.Level original;

        CapturedLogs(java.util.logging.Level level) {
            original = LOG.getLevel();
            LOG.setLevel(level);
            handler = new java.util.logging.Handler() {
                @Override public void publish(java.util.logging.LogRecord record) {
                    if (record.getLevel().intValue() >= level.intValue()) {
                        lines.add(record.getMessage());
                    }
                }
                @Override public void flush() { }
                @Override public void close() throws SecurityException { }
            };
            LOG.addHandler(handler);
        }

        List<String> lines() { return List.copyOf(lines); }

        @Override
        public void close() {
            LOG.removeHandler(handler);
            LOG.setLevel(original);
        }
    }
}
