package io.github.simbo1905.tracker;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonBoolean;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import jdk.sandbox.java.util.json.JsonParseException;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;

/**
 * Main entry point for the API Tracker tool.
 *
 * This tool analyzes Java API structures and tracks changes between
 * the OpenJDK sandbox java.util.json implementation and this backport.
 */
public class ApiTrackerMain {
    private static final Logger LOGGER = Logger.getLogger(ApiTrackerMain.class.getName());

    static {
        // Configure logging with a clean formatter
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);

        // Remove default handlers
        for (var handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        // Add console handler with simple formatting
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        consoleHandler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(java.util.logging.LogRecord record) {
                return String.format("[%s] %s - %s%n",
                    record.getLevel().getName(),
                    record.getLoggerName(),
                    formatMessage(record)
                );
            }
        });
        rootLogger.addHandler(consoleHandler);
    }

    public static void main(String[] args) {
        LOGGER.info("Starting API Tracker v0.1-SNAPSHOT");

        // Validate our JSON parsing works correctly
        if (!validateJsonBackport()) {
            LOGGER.severe("JSON backport validation failed");
            System.exit(1);
        }

        LOGGER.info("JSON backport validation successful");

        // TODO: Implement API analysis logic
        LOGGER.info("API Tracker initialized successfully");
    }

    /**
     * Validates that the JSON backport is working correctly.
     * Tests various JSON structures to ensure compatibility.
     */
    private static boolean validateJsonBackport() {
        try {
            // Test complex JSON structure
            String testJson = """
                {
                    "apiTracker": {
                        "version": "0.1-SNAPSHOT",
                        "modules": {
                            "core": "java-util-json-java21",
                            "tracker": "java-util-json-java21-api-tracker"
                        },
                        "features": [
                            "API extraction",
                            "Structural comparison",
                            "GitHub integration"
                        ],
                        "config": {
                            "autoTrack": true,
                            "createIssues": true,
                            "trackInterval": 86400
                        }
                    }
                }
                """;

            LOGGER.fine("Parsing test JSON structure");
            JsonValue parsedValue = Json.parse(testJson);

            if (!(parsedValue instanceof JsonObject root)) {
                LOGGER.severe("Expected JsonObject but got: " + parsedValue.getClass().getName());
                return false;
            }

            // Navigate the structure to validate parsing
            JsonObject apiTracker = (JsonObject) root.members().get("apiTracker");
            String version = ((JsonString) apiTracker.members().get("version")).value();

            if (!"0.1-SNAPSHOT".equals(version)) {
                LOGGER.severe("Version mismatch: expected 0.1-SNAPSHOT, got " + version);
                return false;
            }

            // Validate nested objects
            JsonObject modules = (JsonObject) apiTracker.members().get("modules");
            LOGGER.fine("Found modules: " + modules.members().size());

            // Validate array handling
            JsonArray features = (JsonArray) apiTracker.members().get("features");
            LOGGER.fine("Found features: " + features.values().size());

            // Validate boolean handling
            JsonObject config = (JsonObject) apiTracker.members().get("config");
            boolean autoTrack = ((JsonBoolean) config.members().get("autoTrack")).value();

            if (!autoTrack) {
                LOGGER.warning("autoTrack is disabled in test configuration");
            }

            // Test edge cases
            testEdgeCases();

            LOGGER.info("All JSON validation tests passed");
            return true;

        } catch (JsonParseException e) {
            LOGGER.log(Level.SEVERE, "JSON parsing failed", e);
            return false;
        } catch (ClassCastException e) {
            LOGGER.log(Level.SEVERE, "Unexpected JSON structure", e);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during validation", e);
            return false;
        }
    }

    /**
     * Tests edge cases for JSON parsing.
     */
    private static void testEdgeCases() throws JsonParseException {
        // Empty object
        Json.parse("{}");

        // Empty array
        Json.parse("[]");

        // Null value
        Json.parse("{\"key\": null}");

        // Unicode handling
        Json.parse("{\"unicode\": \"Hello \\u4e16\\u754c\"}");

        // Number formats
        Json.parse("{\"int\": 42, \"float\": 3.14, \"exp\": 1.23e-4}");

        LOGGER.fine("Edge case tests completed");
    }
}