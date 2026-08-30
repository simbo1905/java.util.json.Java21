package jdk.incubator.internal.util.json;

import jdk.incubator.java.util.json.JsonArray;
import jdk.incubator.java.util.json.JsonBoolean;
import jdk.incubator.java.util.json.JsonNull;
import jdk.incubator.java.util.json.JsonNumber;
import jdk.incubator.java.util.json.JsonObject;
import jdk.incubator.java.util.json.JsonString;
import jdk.incubator.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonPatternMatchingTests {

    private String identifyJsonValue(JsonValue jsonValue) {
        return switch (jsonValue) {
            case JsonObject o -> "Object with " + o.asMap().size() + " members";
            case JsonArray a -> "Array with " + a.asList().size() + " elements";
            case JsonString s -> "String with value: " + s.asString();
            case JsonNumber n -> "Number with value: " + n.asDouble();
            case JsonBoolean b -> "Boolean with value: " + b.asBoolean();
            case JsonNull ignored -> "Null";
        };
    }

    @Test
    void testPatternMatchingOnJsonTypes() {
        String json = """
        {
            "myObject": {},
            "myArray": [1, 2],
            "myString": "hello",
            "myNumber": 123.45,
            "myBoolean": true,
            "myNull": null
        }
        """;

        JsonParser parser = new JsonParser(json.toCharArray());
        JsonObject jsonObject = (JsonObject) parser.parseRoot();

        assertThat(identifyJsonValue(jsonObject.asMap().get("myObject"))).isEqualTo("Object with 0 members");
        assertThat(identifyJsonValue(jsonObject.asMap().get("myArray"))).isEqualTo("Array with 2 elements");
        assertThat(identifyJsonValue(jsonObject.asMap().get("myString"))).isEqualTo("String with value: hello");
        assertThat(identifyJsonValue(jsonObject.asMap().get("myNumber"))).isEqualTo("Number with value: 123.45");
        assertThat(identifyJsonValue(jsonObject.asMap().get("myBoolean"))).isEqualTo("Boolean with value: true");
        assertThat(identifyJsonValue(jsonObject.asMap().get("myNull"))).isEqualTo("Null");
    }
}
