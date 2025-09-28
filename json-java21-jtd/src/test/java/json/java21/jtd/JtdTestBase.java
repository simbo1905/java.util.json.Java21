package json.java21.jtd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.logging.Logger;

/// Base class for all JTD tests.
/// - Emits an INFO banner per test.
/// - Provides common helpers for loading resources and assertions.
public class JtdTestBase extends JtdLoggingConfig {

    static final Logger LOG = Logger.getLogger("json.java21.jtd");

    @BeforeEach
    void announce(TestInfo testInfo) {
        final String cls = testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownTest");
        final String name = testInfo.getTestMethod().map(java.lang.reflect.Method::getName)
                .orElseGet(testInfo::getDisplayName);
        LOG.info(() -> "TEST: " + cls + "#" + name);
    }
}
