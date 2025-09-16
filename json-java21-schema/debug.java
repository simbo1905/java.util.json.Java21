import jdk.sandbox.java.util.json.Json;
import io.github.simbo1905.json.schema.JsonSchema;

public class Debug {
    public static void main(String[] args) {
        var schemaJson = Json.parse("""
            {
              "$defs": {
                "deny": false,
                "allow": true
              },
              "one": { "$ref":"#/$defs/allow" },
              "two": { "$ref":"#/$defs/deny" }
            }
            """);
        
        try {
            var schema = JsonSchema.compile(schemaJson);
            System.out.println("Schema compiled successfully!");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}