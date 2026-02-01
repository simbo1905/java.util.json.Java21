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
                "ISBN": "0-553-21311-3",
                "price": 8.99
              },
              {
                "category": "fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "ISBN": "0-395-19395-8",
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
        final var results = JsonPath.query("$", storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEqualTo(storeJson);
    }

    @Test
    void testSingleProperty() {
        LOG.info(() -> "TEST: testSingleProperty - $.store returns the store object");
        final var results = JsonPath.query("$.store", storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isInstanceOf(JsonObject.class);
    }

    @Test
    void testNestedProperty() {
        LOG.info(() -> "TEST: testNestedProperty - $.store.bicycle returns the bicycle object");
        final var results = JsonPath.query("$.store.bicycle", storeJson);
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
        final var results = JsonPath.query("$.store.book[*].author", storeJson);
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
    void testAllAuthorsRecursive() {
        LOG.info(() -> "TEST: testAllAuthorsRecursive - $..author");
        final var results = JsonPath.query("$..author", storeJson);
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
        final var results = JsonPath.query("$.store.*", storeJson);
        assertThat(results).hasSize(2); // book array and bicycle object
    }

    @Test
    void testAllPricesInStore() {
        LOG.info(() -> "TEST: testAllPricesInStore - $.store..price");
        final var results = JsonPath.query("$.store..price", storeJson);
        assertThat(results).hasSize(5); // 4 book prices + 1 bicycle price
        final var prices = results.stream()
            .map(JsonValue::toDouble)
            .toList();
        assertThat(prices).containsExactlyInAnyOrder(8.95, 12.99, 8.99, 22.99, 19.95);
    }

    @Test
    void testThirdBook() {
        LOG.info(() -> "TEST: testThirdBook - $..book[2]");
        final var results = JsonPath.query("$..book[2]", storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("title").string()).isEqualTo("Moby Dick");
    }

    @Test
    void testLastBookScriptExpression() {
        LOG.info(() -> "TEST: testLastBookScriptExpression - $..book[(@.length-1)]");
        final var results = JsonPath.query("$..book[(@.length-1)]", storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("title").string()).isEqualTo("The Lord of the Rings");
    }

    @Test
    void testLastBookSlice() {
        LOG.info(() -> "TEST: testLastBookSlice - $..book[-1:]");
        final var results = JsonPath.query("$..book[-1:]", storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("title").string()).isEqualTo("The Lord of the Rings");
    }

    @Test
    void testFirstTwoBooksUnion() {
        LOG.info(() -> "TEST: testFirstTwoBooksUnion - $..book[0,1]");
        final var results = JsonPath.query("$..book[0,1]", storeJson);
        assertThat(results).hasSize(2);
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactly("Sayings of the Century", "Sword of Honour");
    }

    @Test
    void testFirstTwoBooksSlice() {
        LOG.info(() -> "TEST: testFirstTwoBooksSlice - $..book[:2]");
        final var results = JsonPath.query("$..book[:2]", storeJson);
        assertThat(results).hasSize(2);
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactly("Sayings of the Century", "Sword of Honour");
    }

    @Test
    void testBooksWithIsbn() {
        LOG.info(() -> "TEST: testBooksWithIsbn - $..book[?(@.isbn)]");
        final var results = JsonPath.query("$..book[?(@.isbn)]", storeJson);
        assertThat(results).hasSize(2);
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactlyInAnyOrder("Moby Dick", "The Lord of the Rings");
    }

    @Test
    void testBooksCheaperThan10() {
        LOG.info(() -> "TEST: testBooksCheaperThan10 - $..book[?(@.price<10)]");
        final var results = JsonPath.query("$..book[?(@.price<10)]", storeJson);
        assertThat(results).hasSize(2);
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactlyInAnyOrder("Sayings of the Century", "Moby Dick");
    }

    @Test
    void testAllMembersRecursive() {
        LOG.info(() -> "TEST: testAllMembersRecursive - $..*");
        final var results = JsonPath.query("$..*", storeJson);
        // This should return all nodes in the tree
        assertThat(results).isNotEmpty();
        LOG.fine(() -> "Found " + results.size() + " members recursively");
    }

    // ========== Additional edge cases ==========

    @Test
    void testArrayIndexFirst() {
        LOG.info(() -> "TEST: testArrayIndexFirst - $.store.book[0]");
        final var results = JsonPath.query("$.store.book[0]", storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("author").string()).isEqualTo("Nigel Rees");
    }

    @Test
    void testArrayIndexNegative() {
        LOG.info(() -> "TEST: testArrayIndexNegative - $.store.book[-1]");
        final var results = JsonPath.query("$.store.book[-1]", storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("author").string()).isEqualTo("J. R. R. Tolkien");
    }

    @Test
    void testBracketNotationProperty() {
        LOG.info(() -> "TEST: testBracketNotationProperty - $['store']['book'][0]");
        final var results = JsonPath.query("$['store']['book'][0]", storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("author").string()).isEqualTo("Nigel Rees");
    }

    @Test
    void testFilterEquality() {
        LOG.info(() -> "TEST: testFilterEquality - $..book[?(@.category=='fiction')]");
        final var results = JsonPath.query("$..book[?(@.category=='fiction')]", storeJson);
        assertThat(results).hasSize(3); // 3 fiction books
    }

    @Test
    void testPropertyNotFound() {
        LOG.info(() -> "TEST: testPropertyNotFound - $.nonexistent");
        final var results = JsonPath.query("$.nonexistent", storeJson);
        assertThat(results).isEmpty();
    }

    @Test
    void testArrayIndexOutOfBounds() {
        LOG.info(() -> "TEST: testArrayIndexOutOfBounds - $.store.book[100]");
        final var results = JsonPath.query("$.store.book[100]", storeJson);
        assertThat(results).isEmpty();
    }

    @Test
    void testSliceWithStep() {
        LOG.info(() -> "TEST: testSliceWithStep - $.store.book[0:4:2] (every other book)");
        final var results = JsonPath.query("$.store.book[0:4:2]", storeJson);
        assertThat(results).hasSize(2); // books at index 0 and 2
        final var titles = results.stream()
            .map(v -> v.members().get("title").string())
            .toList();
        assertThat(titles).containsExactly("Sayings of the Century", "Moby Dick");
    }

    @Test
    void testDeepNestedAccess() {
        LOG.info(() -> "TEST: testDeepNestedAccess - $.store.book[0].title");
        final var results = JsonPath.query("$.store.book[0].title", storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().string()).isEqualTo("Sayings of the Century");
    }

    @Test
    void testRecursiveDescentOnArray() {
        LOG.info(() -> "TEST: testRecursiveDescentOnArray - $..book");
        final var results = JsonPath.query("$..book", storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isInstanceOf(JsonArray.class);
    }

    @Test
    void testPropertyUnion() {
        LOG.info(() -> "TEST: testPropertyUnion - $.store['book','bicycle']");
        final var results = JsonPath.query("$.store['book','bicycle']", storeJson);
        assertThat(results).hasSize(2);
    }

    @Test
    void testFilterGreaterThan() {
        LOG.info(() -> "TEST: testFilterGreaterThan - $..book[?(@.price>20)]");
        final var results = JsonPath.query("$..book[?(@.price>20)]", storeJson);
        assertThat(results).hasSize(1);
        final var book = (JsonObject) results.getFirst();
        assertThat(book.members().get("title").string()).isEqualTo("The Lord of the Rings");
    }

    @Test
    void testFilterLessOrEqual() {
        LOG.info(() -> "TEST: testFilterLessOrEqual - $..book[?(@.price<=8.99)]");
        final var results = JsonPath.query("$..book[?(@.price<=8.99)]", storeJson);
        assertThat(results).hasSize(2);
    }

    // ========== Fluent API tests ==========

    @Test
    void testFluentApiParseAndSelect() {
        LOG.info(() -> "TEST: testFluentApiParseAndSelect - JsonPath.parse(...).select(...)");
        final var matches = JsonPath.parse("$.store.book").select(storeJson);
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst()).isInstanceOf(JsonArray.class);
        final var bookArray = (JsonArray) matches.getFirst();
        assertThat(bookArray.elements()).hasSize(4); // 4 books in the array
    }

    @Test
    void testFluentApiReusable() {
        LOG.info(() -> "TEST: testFluentApiReusable - compiled path can be reused");
        final var compiledPath = JsonPath.parse("$..price");
        
        // Use on store doc
        final var storeResults = compiledPath.select(storeJson);
        assertThat(storeResults).hasSize(5); // 4 book prices + 1 bicycle price
        
        // Use on a different doc
        final var simpleDoc = Json.parse("""
            {"item": {"price": 99.99}}
            """);
        final var simpleResults = compiledPath.select(simpleDoc);
        assertThat(simpleResults).hasSize(1);
        assertThat(simpleResults.getFirst().toDouble()).isEqualTo(99.99);
    }

    @Test
    void testFluentApiExpressionAccessor() {
        LOG.info(() -> "TEST: testFluentApiExpressionAccessor - expression() returns original path");
        final var path = JsonPath.parse("$.store.book[*].author");
        assertThat(path.expression()).isEqualTo("$.store.book[*].author");
    }
}
