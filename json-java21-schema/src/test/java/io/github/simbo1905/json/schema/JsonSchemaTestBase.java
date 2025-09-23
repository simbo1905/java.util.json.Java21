package io.github.simbo1905.json.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

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
}
