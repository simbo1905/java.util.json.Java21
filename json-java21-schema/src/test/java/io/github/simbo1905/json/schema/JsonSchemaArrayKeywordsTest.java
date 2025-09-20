package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonSchemaArrayKeywordsTest extends JsonSchemaTestBase {

    @Test
    void testContains_only_defaults() {
        // Test contains with default minContains=1, maxContains=∞
        String schemaJson = """
            {
                "type": "array",
                "contains": { "type": "integer" }
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - contains at least one integer
        assertThat(schema.validate(Json.parse("[\"x\", 1, \"y\"]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1, 2, 3]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1]")).valid()).isTrue();
        
        // Invalid - no integers
        assertThat(schema.validate(Json.parse("[\"x\", \"y\"]")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("[]")).valid()).isFalse();
    }

    @Test
    void testContains_minContains_maxContains() {
        // Test contains with explicit min/max constraints
        String schemaJson = """
            {
                "type": "array",
                "contains": { "type": "string" },
                "minContains": 2,
                "maxContains": 3
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - exactly 2-3 strings
        assertThat(schema.validate(Json.parse("[\"a\",\"b\",\"c\"]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[\"a\",\"b\"]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1, \"a\", 2, \"b\"]")).valid()).isTrue();
        
        // Invalid - too few matches
        assertThat(schema.validate(Json.parse("[\"a\"]")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("[1, 2, \"a\"]")).valid()).isFalse();
        
        // Invalid - too many matches
        assertThat(schema.validate(Json.parse("[\"a\",\"b\",\"c\",\"d\"]")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("[\"a\",\"b\",\"c\",\"d\",\"e\"]")).valid()).isFalse();
    }

    @Test
    void testContains_minContains_zero() {
        // Test minContains=0 (allow zero matches)
        String schemaJson = """
            {
                "type": "array",
                "contains": { "type": "boolean" },
                "minContains": 0
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - zero or more booleans
        assertThat(schema.validate(Json.parse("[]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1, 2, 3]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[true, false]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1, true, 2]")).valid()).isTrue();
    }

    @Test
    void testUniqueItems_structural() {
        // Test uniqueItems with structural equality
        String schemaJson = """
            {
                "type": "array",
                "uniqueItems": true
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - all unique
        assertThat(schema.validate(Json.parse("[1, 2, 3]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[\"a\", \"b\"]")).valid()).isTrue();
        
        // Invalid - duplicate numbers
        assertThat(schema.validate(Json.parse("[1, 2, 2]")).valid()).isFalse();
        
        // Invalid - duplicate objects (different key order)
        assertThat(schema.validate(Json.parse("[{\"a\":1,\"b\":2},{\"b\":2,\"a\":1}]")).valid()).isFalse();
        
        // Invalid - duplicate arrays
        assertThat(schema.validate(Json.parse("[[1,2],[1,2]]")).valid()).isFalse();
        
        // Valid - objects with different values
        assertThat(schema.validate(Json.parse("[{\"a\":1,\"b\":2},{\"a\":1,\"b\":3}]")).valid()).isTrue();
    }

    @Test
    void testUniqueItems_withComplexObjects() {
        // Test uniqueItems with nested structures
        String schemaJson = """
            {
                "type": "array",
                "uniqueItems": true
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - different nested structures
        assertThat(schema.validate(Json.parse("[{\"x\":{\"y\":1}},{\"x\":{\"y\":2}}]")).valid()).isTrue();
        
        // Invalid - same nested structure (different order)
        assertThat(schema.validate(Json.parse("[{\"x\":{\"y\":1,\"z\":2}},{\"x\":{\"z\":2,\"y\":1}}]")).valid()).isFalse();
        
        // Valid - arrays with different contents
        assertThat(schema.validate(Json.parse("[[1, 2, 3], [3, 2, 1]]")).valid()).isTrue();
        
        // Invalid - same array contents
        assertThat(schema.validate(Json.parse("[[1, 2, 3], [1, 2, 3]]")).valid()).isFalse();
    }

    @Test
    void testPrefixItems_withTailItems() {
        // Test prefixItems with trailing items validation
        String schemaJson = """
            {
                "prefixItems": [
                    {"type": "integer"},
                    {"type": "string"}
                ],
                "items": {"type": "boolean"}
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - correct prefix + tail items
        assertThat(schema.validate(Json.parse("[1,\"x\",true,false]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1,\"x\",true]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1,\"x\"]")).valid()).isTrue();
        
        // Invalid - wrong prefix type
        assertThat(schema.validate(Json.parse("[\"x\",1]")).valid()).isFalse();
        
        // Invalid - wrong tail type
        assertThat(schema.validate(Json.parse("[1,\"x\",42]")).valid()).isFalse();
        
        // Invalid - missing prefix items
        assertThat(schema.validate(Json.parse("[1]")).valid()).isFalse();
    }

    @Test
    void testPrefixItems_only() {
        // Test prefixItems without items (extras allowed)
        String schemaJson = """
            {
                "prefixItems": [
                    {"type": "integer"}
                ]
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - correct prefix + any extras
        assertThat(schema.validate(Json.parse("[1]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1,\"anything\",{},null]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1,2,3,4,5]")).valid()).isTrue();
        
        // Invalid - wrong prefix type
        assertThat(schema.validate(Json.parse("[\"not integer\"]")).valid()).isFalse();
    }

    @Test
    void testPrefixItems_withMinMaxItems() {
        // Test prefixItems combined with min/max items
        String schemaJson = """
            {
                "prefixItems": [
                    {"type": "integer"},
                    {"type": "string"}
                ],
                "minItems": 2,
                "maxItems": 4
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - within bounds
        assertThat(schema.validate(Json.parse("[1,\"x\"]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1,\"x\",true]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1,\"x\",true,false]")).valid()).isTrue();
        
        // Invalid - too few items
        assertThat(schema.validate(Json.parse("[1]")).valid()).isFalse();
        
        // Invalid - too many items
        assertThat(schema.validate(Json.parse("[1,\"x\",true,false,5]")).valid()).isFalse();
    }

    @Test
    void testCombinedArrayFeatures() {
        // Test complex combination of all array features
        String schemaJson = """
            {
                "type": "array",
                "prefixItems": [
                    {"type": "string"},
                    {"type": "number"}
                ],
                "items": {"type": ["boolean", "null"]},
                "uniqueItems": true,
                "contains": {"type": "null"},
                "minContains": 1,
                "maxContains": 2,
                "minItems": 3,
                "maxItems": 6
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - meets all constraints (all positional validations pass)
        assertThat(schema.validate(Json.parse("[\"start\", 42, true, false, null]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[\"start\", 42, null, true, false]")).valid()).isTrue();
        
        // Invalid - too few items
        assertThat(schema.validate(Json.parse("[\"start\", 42]")).valid()).isFalse();
        
        // Invalid - too many items  
        assertThat(schema.validate(Json.parse("[\"start\", 42, true, false, true, false]")).valid()).isFalse();
        
        // Invalid - too many contains
        assertThat(schema.validate(Json.parse("[\"start\", 42, true, null, null, null]")).valid()).isFalse();
        
        // Invalid - duplicate items
        assertThat(schema.validate(Json.parse("[\"start\", 42, true, true, null]")).valid()).isFalse();
        
        // Invalid - wrong tail type
        assertThat(schema.validate(Json.parse("[\"start\", 42, \"not boolean or null\", null]")).valid()).isFalse();
    }

    @Test
    void testContains_withComplexSchema() {
        // Test contains with complex nested schema
        String schemaJson = """
            {
                "type": "array",
                "contains": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "age": {"type": "integer", "minimum": 18}
                    },
                    "required": ["name", "age"]
                },
                "minContains": 1
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        // Valid - contains matching object
        assertThat(schema.validate(Json.parse("[{\"name\":\"Alice\",\"age\":25},\"x\",1]")).valid()).isTrue();
        assertThat(schema.validate(Json.parse("[1,2,{\"name\":\"Bob\",\"age\":30}]")).valid()).isTrue();
        
        // Invalid - no matching objects
        assertThat(schema.validate(Json.parse("[1,2,3]")).valid()).isFalse();
        assertThat(schema.validate(Json.parse("[{\"name\":\"Charlie\"}]")).valid()).isFalse(); // missing age
        assertThat(schema.validate(Json.parse("[{\"name\":\"Dave\",\"age\":16}]")).valid()).isFalse(); // age too low
    }

    @Test
    void testUniqueItems_deepStructural() {
        /// Test deep structural equality for uniqueItems with nested objects and arrays
        String schemaJson = """
            {
                "type": "array",
                "uniqueItems": true
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        /// Invalid: deeply nested identical structures
        assertThat(schema.validate(Json.parse("[{\"x\":[1,{\"y\":2}]},{\"x\":[1,{\"y\":2}]}]")).valid()).isFalse();
        
        /// Valid: different nested values
        assertThat(schema.validate(Json.parse("[{\"x\":[1,{\"y\":2}]},{\"x\":[1,{\"y\":3}]}]")).valid()).isTrue();
        
        /// Valid: arrays with different order
        assertThat(schema.validate(Json.parse("[[1,2],[2,1]]")).valid()).isTrue();
        
        /// Invalid: identical arrays
        assertThat(schema.validate(Json.parse("[[1,2],[1,2]]")).valid()).isFalse();
    }

    @Test
    void testPrefixItems_withTrailingItemsValidation() {
        /// Test prefixItems with trailing items schema validation
        String schemaJson = """
            {
                "prefixItems": [
                    {"const": 1},
                    {"const": 2}
                ],
                "items": {"type": "integer"}
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        /// Valid: exact prefix match with valid trailing items
        assertThat(schema.validate(Json.parse("[1,2,3,4]")).valid()).isTrue();
        
        /// Invalid: valid prefix but wrong tail type
        assertThat(schema.validate(Json.parse("[1,2,\"x\"]")).valid()).isFalse();
        
        /// Invalid: wrong prefix order
        assertThat(schema.validate(Json.parse("[2,1,3]")).valid()).isFalse();
        
        /// Invalid: incomplete prefix
        assertThat(schema.validate(Json.parse("[1]")).valid()).isFalse();
    }

    @Test
    void testContains_minContainsZero() {
        /// Test contains with minContains=0 (allows zero matches)
        String schemaJson = """
            {
                "type": "array",
                "contains": {"type": "boolean"},
                "minContains": 0
            }
            """;
        
        JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
        
        /// Valid: empty array (zero matches allowed)
        assertThat(schema.validate(Json.parse("[]")).valid()).isTrue();
        
        /// Valid: no booleans (zero matches allowed)
        assertThat(schema.validate(Json.parse("[1,2,3]")).valid()).isTrue();
        
        /// Valid: some booleans (still allowed)
        assertThat(schema.validate(Json.parse("[true,false]")).valid()).isTrue();
        
        /// Valid: mixed with booleans
        assertThat(schema.validate(Json.parse("[1,true,2]")).valid()).isTrue();
    }
}
