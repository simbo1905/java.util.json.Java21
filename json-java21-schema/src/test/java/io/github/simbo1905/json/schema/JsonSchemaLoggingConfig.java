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

        // Ensure test resource base is absolute and portable across CI and local runs
        String prop = System.getProperty("json.schema.test.resources");
        if (prop == null || prop.isBlank()) {
            java.nio.file.Path base = java.nio.file.Paths.get("src", "test", "resources").toAbsolutePath();
            System.setProperty("json.schema.test.resources", base.toString());
            Logger.getLogger(JsonSchemaLoggingConfig.class.getName()).config(
                () -> "json.schema.test.resources set to " + base);
        }
    }
}
