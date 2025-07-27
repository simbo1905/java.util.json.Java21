package jdk.sandbox.internal.util.json;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonBoolean;
import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class JsonParserTests {

    @Test
    void testParseComplexJson() {
        JsonObject jsonObject = complexJsonObject();

        assertThat(((JsonString) jsonObject.members().get("name")).value()).isEqualTo("John Doe");
        assertThat(((JsonNumber) jsonObject.members().get("age")).toNumber().longValue()).isEqualTo(30L);
        assertThat(((JsonBoolean) jsonObject.members().get("isStudent")).value()).isFalse();

        JsonArray courses = (JsonArray) jsonObject.members().get("courses");
        assertThat(courses.values()).hasSize(2);

        JsonObject course1 = (JsonObject) courses.values().getFirst();
        assertThat(((JsonString) course1.members().get("title")).value()).isEqualTo("History");
        assertThat(((JsonNumber) course1.members().get("credits")).toNumber().longValue()).isEqualTo(3L);

        JsonObject course2 = (JsonObject) courses.values().get(1);
        assertThat(((JsonString) course2.members().get("title")).value()).isEqualTo("Math");
        assertThat(((JsonNumber) course2.members().get("credits")).toNumber().longValue()).isEqualTo(4L);

        JsonObject address = (JsonObject) jsonObject.members().get("address");
        assertThat(((JsonString) address.members().get("street")).value()).isEqualTo("123 Main St");
        assertThat(((JsonString) address.members().get("city")).value()).isEqualTo("Anytown");
    }

    private static JsonObject complexJsonObject() {
        String json = """
                {
                    "name": "John Doe",
                    "age": 30,
                    "isStudent": false,
                    "courses": [
                        {"title": "History", "credits": 3},
                        {"title": "Math", "credits": 4}
                    ],
                    "address": {
                        "street": "123 Main St",
                        "city": "Anytown"
                    }
                }
                """;

        JsonParser parser = new JsonParser(json.toCharArray());
      return (JsonObject) parser.parseRoot();
    }
}
