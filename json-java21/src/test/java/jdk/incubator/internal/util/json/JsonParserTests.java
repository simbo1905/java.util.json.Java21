package jdk.incubator.internal.util.json;

import jdk.incubator.java.util.json.JsonArray;
import jdk.incubator.java.util.json.JsonBoolean;
import jdk.incubator.java.util.json.JsonNumber;
import jdk.incubator.java.util.json.JsonObject;
import jdk.incubator.java.util.json.JsonString;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class JsonParserTests {

    @Test
    void testParseComplexJson() {
        JsonObject jsonObject = complexJsonObject();

        assertThat(((JsonString) jsonObject.asMap().get("name")).asString()).isEqualTo("John Doe");
        assertThat(((JsonNumber) jsonObject.asMap().get("age")).asLong()).isEqualTo(30L);
        assertThat(((JsonBoolean) jsonObject.asMap().get("isStudent")).asBoolean()).isFalse();

        JsonArray courses = (JsonArray) jsonObject.asMap().get("courses");
        assertThat(courses.asList()).hasSize(2);

        JsonObject course1 = (JsonObject) courses.asList().getFirst();
        assertThat(((JsonString) course1.asMap().get("title")).asString()).isEqualTo("History");
        assertThat(((JsonNumber) course1.asMap().get("credits")).asLong()).isEqualTo(3L);

        JsonObject course2 = (JsonObject) courses.asList().get(1);
        assertThat(((JsonString) course2.asMap().get("title")).asString()).isEqualTo("Math");
        assertThat(((JsonNumber) course2.asMap().get("credits")).asLong()).isEqualTo(4L);

        JsonObject address = (JsonObject) jsonObject.asMap().get("address");
        assertThat(((JsonString) address.asMap().get("street")).asString()).isEqualTo("123 Main St");
        assertThat(((JsonString) address.asMap().get("city")).asString()).isEqualTo("Anytown");
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
