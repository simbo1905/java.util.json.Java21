package json.java21.jsonpath;

import org.junit.jupiter.api.BeforeAll;
import java.util.Locale;
import java.util.logging.*;

/// Base class for JsonPath tests that configures JUL logging from system properties.
/// All test classes should extend this class to enable consistent logging behavior.
public class JsonPathLoggingConfig {
    @BeforeAll
    static void enableJulDebug() {
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
                    System.err.println("Unrecognized logging level from 'java.util.logging.ConsoleHandler.level': " + levelProp);
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
        String prop = System.getProperty("jsonpath.test.resources");
        if (prop == null || prop.isBlank()) {
            java.nio.file.Path base = java.nio.file.Paths.get("src", "test", "resources").toAbsolutePath();
            System.setProperty("jsonpath.test.resources", base.toString());
            Logger.getLogger(JsonPathLoggingConfig.class.getName()).config(
                () -> "jsonpath.test.resources set to " + base);
        }
    }
}
