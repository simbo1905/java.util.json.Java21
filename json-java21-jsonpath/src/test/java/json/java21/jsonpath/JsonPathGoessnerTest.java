package json.java21.jsonpath;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for JsonPath based on examples from [...](https://goessner.net/articles/JsonPath/)
/// This test class uses the sample JSON document from the article.
class JsonPathGoessnerTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathGoessnerTest.class.getName());

    /// Sample JSON from Goessner article
    private static final String STORE_JSON = """
        {
          "store": {
            "book": [
              {
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
              },
              {
                "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
              },
              {
                "category": "fiction",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
              },
              {
                "category": "fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "isbn": "0-395-19395-8",
                "price": 22.99
              }
            ],
            "bicycle": {
              "color": "red",
              "price": 19.95
            }
          }
        }
        """;

    private static JsonValue storeJson;

    @BeforeAll
    static void parseJson() {
        storeJson = Json.parse(STORE_JSON);
        LOG.info(() -> "Parsed store JSON for Goessner tests");
    }

    // ========== Basic path queries ==========

    @Test
    void testRootOnly() {
        LOG.info(() -> "TEST: testRootOnly - $ returns the root document");
        final var results = JsonPath.parse("$").query(storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEqualTo(storeJson);
    }

    @Test
    void testSingleProperty() {
        LOG.info(() -> "TEST: testSingleProperty - $.store returns the store object");
        final var results = JsonPath.parse("$.store").query(storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isInstanceOf(JsonObject.class);
    }

    @Test
    void testNestedProperty() {
        LOG.info(() -> "TEST: testNestedProperty - $.store.bicycle returns the bicycle object");
        final var results = JsonPath.parse("$.store.bicycle").query(storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isInstanceOf(JsonObject.class);
        final var bicycle = (JsonObject) results.getFirst();
        assertThat(bicycle.members().get("color")).isInstanceOf(JsonString.class);
        assertThat(bicycle.members().get("color").string()).isEqualTo("red");
    }

    // ========== Goessner Article Examples ==========

    @Test
    void testAuthorsOfAllBooks() {
        LOG.info(() -> "TEST: testAuthorsOfAllBooks - $.store.book[*].author");
        final var results = JsonPath.parse("$.store.book[*].author").query(storeJson);
        assertThat(results).hasSize(4);
        final var authors = results.stream()
            .map(JsonValue::string)
            .toList();
        assertThat(authors).containsExactly(
            "Nigel Rees",
            "Evelyn Waugh",
            "Herman Melville",
            "J. R. R. Tolkien"
        );
    }

    @Test
    void testAllBooks() {
        LOG.info(() -> "TEST: testAllBooks - $.store.book");
        final var results = JsonPath.parse("$.store.book").query(storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isInstanceOf(JsonArray.class);
        assertThat(((JsonArray) results.getFirst()).elements()).hasSize(4);
    }

    @Test
    void testAllAuthorsRecursive() {
        LOG.info(() -> "TEST: testAllAuthorsRecursive - $..author");
        final var results = JsonPath.parse("$..author").query(storeJson);
        assertThat(results).hasSize(4);
        final var authors = results.stream()
            .map(JsonValue::string)
            .toList();
        assertThat(authors).containsExactlyInAnyOrder(
            "Nigel Rees",
            "Evelyn Waugh",
            "Herman Melville",
            "J. R. R. Tolkien"
        );
    }

    @Test
    void testAllThingsInStore() {
        LOG.info(() -> "TEST: testAllThingsInStore - $.store.*");
        final var results = JsonPath.parse("$.store.*").query(storeJson);
        assertThat(results).hasSize(2); // book array and bicycle object
    }

    @Test
    void testAllPricesInStore() {
        LOG.info(() -> "TEST: testAllPricesInStore - $.store..price");
        final var results = JsonPath.parse("$.store..price").query(storeJson);
        assertThat(results).hasSize(5); // 4 book prices + 1 bicycle price
        final var prices = results.stream()
            .map(JsonValue::toDouble)
            .toList();
        assertThat(prices).containsExactlyInAnyOrder(8.95, 12.99, 8.99, 22.99, 19.95);
    }

    @Test
    void testThirdBook() {
        LOG.info(() -> "TEST: testThirdBook - $..book[2]");
        final var results = JsonPath.parse("$..book[2]").query(storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("title").string()).isEqualTo("Moby Dick");
    }

    @Test
    void testLastBookScriptExpression() {
        LOG.info(() -> "TEST: testLastBookScriptExpression - $..book[(@.length-1)]");
        final var results = JsonPath.parse("$..book[(@.length-1)]").query(storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("title").string()).isEqualTo("The Lord of the Rings");
    }

    @Test
    void testLastBookSlice() {
        LOG.info(() -> "TEST: testLastBookSlice - $..book[-1:]");
        final var results = JsonPath.parse("$..book[-1:]").query(storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("title").string()).isEqualTo("The Lord of the Rings");
    }

    @Test
    void testFirstTwoBooksUnion() {
        LOG.info(() -> "TEST: testFirstTwoBooksUnion - $..book[0,1]");
        final var results = JsonPath.parse("$..book[0,1]").query(storeJson);
        assertThat(results).hasSize(2);
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactly("Sayings of the Century", "Sword of Honour");
    }

    @Test
    void testFirstTwoBooksSlice() {
        LOG.info(() -> "TEST: testFirstTwoBooksSlice - $..book[:2]");
        final var results = JsonPath.parse("$..book[:2]").query(storeJson);
        assertThat(results).hasSize(2);
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactly("Sayings of the Century", "Sword of Honour");
    }

    @Test
    void testBooksWithIsbn() {
        LOG.info(() -> "TEST: testBooksWithIsbn - $..book[?(@.isbn)]");
        final var results = JsonPath.parse("$..book[?(@.isbn)]").query(storeJson);
        assertThat(results).hasSize(2);
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactlyInAnyOrder("Moby Dick", "The Lord of the Rings");
    }

    @Test
    void testBooksCheaperThan10() {
        LOG.info(() -> "TEST: testBooksCheaperThan10 - $..book[?(@.price<10)]");
        final var results = JsonPath.parse("$..book[?(@.price<10)]").query(storeJson);
        assertThat(results).hasSize(2);
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactlyInAnyOrder("Sayings of the Century", "Moby Dick");
    }

    @Test
    void testAllMembersRecursive() {
        LOG.info(() -> "TEST: testAllMembersRecursive - $..*");
        final var results = JsonPath.parse("$..*").query(storeJson);
        // This should return all nodes in the tree
        assertThat(results).isNotEmpty();
        LOG.fine(() -> "Found " + results.size() + " members recursively");
    }

    // ========== Additional edge cases ==========

    @Test
    void testArrayIndexFirst() {
        LOG.info(() -> "TEST: testArrayIndexFirst - $.store.book[0]");
        final var results = JsonPath.parse("$.store.book[0]").query(storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("author").string()).isEqualTo("Nigel Rees");
    }

    @Test
    void testArrayIndexNegative() {
        LOG.info(() -> "TEST: testArrayIndexNegative - $.store.book[-1]");
        final var results = JsonPath.parse("$.store.book[-1]").query(storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("author").string()).isEqualTo("J. R. R. Tolkien");
    }

    @Test
    void testBracketNotationProperty() {
        LOG.info(() -> "TEST: testBracketNotationProperty - $['store']['book'][0]");
        final var results = JsonPath.parse("$['store']['book'][0]").query(storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("author").string()).isEqualTo("Nigel Rees");
    }

    @Test
    void testFilterEquality() {
        LOG.info(() -> "TEST: testFilterEquality - $..book[?(@.category=='fiction')]");
        final var results = JsonPath.parse("$..book[?(@.category=='fiction')]").query(storeJson);
        assertThat(results).hasSize(3); // 3 fiction books
    }

    @Test
    void testPropertyNotFound() {
        LOG.info(() -> "TEST: testPropertyNotFound - $.nonexistent");
        final var results = JsonPath.parse("$.nonexistent").query(storeJson);
        assertThat(results).isEmpty();
    }

    @Test
    void testArrayIndexOutOfBounds() {
        LOG.info(() -> "TEST: testArrayIndexOutOfBounds - $.store.book[100]");
        final var results = JsonPath.parse("$.store.book[100]").query(storeJson);
        assertThat(results).isEmpty();
    }

    @Test
    void testSliceWithStep() {
        LOG.info(() -> "TEST: testSliceWithStep - $.store.book[0:4:2] (every other book)");
        final var results = JsonPath.parse("$.store.book[0:4:2]").query(storeJson);
        assertThat(results).hasSize(2); // books at index 0 and 2
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactly("Sayings of the Century", "Moby Dick");
    }

    @Test
    void testDeepNestedAccess() {
        LOG.info(() -> "TEST: testDeepNestedAccess - $.store.book[0].title");
        final var results = JsonPath.parse("$.store.book[0].title").query(storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().string()).isEqualTo("Sayings of the Century");
    }

    @Test
    void testRecursiveDescentOnArray() {
        LOG.info(() -> "TEST: testRecursiveDescentOnArray - $..book");
        final var results = JsonPath.parse("$..book").query(storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isInstanceOf(JsonArray.class);
    }

    @Test
    void testPropertyUnion() {
        LOG.info(() -> "TEST: testPropertyUnion - $.store['book','bicycle']");
        final var results = JsonPath.parse("$.store['book','bicycle']").query(storeJson);
        assertThat(results).hasSize(2);
    }

    @Test
    void testFilterGreaterThan() {
        LOG.info(() -> "TEST: testFilterGreaterThan - $..book[?(@.price>20)]");
        final var results = JsonPath.parse("$..book[?(@.price>20)]").query(storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("title").string()).isEqualTo("The Lord of the Rings");
    }

    @Test
    void testFilterLessOrEqual() {
        LOG.info(() -> "TEST: testFilterLessOrEqual - $..book[?(@.price<=8.99)]");
        final var results = JsonPath.parse("$..book[?(@.price<=8.99)]").query(storeJson);
        assertThat(results).hasSize(2);
    }

    @Test
    void testFilterCurrentNodeAlwaysTrue() {
        LOG.info(() -> "TEST: testFilterCurrentNodeAlwaysTrue - $.store.book[?(@)]");
        final var results = JsonPath.parse("$.store.book[?(@)]").query(storeJson);
        assertThat(results).hasSize(4);
    }

    @Test
    void testFilterLogicalNot() {
        LOG.info(() -> "TEST: testFilterLogicalNot - $.store.book[?(!@.isbn)]");
        final var results = JsonPath.parse("$.store.book[?(!@.isbn)]").query(storeJson);
        assertThat(results).hasSize(2);
    }

    @Test
    void testFilterLogicalAndOr() {
        LOG.info(() -> "TEST: testFilterLogicalAndOr - $.store.book[?(@.isbn && (@.price<10 || @.price>20))]");
        final var results = JsonPath.parse("$.store.book[?(@.isbn && (@.price<10 || @.price>20))]").query(storeJson);
        assertThat(results).hasSize(2);
    }

    @Test
    void testFilterLogicalAnd() {
        LOG.info(() -> "TEST: testFilterLogicalAnd - $.store.book[?(@.isbn && @.price>20)]");
        final var results = JsonPath.parse("$.store.book[?(@.isbn && @.price>20)]").query(storeJson);
        assertThat(results).hasSize(1);
        assertThat(((JsonObject) results.getFirst()).members().get("title").string()).isEqualTo("The Lord of the Rings");
    }

    // ========== Fluent API tests ==========

    @Test
    void testFluentApiParseAndSelect() {
        LOG.info(() -> "TEST: testFluentApiParseAndSelect - JsonPath.parse(...).query(...)");
        final var matches = JsonPath.parse("$.store.book").query(storeJson);
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst()).isInstanceOf(JsonArray.class);
        final var bookArray = (JsonArray) matches.getFirst();
        assertThat(bookArray.elements()).hasSize(4); // 4 books in the array
    }

    @Test
    void testStaticQueryWithCompiledPath() {
        LOG.info(() -> "TEST: testStaticQueryWithCompiledPath - JsonPath.query(JsonPath, JsonValue) does not re-parse");
        final var compiled = JsonPath.parse("$.store.book[*].author");
        final var results = JsonPath.query(compiled, storeJson);
        assertThat(results).hasSize(4);
        assertThat(results.stream().map(JsonValue::string).toList()).containsExactly(
            "Nigel Rees",
            "Evelyn Waugh",
            "Herman Melville",
            "J. R. R. Tolkien"
        );
    }

    @Test
    void testFluentApiReusable() {
        LOG.info(() -> "TEST: testFluentApiReusable - compiled path can be reused");
        final var compiledPath = JsonPath.parse("$..price");
        
        // Use on store doc
        final var storeResults = compiledPath.query(storeJson);
        assertThat(storeResults).hasSize(5); // 4 book prices + 1 bicycle price
        
        // Use on a different doc
        final var simpleDoc = Json.parse("""
            {"item": {"price": 99.99}}
            """);
        final var simpleResults = compiledPath.query(simpleDoc);
        assertThat(simpleResults).hasSize(1);
        assertThat(simpleResults.getFirst().toDouble()).isEqualTo(99.99);
    }

    @Test
    void testFluentApiExpressionAccessor() {
        LOG.info(() -> "TEST: testFluentApiExpressionAccessor - toString() reconstructs path");
        final var path = JsonPath.parse("$.store.book[*].author");
        // Reconstructed path might vary slightly (e.g. .* vs [*]), but should be valid and equivalent
        // Our implementation uses .* for Wildcard
        assertThat(path.toString()).isEqualTo("$.store.book.*.author");
    }
}
