package jdk.sandbox.internal.util.json;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonBoolean;
import jdk.sandbox.java.util.json.JsonNull;
import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonPatternMatchingTests {

    private String identifyJsonValue(JsonValue jsonValue) {
        return switch (jsonValue) {
            case JsonObject o -> "Object with " + o.members().size() + " members";
            case JsonArray a -> "Array with " + a.elements().size() + " elements";
            case JsonString s -> "String with value: " + s.string();
            case JsonNumber n -> "Number with value: " + n.toDouble();
            case JsonBoolean b -> "Boolean with value: " + b.bool();
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

        assertThat(identifyJsonValue(jsonObject.members().get("myObject"))).isEqualTo("Object with 0 members");
        assertThat(identifyJsonValue(jsonObject.members().get("myArray"))).isEqualTo("Array with 2 elements");
        assertThat(identifyJsonValue(jsonObject.members().get("myString"))).isEqualTo("String with value: hello");
        assertThat(identifyJsonValue(jsonObject.members().get("myNumber"))).isEqualTo("Number with value: 123.45");
        assertThat(identifyJsonValue(jsonObject.members().get("myBoolean"))).isEqualTo("Boolean with value: true");
        assertThat(identifyJsonValue(jsonObject.members().get("myNull"))).isEqualTo("Null");
    }
}
