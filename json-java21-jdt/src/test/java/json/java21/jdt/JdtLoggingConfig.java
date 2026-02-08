package json.java21.jdt;

import org.junit.jupiter.api.BeforeAll;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Base class for JDT tests that configures logging.
///
/// Extends this class to automatically configure JUL logging based on
/// the `java.util.logging.ConsoleHandler.level` system property.
abstract class JdtLoggingConfig {

    @BeforeAll
    static void configureLogging() {
        final var levelStr = System.getProperty("java.util.logging.ConsoleHandler.level", "INFO");
        final var level = Level.parse(levelStr);

        // Configure root logger
        final var rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);

        // Configure console handler
        for (final var handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(level);
            }
        }

        // Configure JDT package logger
        final var jdtLogger = Logger.getLogger("json.java21.jdt");
        jdtLogger.setLevel(level);

        // Configure JsonPath package logger
        final var jsonPathLogger = Logger.getLogger("json.java21.jsonpath");
        jsonPathLogger.setLevel(level);
    }
}
