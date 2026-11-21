package jdk.incubator.java.util.json;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Base class for the ported upstream JSON tests. Configures JUL logging from
/// the `java.util.logging.ConsoleHandler.level` system property so that
/// `-Djava.util.logging.ConsoleHandler.level=FINE` (etc.) works uniformly, and
/// announces each test method execution at INFO level.
public class JsonTestLoggingConfig {

    static final Logger LOG = Logger.getLogger(JsonTestLoggingConfig.class.getName());

    @BeforeAll
    static void enableJulDebug() {
        Logger root = Logger.getLogger("");
        String levelProp = System.getProperty("java.util.logging.ConsoleHandler.level");
        Level targetLevel = resolveLevel(levelProp);
        // Ensure the root logger honors the most verbose configured level
        if (root.getLevel() == null || root.getLevel().intValue() > targetLevel.intValue()) {
            root.setLevel(targetLevel);
        }
        for (var handler : root.getHandlers()) {
            Level handlerLevel = handler.getLevel();
            if (handlerLevel == null || handlerLevel.intValue() > targetLevel.intValue()) {
                handler.setLevel(targetLevel);
            }
        }
        LOG.config(() -> "JUL level configured for JSON tests: " + targetLevel);
    }

    private static Level resolveLevel(String levelProp) {
        Level targetLevel = Level.INFO;
        if (levelProp != null) {
            try {
                targetLevel = Level.parse(levelProp.trim());
            } catch (IllegalArgumentException ex) {
                try {
                    targetLevel = Level.parse(levelProp.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    LOG.warning(() -> "Unrecognized logging level from 'java.util.logging.ConsoleHandler.level': " + levelProp);
                }
            }
        }
        return targetLevel;
    }

    @BeforeEach
    void announceTest(TestInfo testInfo) {
        LOG.info(() -> "Running test: " + testInfo.getDisplayName());
    }
}
