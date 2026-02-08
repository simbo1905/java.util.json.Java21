package json.java21.jsonpath.codegen;

import java.util.logging.*;

import org.junit.jupiter.api.BeforeAll;

/// Base class that configures JUL logging from the system property.
class CodegenTestBase {

    static final Logger LOG = Logger.getLogger(CodegenTestBase.class.getPackageName());

    @BeforeAll
    static void configureLogging() {
        final var levelName = System.getProperty("java.util.logging.ConsoleHandler.level", "INFO");
        final var level = Level.parse(levelName);

        final var rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        for (final var handler : rootLogger.getHandlers()) {
            handler.setLevel(level);
        }
    }
}
