package json.java21.jsonpath;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/// Dedicated evaluation tests for logical operators in filters (!, &&, ||, parens).
/// Verifies truth tables and precedence on controlled documents.
class JsonPathFilterEvaluationTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathFilterEvaluationTest.class.getName());

    @Test
    void testLogicalAnd() {
        LOG.info(() -> "TEST: testLogicalAnd (&&)");
        // Truth table for AND
        // Items:
        // 1. T && T -> Match
        // 2. T && F -> No
        // 3. F && T -> No
        // 4. F && F -> No
        var json = Json.parse("""
            [
              {"id": 1, "a": true, "b": true},
              {"id": 2, "a": true, "b": false},
              {"id": 3, "a": false, "b": true},
              {"id": 4, "a": false, "b": false}
            ]
            """);

        var results = JsonPath.parse("$[?(@.a == true && @.b == true)]").query(json);

        assertThat(results).hasSize(1);
        assertThat(asInt(results.getFirst(), "id")).isEqualTo(1);
    }

    @Test
    void testLogicalOr() {
        LOG.info(() -> "TEST: testLogicalOr (||)");
        // Truth table for OR
        // Items:
        // 1. T || T -> Match
        // 2. T || F -> Match
        // 3. F || T -> Match
        // 4. F || F -> No
        var json = Json.parse("""
            [
              {"id": 1, "a": true, "b": true},
              {"id": 2, "a": true, "b": false},
              {"id": 3, "a": false, "b": true},
              {"id": 4, "a": false, "b": false}
            ]
            """);

        var results = JsonPath.parse("$[?(@.a == true || @.b == true)]").query(json);

        assertThat(results).hasSize(3);
        assertThat(results.stream().map(v -> asInt(v, "id")).toList())
            .containsExactly(1, 2, 3);
    }

    @Test
    void testLogicalNot() {
        LOG.info(() -> "TEST: testLogicalNot (!)");
        var json = Json.parse("""
            [
              {"id": 1, "active": true},
              {"id": 2, "active": false},
              {"id": 3}
            ]
            """);

        // !@.active should match where active is false or null/missing (if treated as falsy? strictly missing check is different)
        // In this implementation:
        // !@.active implies we invert the truthiness of @.active.
        // If @.active exists and is true -> false.
        // If @.active exists and is false -> true.
        // If @.active is missing -> ExistsFilter returns false -> !false -> true.
        
        // However, "ExistsFilter" checks for existence. 
        // @.active matches id 1 (true) and 2 (false). 
        // Wait, ExistsFilter checks if the path *exists* and is non-null.
        // Let's verify specific behavior for boolean value comparison vs existence.
        
        // Case A: Existence check negation
        // [?(!@.active)] -> Match items where "active" does NOT exist.
        var missingResults = JsonPath.parse("$[?(!@.active)]").query(json);
        assertThat(missingResults).hasSize(1);
        assertThat(asInt(missingResults.getFirst(), "id")).isEqualTo(3);

        // Case B: Value comparison negation
        // [?(!(@.active == true))]
        var notTrueResults = JsonPath.parse("$[?(!(@.active == true))]").query(json);
        // id 1: active=true -> EQ is true -> !T -> F
        // id 2: active=false -> EQ is false -> !F -> T
        // id 3: active missing -> EQ is false (null != true) -> !F -> T
        assertThat(notTrueResults).hasSize(2);
        assertThat(notTrueResults.stream().map(v -> asInt(v, "id")).toList())
            .containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void testParenthesesPrecedence() {
        LOG.info(() -> "TEST: testParenthesesPrecedence");
        // Logic: A && (B || C) vs (A && B) || C
        // A=true, B=false, C=true
        // A && (B || C) -> T && (F || T) -> T && T -> MATCH
        // (A && B) || C -> (T && F) || T -> F || T -> MATCH (Wait, bad example, both match)
        
        // Let's try: A=false, B=true, C=true
        // A && (B || C) -> F && T -> NO MATCH
        // (A && B) || C -> F || T -> MATCH
        
        var json = Json.parse("""
            [
              {"id": 1, "A": false, "B": true, "C": true}
            ]
            """);

        // Case 1: A && (B || C) -> Expect Empty
        var results1 = JsonPath.parse("$[?(@.A == true && (@.B == true || @.C == true))]").query(json);
        assertThat(results1).isEmpty();

        // Case 2: (A && B) || C -> Expect Match (since C is true)
        // Note: The parser must respect precedence. AND usually binds tighter than OR, but we use parens to force order.
        // Standard precedence: && before ||.
        // So @.A && @.B || @.C means (@.A && @.B) || @.C.
        // Let's verify explicit parens first.
        var results2 = JsonPath.parse("$[?((@.A == true && @.B == true) || @.C == true)]").query(json);
        assertThat(results2).hasSize(1);
    }

    @Test
    void testComplexNestedLogic() {
        LOG.info(() -> "TEST: testComplexNestedLogic");
        // (Price < 10 OR (Category == 'fiction' AND Not Published))
        var json = Json.parse("""
            [
              {"id": 1, "price": 5, "category": "ref", "published": true},
              {"id": 2, "price": 20, "category": "fiction", "published": false},
              {"id": 3, "price": 20, "category": "fiction", "published": true},
              {"id": 4, "price": 20, "category": "ref", "published": false}
            ]
            """);

        var results = JsonPath.parse("$[?(@.price < 10 || (@.category == 'fiction' && !@.published))]").query(json);

        assertThat(results).hasSize(2);
        assertThat(results.stream().map(v -> asInt(v, "id")).toList())
            .containsExactlyInAnyOrder(1, 2);
    }

    // Helper to extract integer field for assertions
    private int asInt(JsonValue v, String key) {
        if (v instanceof jdk.sandbox.java.util.json.JsonObject obj) {
            return (int) obj.members().get(key).toLong();
        }
        throw new IllegalArgumentException("Not an object");
    }
}
