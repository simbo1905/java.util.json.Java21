package jdk.incubator.java.util.json;

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
    assertThat(((JsonString) obj.asMap().get("name")).asString()).isEqualTo("Alice");
    assertThat(((JsonNumber) obj.asMap().get("age")).asLong()).isEqualTo(30L);

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

    // Verify the JSON structure
    assertThat(teamJson).isInstanceOf(JsonObject.class);
    JsonObject teamObj = (JsonObject) teamJson;
    assertThat(((JsonString) teamObj.asMap().get("teamName")).asString()).isEqualTo("Engineering");

    JsonArray members = (JsonArray) teamObj.asMap().get("members");
    assertThat(members.asList()).hasSize(2);

    // Parse JSON back to records
    JsonObject parsed = (JsonObject) Json.parse(teamJson.toString());
    Team reconstructed = new Team(
        ((JsonString) parsed.asMap().get("teamName")).asString(),
        ((JsonArray) parsed.asMap().get("members")).asList().stream()
            .map(v -> {
              JsonObject member = (JsonObject) v;
              return new User(
                  ((JsonString) member.asMap().get("name")).asString(),
                  ((JsonString) member.asMap().get("email")).asString(),
                  ((JsonBoolean) member.asMap().get("active")).asBoolean()
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
    assertThat(((JsonString) response.asMap().get("status")).asString()).isEqualTo("success");

    JsonObject data = (JsonObject) response.asMap().get("data");
    JsonObject user = (JsonObject) data.asMap().get("user");
    assertThat(((JsonNumber) user.asMap().get("id")).asLong()).isEqualTo(12345L);
    assertThat(((JsonString) user.asMap().get("name")).asString()).isEqualTo("John Doe");

    JsonArray roles = (JsonArray) user.asMap().get("roles");
    assertThat(roles.asList()).hasSize(2);
    assertThat(((JsonString) roles.asList().getFirst()).asString()).isEqualTo("admin");

    JsonArray errors = (JsonArray) response.asMap().get("errors");
    assertThat(errors.asList()).isEmpty();
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
    List<String> activeUserEmails = items.asList().stream()
        .map(v -> (JsonObject) v)
        .filter(obj -> ((JsonBoolean) obj.asMap().get("active")).asBoolean())
        .map(obj -> ((JsonString) obj.asMap().get("email")).asString())
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
    String formatted = Json.toDisplayString(data, "  ");

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
