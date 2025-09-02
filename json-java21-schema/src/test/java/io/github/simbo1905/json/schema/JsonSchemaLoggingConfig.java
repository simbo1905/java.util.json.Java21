package io.github.simbo1905.json.schema;

import org.junit.jupiter.api.BeforeAll;
import java.util.logging.*;

public class JsonSchemaLoggingConfig {
    @BeforeAll
    static void enableJulDebug() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.FINEST);          // show FINEST level messages
        for (Handler h : root.getHandlers()) {
            h.setLevel(Level.FINEST);
        }
    }
}
