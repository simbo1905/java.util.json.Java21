package jdk.sandbox.demo;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonNumber;
import java.util.Map;

public class JsonDemo {
    public static void main(String[] args) {
        // Create a JSON object using the factory method
        JsonObject jsonObject = JsonObject.of(Map.of(
            "name", JsonString.of("John"),
            "age", JsonNumber.of(30)
        ));

        System.out.println(jsonObject.toString());
        
        // Parse JSON string
        String jsonStr = "{\"name\":\"Jane\",\"age\":25}";
        var parsed = Json.parse(jsonStr);
        System.out.println("Parsed: " + parsed);
    }
}