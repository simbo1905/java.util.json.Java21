package io.github.simbo1905.json.schema;

import org.junit.jupiter.api.BeforeAll;
import java.util.Locale;
import java.util.logging.*;

public class JsonSchemaLoggingConfig {
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
                    targetLevel = Level.INFO;
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
    }
}
