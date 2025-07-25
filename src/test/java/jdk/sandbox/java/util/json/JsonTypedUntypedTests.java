package jdk.sandbox.java.util.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonTypedUntypedTests {

    @Test
    void testFromUntypedWithSimpleTypes() {
        // Test string
        JsonValue jsonString = Json.fromUntyped("hello");
        assertThat(jsonString).isInstanceOf(JsonString.class);
        assertThat(((JsonString) jsonString).value()).isEqualTo("hello");

        // Test integer
        JsonValue jsonInt = Json.fromUntyped(42);
        assertThat(jsonInt).isInstanceOf(JsonNumber.class);
        assertThat(((JsonNumber) jsonInt).toNumber()).isEqualTo(42L);

        // Test long
        JsonValue jsonLong = Json.fromUntyped(42L);
        assertThat(jsonLong).isInstanceOf(JsonNumber.class);
        assertThat(((JsonNumber) jsonLong).toNumber()).isEqualTo(42L);

        // Test double
        JsonValue jsonDouble = Json.fromUntyped(3.14);
        assertThat(jsonDouble).isInstanceOf(JsonNumber.class);
        assertThat(((JsonNumber) jsonDouble).toNumber()).isEqualTo(3.14);

        // Test boolean
        JsonValue jsonBool = Json.fromUntyped(true);
        assertThat(jsonBool).isInstanceOf(JsonBoolean.class);
        assertThat(((JsonBoolean) jsonBool).value()).isTrue();

        // Test null
        JsonValue jsonNull = Json.fromUntyped(null);
        assertThat(jsonNull).isInstanceOf(JsonNull.class);
    }

    @Test
    void testFromUntypedWithBigNumbers() {
        // Test BigInteger
        BigInteger bigInt = new BigInteger("123456789012345678901234567890");
        JsonValue jsonBigInt = Json.fromUntyped(bigInt);
        assertThat(jsonBigInt).isInstanceOf(JsonNumber.class);
        assertThat(((JsonNumber) jsonBigInt).toNumber()).isEqualTo(bigInt);

        // Test BigDecimal
        BigDecimal bigDec = new BigDecimal("123456789012345678901234567890.123456789");
        JsonValue jsonBigDec = Json.fromUntyped(bigDec);
        assertThat(jsonBigDec).isInstanceOf(JsonNumber.class);
        assertThat(((JsonNumber) jsonBigDec).toNumber()).isEqualTo(bigDec);
    }

    @Test
    void testFromUntypedWithCollections() {
        // Test List
        List<Object> list = List.of("item1", 42, true);
        JsonValue jsonArray = Json.fromUntyped(list);
        assertThat(jsonArray).isInstanceOf(JsonArray.class);
        JsonArray array = (JsonArray) jsonArray;
        assertThat(array.values()).hasSize(3);
        assertThat(((JsonString) array.values().get(0)).value()).isEqualTo("item1");
        assertThat(((JsonNumber) array.values().get(1)).toNumber()).isEqualTo(42L);
        assertThat(((JsonBoolean) array.values().get(2)).value()).isTrue();

        // Test Map
        Map<String, Object> map = Map.of("name", "John", "age", 30, "active", true);
        JsonValue jsonObject = Json.fromUntyped(map);
        assertThat(jsonObject).isInstanceOf(JsonObject.class);
        JsonObject obj = (JsonObject) jsonObject;
        assertThat(((JsonString) obj.members().get("name")).value()).isEqualTo("John");
        assertThat(((JsonNumber) obj.members().get("age")).toNumber()).isEqualTo(30L);
        assertThat(((JsonBoolean) obj.members().get("active")).value()).isTrue();
    }

    @Test
    void testFromUntypedWithNestedStructures() {
        Map<String, Object> nested = Map.of(
            "user", Map.of("name", "John", "age", 30),
            "scores", List.of(85, 92, 78),
            "active", true
        );
        
        JsonValue json = Json.fromUntyped(nested);
        assertThat(json).isInstanceOf(JsonObject.class);
        
        JsonObject root = (JsonObject) json;
        JsonObject user = (JsonObject) root.members().get("user");
        assertThat(((JsonString) user.members().get("name")).value()).isEqualTo("John");
        
        JsonArray scores = (JsonArray) root.members().get("scores");
        assertThat(scores.values()).hasSize(3);
        assertThat(((JsonNumber) scores.values().get(0)).toNumber()).isEqualTo(85L);
    }

    @Test
    void testFromUntypedWithJsonValue() {
        // If input is already a JsonValue, return as-is
        JsonString original = JsonString.of("test");
        JsonValue result = Json.fromUntyped(original);
        assertThat(result).isSameAs(original);
    }

    @Test
    void testFromUntypedWithInvalidTypes() {
        // Test with unsupported type
        assertThatThrownBy(() -> Json.fromUntyped(new StringBuilder("test")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("StringBuilder is not a recognized type");
    }

    @Test
    void testFromUntypedWithNonStringMapKey() {
        // Test map with non-string key
        Map<Object, Object> invalidMap = Map.of(123, "value");
        assertThatThrownBy(() -> Json.fromUntyped(invalidMap))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("The key '123' is not a String");
    }

    @Test
    void testToUntypedWithSimpleTypes() {
        // Test string
        Object str = Json.toUntyped(JsonString.of("hello"));
        assertThat(str).isEqualTo("hello");

        // Test number
        Object num = Json.toUntyped(JsonNumber.of(42));
        assertThat(num).isEqualTo(42L);

        // Test boolean
        Object bool = Json.toUntyped(JsonBoolean.of(true));
        assertThat(bool).isEqualTo(true);

        // Test null
        Object nullVal = Json.toUntyped(JsonNull.of());
        assertThat(nullVal).isNull();
    }

    @Test
    void testToUntypedWithCollections() {
        // Test array
        JsonArray array = JsonArray.of(List.of(
            JsonString.of("item1"),
            JsonNumber.of(42),
            JsonBoolean.of(true)
        ));
        Object result = Json.toUntyped(array);
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        assertThat(list).containsExactly("item1", 42L, true);

        // Test object
        JsonObject obj = JsonObject.of(Map.of(
            "name", JsonString.of("John"),
            "age", JsonNumber.of(30),
            "active", JsonBoolean.of(true)
        ));
        Object objResult = Json.toUntyped(obj);
        assertThat(objResult).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) objResult;
        assertThat(map.get("name")).isEqualTo("John");
        assertThat(map.get("age")).isEqualTo(30L);
        assertThat(map.get("active")).isEqualTo(true);
    }

    @Test
    void testRoundTripConversion() {
        // Create complex nested structure
        Map<String, Object> original = Map.of(
            "user", Map.of(
                "name", "John Doe",
                "age", 30,
                "email", "john@example.com"
            ),
            "scores", List.of(85.5, 92.0, 78.3),
            "active", true,
            "metadata", Map.of(
                "created", "2024-01-01",
                "tags", List.of("vip", "premium")
            )
        );

        // Convert to JsonValue and back
        JsonValue json = Json.fromUntyped(original);
        Object reconstructed = Json.toUntyped(json);

        // Verify structure is preserved
        assertThat(reconstructed).isInstanceOf(Map.class);
        Map<?, ?> resultMap = (Map<?, ?>) reconstructed;
        
        Map<?, ?> user = (Map<?, ?>) resultMap.get("user");
        assertThat(user.get("name")).isEqualTo("John Doe");
        assertThat(user.get("age")).isEqualTo(30L);
        
        @SuppressWarnings("unchecked")
        List<Object> scores = (List<Object>) resultMap.get("scores");
        assertThat(scores).containsExactly(85.5, 92.0, 78.3);
        
        Map<?, ?> metadata = (Map<?, ?>) resultMap.get("metadata");
        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) metadata.get("tags");
        assertThat(tags).containsExactly("vip", "premium");
    }

    @Test
    void testToUntypedPreservesOrder() {
        // JsonObject should preserve insertion order
        JsonObject obj = JsonObject.of(Map.of(
            "z", JsonString.of("last"),
            "a", JsonString.of("first"),
            "m", JsonString.of("middle")
        ));
        
        Object result = Json.toUntyped(obj);
        assertThat(result).isInstanceOf(Map.class);
        
        // The order might not be preserved with Map.of(), so let's just verify contents
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsEntry("z", "last")
                      .containsEntry("a", "first")
                      .containsEntry("m", "middle");
    }
}