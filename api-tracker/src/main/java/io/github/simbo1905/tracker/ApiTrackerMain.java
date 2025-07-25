package io.github.simbo1905.tracker;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import jdk.sandbox.java.util.json.JsonParseException;

public class ApiTrackerMain {
    public static void main(String[] args) {
        String testJson = """
        {
            "module": "api-tracker",
            "status": "ok",
            "dependencies": [
                "java-util-json-java21"
            ],
            "active": true
        }
        """
;
        System.out.println("Parsing test JSON in api-tracker module...");

        try {
            JsonValue parsedValue = Json.parse(testJson);
            if (parsedValue instanceof JsonObject jsonObject) {
                System.out.println("Successfully parsed JsonObject!");
                System.out.println("Module: " + ((JsonString) jsonObject.members().get("module")).value());
                System.out.println("Status: " + ((JsonString) jsonObject.members().get("status")).value());
            }
        } catch (JsonParseException e) {
            System.err.println("Failed to parse JSON: " + e.getMessage());
        }
    }
}
