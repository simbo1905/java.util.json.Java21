package jdk.sandbox.java.util.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ReadmeDemoTests {

  @Test
  void quickStartExample() {
    // Basic parsing example
    String jsonString = "{\"name\":\"Alice\",\"age\":30}";
    JsonValue value = Json.parse(jsonString);

    assertThat(value).isInstanceOf(JsonObject.class);
    JsonObject obj = (JsonObject) value;
    assertThat(((JsonString) obj.members().get("name")).value()).isEqualTo("Alice");
    assertThat(((JsonNumber) obj.members().get("age")).toNumber()).isEqualTo(30L);

    String roundTrip = value.toString();
    assertThat(roundTrip).isEqualTo(jsonString);
  }

  // Domain model using records
  record User(String name, String email, boolean active) {
  }

  record Team(String teamName, List<User> members) {
  }

  @Test
  void recordMappingExample() {
    // Create a team with users
    Team team = new Team("Engineering", List.of(
        new User("Alice", "alice@example.com", true),
        new User("Bob", "bob@example.com", false)
    ));

    // Convert records to JSON using untyped conversion
    JsonValue teamJson = Json.fromUntyped(Map.of(
        "teamName", team.teamName(),
        "members", team.members().stream()
            .map(u -> Map.of(
                "name", u.name(),
                "email", u.email(),
                "active", u.active()
            ))
            .toList()
    ));

    // Verify the JSON structure
    assertThat(teamJson).isInstanceOf(JsonObject.class);
    JsonObject teamObj = (JsonObject) teamJson;
    assertThat(((JsonString) teamObj.members().get("teamName")).value()).isEqualTo("Engineering");

    JsonArray members = (JsonArray) teamObj.members().get("members");
    assertThat(members.values()).hasSize(2);

    // Parse JSON back to records
    JsonObject parsed = (JsonObject) Json.parse(teamJson.toString());
    Team reconstructed = new Team(
        ((JsonString) parsed.members().get("teamName")).value(),
        ((JsonArray) parsed.members().get("members")).values().stream()
            .map(v -> {
              JsonObject member = (JsonObject) v;
              return new User(
                  ((JsonString) member.members().get("name")).value(),
                  ((JsonString) member.members().get("email")).value(),
                  ((JsonBoolean) member.members().get("active")).value()
              );
            })
            .toList()
    );

    // Verify reconstruction
    assertThat(reconstructed).isEqualTo(team);
    assertThat(reconstructed.teamName()).isEqualTo("Engineering");
    assertThat(reconstructed.members()).hasSize(2);
    assertThat(reconstructed.members().get(0).name()).isEqualTo("Alice");
    assertThat(reconstructed.members().get(0).active()).isTrue();
    assertThat(reconstructed.members().get(1).name()).isEqualTo("Bob");
    assertThat(reconstructed.members().get(1).active()).isFalse();
  }

  @Test
  void builderPatternExample() {
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
            "timestamp", JsonNumber.of(1234567890L)
        )),
        "errors", JsonArray.of(List.of())
    ));

    // Verify structure
    assertThat(((JsonString) response.members().get("status")).value()).isEqualTo("success");

    JsonObject data = (JsonObject) response.members().get("data");
    JsonObject user = (JsonObject) data.members().get("user");
    assertThat(((JsonNumber) user.members().get("id")).toNumber()).isEqualTo(12345L);
    assertThat(((JsonString) user.members().get("name")).value()).isEqualTo("John Doe");

    JsonArray roles = (JsonArray) user.members().get("roles");
    assertThat(roles.values()).hasSize(2);
    assertThat(((JsonString) roles.values().getFirst()).value()).isEqualTo("admin");

    JsonArray errors = (JsonArray) response.members().get("errors");
    assertThat(errors.values()).isEmpty();
  }

  @Test
  void streamingProcessingExample() {
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
    List<String> activeUserEmails = items.values().stream()
        .map(v -> (JsonObject) v)
        .filter(obj -> ((JsonBoolean) obj.members().get("active")).value())
        .map(obj -> ((JsonString) obj.members().get("email")).value())
        .toList();

    // Verify we got only active users
    assertThat(activeUserEmails).containsExactly(
        "alice@example.com",
        "charlie@example.com",
        "eve@example.com"
    );
  }

  @Test
  void errorHandlingExample() {
    // Valid JSON parsing
    String validJson = "{\"valid\": true}";
    JsonValue value = Json.parse(validJson);
    assertThat(value).isInstanceOf(JsonObject.class);

    // Invalid JSON parsing
    String invalidJson = "{invalid json}";
    assertThatThrownBy(() -> Json.parse(invalidJson))
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Expecting a JSON Object member name");
  }

  @Test
  void typeConversionExample() {
    // Using fromUntyped and toUntyped for complex structures
    Map<String, Object> config = Map.of(
        "server", Map.of(
            "host", "localhost",
            "port", 8080,
            "ssl", true
        ),
        "features", List.of("auth", "logging", "metrics"),
        "maxConnections", 1000
    );

    // Convert to JSON
    JsonValue json = Json.fromUntyped(config);

    // Convert back to Java types
    @SuppressWarnings("unchecked")
    Map<String, Object> restored = (Map<String, Object>) Json.toUntyped(json);

    // Verify round-trip conversion
    assert restored != null;
    @SuppressWarnings("unchecked")
    Map<String, Object> server = (Map<String, Object>) restored.get("server");
    assertThat(server.get("host")).isEqualTo("localhost");
    assertThat(server.get("port")).isEqualTo(8080L); // Note: integers become Long
    assertThat(server.get("ssl")).isEqualTo(true);

    @SuppressWarnings("unchecked")
    List<Object> features = (List<Object>) restored.get("features");
    assertThat(features).containsExactly("auth", "logging", "metrics");

    assertThat(restored.get("maxConnections")).isEqualTo(1000L);
  }

  @Test
  void displayFormattingExample() {
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

    // Verify it contains proper formatting (checking key parts)
    assertThat(formatted).contains("{\n");
    assertThat(formatted).contains("  \"name\": \"Alice\"");
    assertThat(formatted).contains("  \"scores\": [");
    assertThat(formatted).contains("    85,");
    assertThat(formatted).contains("    90,");
    assertThat(formatted).contains("    95");
    assertThat(formatted).contains("  ]");
    assertThat(formatted).contains("}");
  }
}
