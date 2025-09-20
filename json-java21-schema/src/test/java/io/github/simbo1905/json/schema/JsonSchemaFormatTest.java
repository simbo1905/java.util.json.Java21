package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import static org.assertj.core.api.Assertions.*;

class JsonSchemaFormatTest extends JsonSchemaTestBase {
  @Test
  void testCommonFormats_whenAssertionOn_invalidsFail_validsPass() {
    // Toggle "assert formats" ON (wire however your implementation exposes it).
    // If you use a system property, ensure it's read at compile() time.
    System.setProperty("jsonschema.format.assertion", "true");

    // Invalids must FAIL when assertion is on
    final var uuidSchema = JsonSchema.compile(Json.parse("""
        { "type":"string", "format":"uuid" }
        """));
    assertThat(uuidSchema.validate(Json.parse("\"not-a-uuid\"")).valid()).isFalse();

    final var emailSchema = JsonSchema.compile(Json.parse("""
        { "type":"string", "format":"email" }
        """));
    assertThat(emailSchema.validate(Json.parse("\"no-at-sign\"")).valid()).isFalse();

    final var ipv4Schema = JsonSchema.compile(Json.parse("""
        { "type":"string", "format":"ipv4" }
        """));
    assertThat(ipv4Schema.validate(Json.parse("\"999.0.0.1\"")).valid()).isFalse();

    // Valids must PASS
    assertThat(uuidSchema.validate(Json.parse("\"123e4567-e89b-12d3-a456-426614174000\"")).valid()).isTrue();
    assertThat(emailSchema.validate(Json.parse("\"user@example.com\"")).valid()).isTrue();
    assertThat(ipv4Schema.validate(Json.parse("\"192.168.0.1\"")).valid()).isTrue();
  }

  @Test
  void testFormats_whenAssertionOff_areAnnotationsOnly() {
    // Toggle "assert formats" OFF (annotation-only)
    System.setProperty("jsonschema.format.assertion", "false");

    final var uuidSchema = JsonSchema.compile(Json.parse("""
        { "type":"string", "format":"uuid" }
        """));
    final var emailSchema = JsonSchema.compile(Json.parse("""
        { "type":"string", "format":"email" }
        """));
    final var ipv4Schema = JsonSchema.compile(Json.parse("""
        { "type":"string", "format":"ipv4" }
        """));

    // Invalid instances should PASS schema when assertion is off
    assertThat(uuidSchema.validate(Json.parse("\"not-a-uuid\"")).valid()).isTrue();
    assertThat(emailSchema.validate(Json.parse("\"no-at-sign\"")).valid()).isTrue();
    assertThat(ipv4Schema.validate(Json.parse("\"999.0.0.1\"")).valid()).isTrue();
  }
    @Test
    void testUuidFormat() {
        /// Test UUID format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "uuid"
            }
            """;
        
        // With format assertion disabled (default) - all values should be valid
        JsonSchema schemaAnnotation = JsonSchema.compile(Json.parse(schemaJson));
        assertThat(schemaAnnotation.validate(Json.parse("\"123e4567-e89b-12d3-a456-426614174000\"")).valid()).isTrue();
        assertThat(schemaAnnotation.validate(Json.parse("\"123e4567e89b12d3a456426614174000\"")).valid()).isTrue();
        assertThat(schemaAnnotation.validate(Json.parse("\"not-a-uuid\"")).valid()).isTrue();
        
        // With format assertion enabled - only valid UUIDs should pass
        JsonSchema schemaAssertion = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        assertThat(schemaAssertion.validate(Json.parse("\"123e4567-e89b-12d3-a456-426614174000\"")).valid()).isTrue();
        assertThat(schemaAssertion.validate(Json.parse("\"123e4567e89b12d3a456426614174000\"")).valid()).isFalse();
        assertThat(schemaAssertion.validate(Json.parse("\"not-a-uuid\"")).valid()).isFalse();
    }

    @Test
    void testEmailFormat() {
        /// Test email format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "email"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid emails
        assertThat(schema.validate(Json.parse("\"a@b.co\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"first.last@example.io\"")).valid()).isTrue();
        
        // Invalid emails
        assertThat(schema.validate(Json.parse("\"a@b\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\" a@b.co\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"a@b..co\"")).valid()).isFalse();
    }

    @Test
    void testIpv4Format() {
        /// Test IPv4 format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "ipv4"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid IPv4
        assertThat(schema.validate(Json.parse("\"192.168.0.1\"")).valid()).isTrue();
        
        // Invalid IPv4
        assertThat(schema.validate(Json.parse("\"256.1.1.1\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"1.2.3\"")).valid()).isFalse();
    }

    @Test
    void testIpv6Format() {
        /// Test IPv6 format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "ipv6"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid IPv6
        assertThat(schema.validate(Json.parse("\"2001:0db8::1\"")).valid()).isTrue();
        
        // Invalid IPv6
        assertThat(schema.validate(Json.parse("\"2001:::1\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"abcd\"")).valid()).isFalse();
    }

    @Test
    void testUriFormat() {
        /// Test URI format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "uri"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid URI
        assertThat(schema.validate(Json.parse("\"https://example.com/x?y#z\"")).valid()).isTrue();
        
        // Invalid URI (no scheme)
        assertThat(schema.validate(Json.parse("\"//no-scheme/path\"")).valid()).isFalse();
    }

    @Test
    void testUriReferenceFormat() {
        /// Test URI reference format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "uri-reference"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid URI references
        assertThat(schema.validate(Json.parse("\"../rel/path?x=1\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"#frag\"")).valid()).isTrue();
        
        // Invalid URI reference
        assertThat(schema.validate(Json.parse("\"\\n\"")).valid()).isFalse();
    }

    @Test
    void testHostnameFormat() {
        /// Test hostname format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "hostname"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid hostnames
        assertThat(schema.validate(Json.parse("\"example.com\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"a-b.c-d.e\"")).valid()).isTrue();
        
        // Invalid hostnames
        assertThat(schema.validate(Json.parse("\"-bad.com\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"bad-.com\"")).valid()).isFalse();
    }

    @Test
    void testDateFormat() {
        /// Test date format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "date"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid date
        assertThat(schema.validate(Json.parse("\"2025-09-16\"")).valid()).isTrue();
        
        // Invalid date
        assertThat(schema.validate(Json.parse("\"2025-13-01\"")).valid()).isFalse();
    }

    @Test
    void testTimeFormat() {
        /// Test time format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "time"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid times
        assertThat(schema.validate(Json.parse("\"23:59:59\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"23:59:59.123\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"23:59:59Z\"")).valid()).isTrue();
        
        // Invalid time
        assertThat(schema.validate(Json.parse("\"25:00:00\"")).valid()).isFalse();
    }

    @Test
    void testDateTimeFormat() {
        /// Test date-time format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "date-time"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid date-times
        assertThat(schema.validate(Json.parse("\"2025-09-16T12:34:56Z\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"2025-09-16T12:34:56+01:00\"")).valid()).isTrue();
        
        // Invalid date-times
        assertThat(schema.validate(Json.parse("\"2025-09-16 12:34:56\"")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("\"2025-09-16T25:00:00Z\"")).valid()).isFalse();
    }

    @Test
    void testRegexFormat() {
        /// Test regex format validation
        String schemaJson = """
            {
              "type": "string",
              "format": "regex"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid regex
        assertThat(schema.validate(Json.parse("\"[A-Z]{2,3}\"")).valid()).isTrue();
        
        // Invalid regex
        assertThat(schema.validate(Json.parse("\"*[unclosed\"")).valid()).isFalse();
    }

    @Test
    void testUnknownFormat() {
        /// Test unknown format handling
        String schemaJson = """
            {
              "type": "string",
              "format": "made-up"
            }
            """;
        
        // With format assertion disabled (default) - all values should be valid
        JsonSchema schemaAnnotation = JsonSchema.compile(Json.parse(schemaJson));
        assertThat(schemaAnnotation.validate(Json.parse("\"x\"")).valid()).isTrue();
        assertThat(schemaAnnotation.validate(Json.parse("\"\"")).valid()).isTrue();
        
        // With format assertion enabled - unknown format should be no-op (no errors)
        JsonSchema schemaAssertion = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        assertThat(schemaAssertion.validate(Json.parse("\"x\"")).valid()).isTrue();
        assertThat(schemaAssertion.validate(Json.parse("\"\"")).valid()).isTrue();
    }

    @Test
    void testFormatAssertionRootFlag() {
        /// Test format assertion via root schema flag
        String schemaJson = """
            {
              "formatAssertion": true,
              "type": "string",
              "format": "uuid"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Should validate format due to root flag
        assertThat(schema.validate(Json.parse("\"123e4567-e89b-12d3-a456-426614174000\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"not-a-uuid\"")).valid()).isFalse();
    }

    private static String originalSystemProperty;
    
    @BeforeAll
    static void setUpSystemProperty() {
        originalSystemProperty = System.getProperty("jsonschema.format.assertion");
    }
    
    @AfterAll
    static void tearDownSystemProperty() {
        if (originalSystemProperty != null) {
            System.setProperty("jsonschema.format.assertion", originalSystemProperty);
        } else {
            System.clearProperty("jsonschema.format.assertion");
        }
    }

    @AfterEach
    void resetSystemProperty() {
        // Reset to default state after each test that might change it
        if (originalSystemProperty != null) {
            System.setProperty("jsonschema.format.assertion", originalSystemProperty);
        } else {
            System.clearProperty("jsonschema.format.assertion");
        }
    }
    
    @Test
    void testFormatAssertionSystemProperty() {
        /// Test format assertion via system property
        String schemaJson = """
            {
              "type": "string",
              "format": "uuid"
            }
            """;
        
        // Set system property to enable format assertion
        System.setProperty("jsonschema.format.assertion", "true");
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Should validate format due to system property
        assertThat(schema.validate(Json.parse("\"123e4567-e89b-12d3-a456-426614174000\"")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("\"not-a-uuid\"")).valid()).isFalse();
    }

    @Test
    void testFormatWithOtherConstraints() {
        /// Test format validation combined with other string constraints
        String schemaJson = """
            {
              "type": "string",
              "format": "email",
              "minLength": 5,
              "maxLength": 50,
              "pattern": "^[a-z]+@[a-z]+\\\\.[a-z]+$"
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson), new JsonSchema.Options(true));
        
        // Valid: meets all constraints
        assertThat(schema.validate(Json.parse("\"test@example.com\"")).valid()).isTrue();
        
        // Invalid: valid email but doesn't match pattern (uppercase)
        assertThat(schema.validate(Json.parse("\"Test@Example.com\"")).valid()).isFalse();
        
        // Invalid: valid email but too short
        assertThat(schema.validate(Json.parse("\"a@b\"")).valid()).isFalse();
        
        // Invalid: matches pattern but not valid email format
        assertThat(schema.validate(Json.parse("\"test@example\"")).valid()).isFalse();
    }
}
