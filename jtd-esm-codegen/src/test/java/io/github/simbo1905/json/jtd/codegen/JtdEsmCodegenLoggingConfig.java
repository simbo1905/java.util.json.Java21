package io.github.simbo1905.json.jtd.codegen;

import org.junit.jupiter.api.BeforeAll;

import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Base class for JTD ESM codegen tests that configures JUL logging from system properties.
/// All test classes should extend this class to enable consistent logging behavior.
public class JtdEsmCodegenLoggingConfig {
    @BeforeAll
    static void enableJulDebug() {
        final var log = Logger.getLogger(JtdEsmCodegenLoggingConfig.class.getName());
        final Logger root = Logger.getLogger("");

        final String levelProp = System.getProperty("java.util.logging.ConsoleHandler.level");
        Level targetLevel = Level.INFO;
        if (levelProp != null) {
            try {
                targetLevel = Level.parse(levelProp.trim());
            } catch (IllegalArgumentException ex) {
                try {
                    targetLevel = Level.parse(levelProp.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    log.warning(() -> "Unrecognized logging level from 'java.util.logging.ConsoleHandler.level': " + levelProp);
                }
            }
        }

        if (root.getLevel() == null || root.getLevel().intValue() > targetLevel.intValue()) {
            root.setLevel(targetLevel);
        }
        for (Handler handler : root.getHandlers()) {
            final Level handlerLevel = handler.getLevel();
            if (handlerLevel == null || handlerLevel.intValue() > targetLevel.intValue()) {
                handler.setLevel(targetLevel);
            }
        }
    }
}

