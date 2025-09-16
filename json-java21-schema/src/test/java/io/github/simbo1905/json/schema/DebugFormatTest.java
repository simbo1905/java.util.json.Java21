package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DebugFormatTest extends JsonSchemaLoggingConfig {

    @Test
    void debugEmailFormat() {
        /// Debug email format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "email"
            }
            """;
        
        System.out.println("Schema JSON: " + schemaJson);
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        System.out.println("Schema compiled with format assertion enabled");
        
        // Test the failing case
        String testEmail = "\"a@b\"";
        System.out.println("Testing email: " + testEmail);
        
        var result = schema.validate(Json.parse(testEmail));
        System.out.println("Valid: " + result.valid());
        System.out.println("Errors: " + result.errors());
        
        if (!result.valid()) {
            for (var error : result.errors()) {
                System.out.println("Path: '" + error.path() + "', Message: '" + error.message() + "'");
            }
        }
        
        // Test a valid case
        String testEmail2 = "\"a@b.co\"";
        System.out.println("\\nTesting email: " + testEmail2);
        
        var result2 = schema.validate(Json.parse(testEmail2));
        System.out.println("Valid2: " + result2.valid());
        System.out.println("Errors2: " + result2.errors());
        
        // Manual assertion to see the exact values
        assertThat(result.valid()).as("Email 'a@b' should be invalid").isFalse();
        assertThat(result2.valid()).as("Email 'a@b.co' should be valid").isTrue();
    }
}