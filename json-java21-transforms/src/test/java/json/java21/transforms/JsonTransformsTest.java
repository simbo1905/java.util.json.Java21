package json.java21.transforms;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/// Tests for JsonTransforms - tests applying transforms to JSON documents.
/// Based on examples from the Microsoft JSON Document Transforms specification.
class JsonTransformsTest extends JsonTransformsLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonTransformsTest.class.getName());

    @Test
    void testApplyEmptyTransform() {
        LOG.info(() -> "TEST: testApplyEmptyTransform");
        final JsonValue source = Json.parse("""
            {"name": "Alice", "age": 30}
            """);
        final JsonTransforms transform = JsonTransforms.parse("{}");
        
        final JsonValue result = transform.apply(source);
        
        assertThat(result).isInstanceOf(JsonObject.class);
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(2);
        assertThat(((JsonString) obj.members().get("name")).string()).isEqualTo("Alice");
        assertThat(((JsonNumber) obj.members().get("age")).toLong()).isEqualTo(30);
    }

    @Test
    void testApplyValueOpCreate() {
        LOG.info(() -> "TEST: testApplyValueOpCreate - create new property");
        final JsonValue source = Json.parse("""
            {"name": "Alice"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "country": {
                    "@jdt.value": "USA"
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(2);
        assertThat(((JsonString) obj.members().get("name")).string()).isEqualTo("Alice");
        assertThat(((JsonString) obj.members().get("country")).string()).isEqualTo("USA");
    }

    @Test
    void testApplyValueOpOverwrite() {
        LOG.info(() -> "TEST: testApplyValueOpOverwrite - overwrite existing property");
        final JsonValue source = Json.parse("""
            {"name": "Alice", "status": "inactive"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "status": {
                    "@jdt.value": "active"
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(((JsonString) obj.members().get("status")).string()).isEqualTo("active");
    }

    @Test
    void testApplyRemoveOp() {
        LOG.info(() -> "TEST: testApplyRemoveOp");
        final JsonValue source = Json.parse("""
            {"name": "Alice", "age": 30, "secret": "password123"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "secret": {
                    "@jdt.remove": true
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(2);
        assertThat(obj.members().containsKey("secret")).isFalse();
        assertThat(obj.members().containsKey("name")).isTrue();
        assertThat(obj.members().containsKey("age")).isTrue();
    }

    @Test
    void testApplyRemoveOpFalse() {
        LOG.info(() -> "TEST: testApplyRemoveOpFalse - @jdt.remove: false should not remove");
        final JsonValue source = Json.parse("""
            {"name": "Alice", "age": 30}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "age": {
                    "@jdt.remove": false
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(2);
        assertThat(obj.members().containsKey("age")).isTrue();
    }

    @Test
    void testApplyRenameOp() {
        LOG.info(() -> "TEST: testApplyRenameOp");
        final JsonValue source = Json.parse("""
            {"name": "Alice", "age": 30}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "name": {
                    "@jdt.rename": "fullName"
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(2);
        assertThat(obj.members().containsKey("name")).isFalse();
        assertThat(obj.members().containsKey("fullName")).isTrue();
        assertThat(((JsonString) obj.members().get("fullName")).string()).isEqualTo("Alice");
    }

    @Test
    void testApplyRenameOpNonExistent() {
        LOG.info(() -> "TEST: testApplyRenameOpNonExistent - rename non-existent property does nothing");
        final JsonValue source = Json.parse("""
            {"name": "Alice"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "nonExistent": {
                    "@jdt.rename": "newName"
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(1);
        assertThat(obj.members().containsKey("name")).isTrue();
        assertThat(obj.members().containsKey("newName")).isFalse();
    }

    @Test
    void testApplyReplaceOp() {
        LOG.info(() -> "TEST: testApplyReplaceOp");
        final JsonValue source = Json.parse("""
            {"name": "Alice", "status": "inactive"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "status": {
                    "@jdt.replace": "active"
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(((JsonString) obj.members().get("status")).string()).isEqualTo("active");
    }

    @Test
    void testApplyReplaceOpNonExistent() {
        LOG.info(() -> "TEST: testApplyReplaceOpNonExistent - replace non-existent property does nothing");
        final JsonValue source = Json.parse("""
            {"name": "Alice"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "status": {
                    "@jdt.replace": "active"
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(1);
        assertThat(obj.members().containsKey("status")).isFalse();
    }

    @Test
    void testApplyMergeOp() {
        LOG.info(() -> "TEST: testApplyMergeOp");
        final JsonValue source = Json.parse("""
            {
                "config": {
                    "debug": false,
                    "timeout": 1000
                }
            }
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "config": {
                    "@jdt.merge": {
                        "debug": true,
                        "newSetting": "value"
                    }
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        final JsonObject config = (JsonObject) obj.members().get("config");
        assertThat(((JsonBoolean) config.members().get("debug")).bool()).isTrue();
        assertThat(((JsonNumber) config.members().get("timeout")).toLong()).isEqualTo(1000);
        assertThat(((JsonString) config.members().get("newSetting")).string()).isEqualTo("value");
    }

    @Test
    void testApplyMergeOpOnNonExistent() {
        LOG.info(() -> "TEST: testApplyMergeOpOnNonExistent - merge on non-existent creates new object");
        final JsonValue source = Json.parse("""
            {"name": "Alice"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "config": {
                    "@jdt.merge": {
                        "debug": true
                    }
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members().containsKey("config")).isTrue();
        final JsonObject config = (JsonObject) obj.members().get("config");
        assertThat(((JsonBoolean) config.members().get("debug")).bool()).isTrue();
    }

    @Test
    void testApplyMultipleOperations() {
        LOG.info(() -> "TEST: testApplyMultipleOperations");
        final JsonValue source = Json.parse("""
            {
                "name": "Alice",
                "oldProp": "remove me",
                "status": "inactive"
            }
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "name": {
                    "@jdt.rename": "fullName"
                },
                "oldProp": {
                    "@jdt.remove": true
                },
                "status": {
                    "@jdt.value": "active"
                },
                "country": {
                    "@jdt.value": "USA"
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(3);
        assertThat(obj.members().containsKey("fullName")).isTrue();
        assertThat(obj.members().containsKey("name")).isFalse();
        assertThat(obj.members().containsKey("oldProp")).isFalse();
        assertThat(((JsonString) obj.members().get("status")).string()).isEqualTo("active");
        assertThat(((JsonString) obj.members().get("country")).string()).isEqualTo("USA");
    }

    @Test
    void testApplyImplicitValue() {
        LOG.info(() -> "TEST: testApplyImplicitValue - non-object value is implicit @jdt.value");
        final JsonValue source = Json.parse("""
            {"name": "Alice"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "age": 30
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(2);
        assertThat(((JsonNumber) obj.members().get("age")).toLong()).isEqualTo(30);
    }

    @Test
    void testApplyToNonObject() {
        LOG.info(() -> "TEST: testApplyToNonObject - applying to array returns as-is");
        final JsonValue source = Json.parse("[1, 2, 3]");
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "prop": {
                    "@jdt.value": "test"
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        assertThat(result).isInstanceOf(JsonArray.class);
        assertThat(((JsonArray) result).elements()).hasSize(3);
    }

    @Test
    void testApplyNestedTransform() {
        LOG.info(() -> "TEST: testApplyNestedTransform - transform nested object");
        final JsonValue source = Json.parse("""
            {
                "user": {
                    "name": "Alice",
                    "age": 30
                }
            }
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "user": {
                    "name": {
                        "@jdt.rename": "fullName"
                    }
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        final JsonObject user = (JsonObject) obj.members().get("user");
        assertThat(user.members().containsKey("fullName")).isTrue();
        assertThat(user.members().containsKey("name")).isFalse();
        assertThat(((JsonString) user.members().get("fullName")).string()).isEqualTo("Alice");
    }

    @Test
    void testDeepMerge() {
        LOG.info(() -> "TEST: testDeepMerge - deeply nested merge");
        final JsonValue source = Json.parse("""
            {
                "config": {
                    "database": {
                        "host": "localhost",
                        "port": 5432
                    },
                    "cache": {
                        "enabled": true
                    }
                }
            }
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "config": {
                    "@jdt.merge": {
                        "database": {
                            "port": 5433,
                            "ssl": true
                        }
                    }
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        final JsonObject config = (JsonObject) obj.members().get("config");
        final JsonObject database = (JsonObject) config.members().get("database");
        
        assertThat(((JsonString) database.members().get("host")).string()).isEqualTo("localhost");
        assertThat(((JsonNumber) database.members().get("port")).toLong()).isEqualTo(5433);
        assertThat(((JsonBoolean) database.members().get("ssl")).bool()).isTrue();
        
        // Cache should be preserved
        final JsonObject cache = (JsonObject) config.members().get("cache");
        assertThat(((JsonBoolean) cache.members().get("enabled")).bool()).isTrue();
    }

    @Test
    void testApplyValueWithArray() {
        LOG.info(() -> "TEST: testApplyValueWithArray");
        final JsonValue source = Json.parse("""
            {"name": "Alice"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "tags": {
                    "@jdt.value": ["admin", "user"]
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        final JsonArray tags = (JsonArray) obj.members().get("tags");
        assertThat(tags.elements()).hasSize(2);
        assertThat(((JsonString) tags.elements().get(0)).string()).isEqualTo("admin");
        assertThat(((JsonString) tags.elements().get(1)).string()).isEqualTo("user");
    }

    @Test
    void testApplyValueWithObject() {
        LOG.info(() -> "TEST: testApplyValueWithObject");
        final JsonValue source = Json.parse("""
            {"name": "Alice"}
            """);
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "address": {
                    "@jdt.value": {
                        "city": "Seattle",
                        "state": "WA"
                    }
                }
            }
            """);
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        final JsonObject address = (JsonObject) obj.members().get("address");
        assertThat(((JsonString) address.members().get("city")).string()).isEqualTo("Seattle");
        assertThat(((JsonString) address.members().get("state")).string()).isEqualTo("WA");
    }

    @Test
    void testApplyReusableTransform() {
        LOG.info(() -> "TEST: testApplyReusableTransform - same transform applied to multiple docs");
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "status": {
                    "@jdt.value": "processed"
                }
            }
            """);
        
        final JsonValue doc1 = Json.parse("{\"id\": 1}");
        final JsonValue doc2 = Json.parse("{\"id\": 2, \"status\": \"pending\"}");
        final JsonValue doc3 = Json.parse("{\"id\": 3, \"extra\": true}");
        
        final JsonObject result1 = (JsonObject) transform.apply(doc1);
        final JsonObject result2 = (JsonObject) transform.apply(doc2);
        final JsonObject result3 = (JsonObject) transform.apply(doc3);
        
        assertThat(((JsonString) result1.members().get("status")).string()).isEqualTo("processed");
        assertThat(((JsonString) result2.members().get("status")).string()).isEqualTo("processed");
        assertThat(((JsonString) result3.members().get("status")).string()).isEqualTo("processed");
        
        // Original properties preserved
        assertThat(((JsonNumber) result1.members().get("id")).toLong()).isEqualTo(1);
        assertThat(((JsonNumber) result2.members().get("id")).toLong()).isEqualTo(2);
        assertThat(((JsonBoolean) result3.members().get("extra")).bool()).isTrue();
    }

    @Test
    void testParseFromString() {
        LOG.info(() -> "TEST: testParseFromString");
        final JsonValue source = Json.parse("{\"name\": \"Alice\"}");
        final JsonTransforms transform = JsonTransforms.parse("{\"age\": {\"@jdt.value\": 30}}");
        
        final JsonValue result = transform.apply(source);
        
        final JsonObject obj = (JsonObject) result;
        assertThat(obj.members()).hasSize(2);
        assertThat(((JsonNumber) obj.members().get("age")).toLong()).isEqualTo(30);
    }

    @Test
    void testToString() {
        LOG.info(() -> "TEST: testToString");
        final JsonTransforms transform = JsonTransforms.parse("""
            {
                "a": {"@jdt.value": 1},
                "b": {"@jdt.remove": true}
            }
            """);
        
        final String str = transform.toString();
        assertThat(str).contains("JsonTransforms");
        assertThat(str).contains("nodes=2");
    }

    @Test
    void testApplyNullSourceThrows() {
        LOG.info(() -> "TEST: testApplyNullSourceThrows");
        final JsonTransforms transform = JsonTransforms.parse("{}");
        
        assertThatThrownBy(() -> transform.apply(null))
            .isInstanceOf(NullPointerException.class);
    }
}
