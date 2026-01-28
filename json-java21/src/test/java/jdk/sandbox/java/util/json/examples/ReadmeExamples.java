package jdk.sandbox.java.util.json.examples;

import jdk.sandbox.java.util.json.*;

import java.util.List;
import java.util.Map;

/**
 * Standalone examples demonstrating the java.util.json API.
 * This file contains runnable examples that match the README documentation.
 * 
 * To run: 
 *   mvn compile exec:java -Dexec.mainClass="jdk.sandbox.java.util.json.examples.ReadmeExamples"
 */
public class ReadmeExamples {
    
    // Domain model using records
    record User(String name, String email, boolean active) {}
    record Team(String teamName, List<User> members) {}

    public static void main(String[] args) {
        System.out.println("=== Java.util.json API Examples ===\n");
        
        quickStartExample();
        recordMappingExample();
        builderPatternExample();
        streamingProcessingExample();
        errorHandlingExample();
        displayFormattingExample();
        
        System.out.println("\n=== All examples completed successfully! ===");
    }

    static void quickStartExample() {
        System.out.println("1. Quick Start Example");
        System.out.println("----------------------");
        
        // Basic parsing example
        String jsonString = "{\"name\":\"Alice\",\"age\":30}";
        JsonValue value = Json.parse(jsonString);
        
        System.out.println("Parsed JSON: " + jsonString);
        System.out.println("Value type: " + value.getClass().getSimpleName());
        
        JsonObject obj = (JsonObject) value;
        String name = ((JsonString) obj.members().get("name")).string();
        long age = ((JsonNumber) obj.members().get("age")).toLong();
        
        System.out.println("Extracted name: " + name);
        System.out.println("Extracted age: " + age);
        System.out.println("Round trip: " + value.toString());
        System.out.println();
    }

    static void recordMappingExample() {
        System.out.println("2. Record Mapping Example");
        System.out.println("-------------------------");
        
        // Create a team with users
        Team team = new Team("Engineering", List.of(
            new User("Alice", "alice@example.com", true),
            new User("Bob", "bob@example.com", false)
        ));
        
        System.out.println("Original team: " + team);
        
        // Convert records to JSON using typed factories
        JsonValue teamJson = JsonObject.of(Map.of(
            "teamName", JsonString.of(team.teamName()),
            "members", JsonArray.of(team.members().stream()
                .map(u -> JsonObject.of(Map.of(
                    "name", JsonString.of(u.name()),
                    "email", JsonString.of(u.email()),
                    "active", JsonBoolean.of(u.active())
                )))
                .toList())
        ));
        
        System.out.println("JSON representation:");
        System.out.println(teamJson.toString());
        
        // Parse JSON back to records
        JsonObject parsed = (JsonObject) Json.parse(teamJson.toString());
        Team reconstructed = new Team(
            ((JsonString) parsed.members().get("teamName")).string(),
            ((JsonArray) parsed.members().get("members")).elements().stream()
                .map(v -> {
                    JsonObject member = (JsonObject) v;
                    return new User(
                        ((JsonString) member.members().get("name")).string(),
                        ((JsonString) member.members().get("email")).string(),
                        ((JsonBoolean) member.members().get("active")).bool()
                    );
                })
                .toList()
        );
        
        System.out.println("Reconstructed team: " + reconstructed);
        System.out.println("Round trip successful: " + team.equals(reconstructed));
        System.out.println();
    }

    static void builderPatternExample() {
        System.out.println("3. Builder Pattern Example");
        System.out.println("-------------------------");
        
        // Building a REST API response
        JsonObject response = JsonObject.of(Map.of(
            "status", JsonString.of("success"),
            "data", JsonObject.of(Map.of(
                "user", JsonObject.of(Map.of(
                    "id", JsonNumber.of(12345),
                    "name", JsonString.of("John Doe"),
                    "roles", JsonArray.of(List.of(
                        JsonString.of("admin"),
                        JsonString.of("user")
                    ))
                )),
                "timestamp", JsonNumber.of(System.currentTimeMillis())
            )),
            "errors", JsonArray.of(List.of())
        ));
        
        System.out.println("API Response:");
        System.out.println(Json.toDisplayString(response, 2));
        System.out.println();
    }

    static void streamingProcessingExample() {
        System.out.println("4. Streaming Processing Example");
        System.out.println("--------------------------------");
        
        // Create a large array of user records
        String largeJsonArray = """
            [
                {"name": "Alice", "email": "alice@example.com", "active": true},
                {"name": "Bob", "email": "bob@example.com", "active": false},
                {"name": "Charlie", "email": "charlie@example.com", "active": true},
                {"name": "David", "email": "david@example.com", "active": false},
                {"name": "Eve", "email": "eve@example.com", "active": true}
            ]
            """;
        
        // Process a large array of records
        JsonArray items = (JsonArray) Json.parse(largeJsonArray);
        List<String> activeUserEmails = items.elements().stream()
            .map(v -> (JsonObject) v)
            .filter(obj -> ((JsonBoolean) obj.members().get("active")).bool())
            .map(obj -> ((JsonString) obj.members().get("email")).string())
            .toList();
        
        System.out.println("Active user emails: " + activeUserEmails);
        System.out.println();
    }

    static void errorHandlingExample() {
        System.out.println("5. Error Handling Example");
        System.out.println("------------------------");
        
        // Valid JSON parsing
        String validJson = "{\"valid\": true}";
        try {
            JsonValue value = Json.parse(validJson);
            System.out.println("Valid JSON parsed successfully: " + value);
        } catch (JsonParseException e) {
            System.out.println("Unexpected error: " + e.getMessage());
        }
        
        // Invalid JSON parsing
        String invalidJson = "{invalid json}";
        try {
            JsonValue value = Json.parse(invalidJson);
            System.out.println("This shouldn't print");
        } catch (JsonParseException e) {
            System.out.println("Caught expected error: " + e.getMessage());
            System.out.println("Error at line " + e.getErrorLine() + ", position " + e.getErrorPosition());
        }
        System.out.println();
    }

    static void displayFormattingExample() {
        System.out.println("6. Display Formatting Example");
        System.out.println("-----------------------------");
        
        // Create a structured JSON
        JsonObject data = JsonObject.of(Map.of(
            "name", JsonString.of("Alice"),
            "scores", JsonArray.of(List.of(
                JsonNumber.of(85),
                JsonNumber.of(90),
                JsonNumber.of(95)
            ))
        ));
        
        // Format for display
        String formatted = Json.toDisplayString(data, 2);
        System.out.println("Formatted JSON:");
        System.out.println(formatted);
        System.out.println();
    }
}