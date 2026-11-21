package json.java21.jsonpointer;

import org.junit.jupiter.api.BeforeAll;

import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Base class for JSON Pointer tests that configures JUL logging from system properties.
/// All test classes should extend this class to enable consistent logging behaviour.
public class JsonPointerLoggingConfig {

    @BeforeAll
    static void enableJulDebug() {
        final var root = Logger.getLogger("");
        final var levelProp = System.getProperty("java.util.logging.ConsoleHandler.level");
        var targetLevel = Level.INFO;
        if (levelProp != null) {
            try {
                targetLevel = Level.parse(levelProp.trim());
            } catch (IllegalArgumentException ex) {
                try {
                    targetLevel = Level.parse(levelProp.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    Logger.getLogger(JsonPointerLoggingConfig.class.getName())
                            .warning(() -> "Unrecognized logging level from "
                                    + "'java.util.logging.ConsoleHandler.level': " + levelProp);
                }
            }
        }
        if (root.getLevel() == null || root.getLevel().intValue() > targetLevel.intValue()) {
            root.setLevel(targetLevel);
        }
        for (final Handler handler : root.getHandlers()) {
            final var handlerLevel = handler.getLevel();
            if (handlerLevel == null || handlerLevel.intValue() > targetLevel.intValue()) {
                handler.setLevel(targetLevel);
            }
        }
    }
}
