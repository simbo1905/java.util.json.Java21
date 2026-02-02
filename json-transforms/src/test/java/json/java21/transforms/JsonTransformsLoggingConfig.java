package json.java21.transforms;

import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Base class for json-transforms tests that configures JUL logging from system properties.
/// All test classes should extend this class to enable consistent logging behavior.
public class JsonTransformsLoggingConfig {

    @BeforeAll
    static void enableJulDebug() {
        final var log = Logger.getLogger(JsonTransformsLoggingConfig.class.getName());
        final var root = Logger.getLogger("");
        final var levelProp = System.getProperty("java.util.logging.ConsoleHandler.level");

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
            final var handlerLevel = handler.getLevel();
            if (handlerLevel == null || handlerLevel.intValue() > targetLevel.intValue()) {
                handler.setLevel(targetLevel);
            }
        }

        final var prop = System.getProperty("jsontransforms.test.resources");
        if (prop == null || prop.isBlank()) {
            Path base = Paths.get("src", "test", "resources").toAbsolutePath();
            System.setProperty("jsontransforms.test.resources", base.toString());
            log.config(() -> "jsontransforms.test.resources set to " + base);
        }
    }
}

