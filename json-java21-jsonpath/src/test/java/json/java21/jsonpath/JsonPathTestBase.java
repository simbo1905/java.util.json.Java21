package json.java21.jsonpath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.logging.Logger;

/// Base class for all JsonPath tests.
/// - Emits an INFO banner per test.
public class JsonPathTestBase extends JsonPathLoggingConfig {

    static final Logger LOG = Logger.getLogger("json.java21.jsonpath");

    @BeforeEach
    void announce(TestInfo testInfo) {
        final String cls = testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownTest");
        final String name = testInfo.getTestMethod().map(java.lang.reflect.Method::getName)
                .orElseGet(testInfo::getDisplayName);
        LOG.info(() -> "TEST: " + cls + "#" + name);
    }
}

