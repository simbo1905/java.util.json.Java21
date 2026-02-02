package json.java21.transforms;

import org.junit.jupiter.api.BeforeAll;
import java.util.Locale;
import java.util.logging.*;

/// Base class for JSON Transforms tests that configures JUL logging from system properties.
/// All test classes should extend this class to enable consistent logging behavior.
public class JsonTransformsLoggingConfig {
    @BeforeAll
    static void enableJulDebug() {
        final var log = Logger.getLogger(JsonTransformsLoggingConfig.class.getName());
        Logger root = Logger.getLogger("");
        String levelProp = System.getProperty("java.util.logging.ConsoleHandler.level");
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
        // Ensure the root logger honors the most verbose configured level
        if (root.getLevel() == null || root.getLevel().intValue() > targetLevel.intValue()) {
            root.setLevel(targetLevel);
        }
        for (Handler handler : root.getHandlers()) {
            Level handlerLevel = handler.getLevel();
            if (handlerLevel == null || handlerLevel.intValue() > targetLevel.intValue()) {
                handler.setLevel(targetLevel);
            }
        }

        // Ensure test resource base is absolute and portable across CI and local runs
        String prop = System.getProperty("transforms.test.resources");
        if (prop == null || prop.isBlank()) {
            java.nio.file.Path base = java.nio.file.Paths.get("src", "test", "resources").toAbsolutePath();
            System.setProperty("transforms.test.resources", base.toString());
            log.config(() -> "transforms.test.resources set to " + base);
        }
    }
}
