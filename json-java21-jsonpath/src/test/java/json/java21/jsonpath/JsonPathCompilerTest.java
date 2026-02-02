package json.java21.jsonpath;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for JsonPath runtime compilation.
/// Verifies that compiled paths produce identical results to interpreted paths.
class JsonPathCompilerTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathCompilerTest.class.getName());

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
        LOG.info(() -> "Parsed store JSON for compiler tests");
    }

    // Basic compilation tests

    @Test
    void testCompileReturnsCompiledInstance() {
        LOG.info(() -> "TEST: testCompileReturnsCompiledInstance");
        final var interpreted = JsonPath.parse("$.store");
        assertThat(interpreted.isInterpreted()).isTrue();
        assertThat(interpreted.isCompiled()).isFalse();

        final var compiled = JsonPath.compile(interpreted);
        assertThat(compiled.isCompiled()).isTrue();
        assertThat(compiled.isInterpreted()).isFalse();
    }

    @Test
    void testCompileIdempotent() {
        LOG.info(() -> "TEST: testCompileIdempotent - compiling a compiled path returns same instance");
        final var interpreted = JsonPath.parse("$.store");
        final var compiled = JsonPath.compile(interpreted);
        final var compiledAgain = JsonPath.compile(compiled);
        assertThat(compiledAgain).isSameAs(compiled);
    }

    // Simple property access tests

    @Test
    void testCompiledRootOnly() {
        LOG.info(() -> "TEST: testCompiledRootOnly - $ returns the root document");
        assertCompiledMatchesInterpreted("$", storeJson);
    }

    @Test
    void testCompiledSingleProperty() {
        LOG.info(() -> "TEST: testCompiledSingleProperty - $.store");
        assertCompiledMatchesInterpreted("$.store", storeJson);
    }

    @Test
    void testCompiledNestedProperty() {
        LOG.info(() -> "TEST: testCompiledNestedProperty - $.store.bicycle");
        assertCompiledMatchesInterpreted("$.store.bicycle", storeJson);
    }

    @Test
    void testCompiledDeepProperty() {
        LOG.info(() -> "TEST: testCompiledDeepProperty - $.store.bicycle.color");
        assertCompiledMatchesInterpreted("$.store.bicycle.color", storeJson);
    }

    @Test
    void testCompiledBracketNotation() {
        LOG.info(() -> "TEST: testCompiledBracketNotation - $['store']['book']");
        assertCompiledMatchesInterpreted("$['store']['book']", storeJson);
    }

    @Test
    void testCompiledPropertyNotFound() {
        LOG.info(() -> "TEST: testCompiledPropertyNotFound - $.nonexistent");
        assertCompiledMatchesInterpreted("$.nonexistent", storeJson);
    }

    // Array index tests

    @Test
    void testCompiledArrayIndexFirst() {
        LOG.info(() -> "TEST: testCompiledArrayIndexFirst - $.store.book[0]");
        assertCompiledMatchesInterpreted("$.store.book[0]", storeJson);
    }

    @Test
    void testCompiledArrayIndexLast() {
        LOG.info(() -> "TEST: testCompiledArrayIndexLast - $.store.book[-1]");
        assertCompiledMatchesInterpreted("$.store.book[-1]", storeJson);
    }

    @Test
    void testCompiledArrayIndexOutOfBounds() {
        LOG.info(() -> "TEST: testCompiledArrayIndexOutOfBounds - $.store.book[100]");
        assertCompiledMatchesInterpreted("$.store.book[100]", storeJson);
    }

    @Test
    void testCompiledArrayIndexChained() {
        LOG.info(() -> "TEST: testCompiledArrayIndexChained - $.store.book[0].title");
        assertCompiledMatchesInterpreted("$.store.book[0].title", storeJson);
    }

    // Array slice tests

    @Test
    void testCompiledArraySliceBasic() {
        LOG.info(() -> "TEST: testCompiledArraySliceBasic - $.store.book[:2]");
        assertCompiledMatchesInterpreted("$.store.book[:2]", storeJson);
    }

    @Test
    void testCompiledArraySliceWithStep() {
        LOG.info(() -> "TEST: testCompiledArraySliceWithStep - $.store.book[0:4:2]");
        assertCompiledMatchesInterpreted("$.store.book[0:4:2]", storeJson);
    }

    @Test
    void testCompiledArraySliceReverse() {
        LOG.info(() -> "TEST: testCompiledArraySliceReverse - $.store.book[::-1]");
        assertCompiledMatchesInterpreted("$.store.book[::-1]", storeJson);
    }

    @Test
    void testCompiledArraySliceNegativeStart() {
        LOG.info(() -> "TEST: testCompiledArraySliceNegativeStart - $.store.book[-1:]");
        assertCompiledMatchesInterpreted("$.store.book[-1:]", storeJson);
    }

    // Wildcard tests

    @Test
    void testCompiledWildcardObject() {
        LOG.info(() -> "TEST: testCompiledWildcardObject - $.store.*");
        assertCompiledMatchesInterpreted("$.store.*", storeJson);
    }

    @Test
    void testCompiledWildcardArray() {
        LOG.info(() -> "TEST: testCompiledWildcardArray - $.store.book[*]");
        assertCompiledMatchesInterpreted("$.store.book[*]", storeJson);
    }

    @Test
    void testCompiledWildcardChained() {
        LOG.info(() -> "TEST: testCompiledWildcardChained - $.store.book[*].author");
        assertCompiledMatchesInterpreted("$.store.book[*].author", storeJson);
    }

    // Filter tests

    @Test
    void testCompiledFilterExists() {
        LOG.info(() -> "TEST: testCompiledFilterExists - $.store.book[?(@.isbn)]");
        assertCompiledMatchesInterpreted("$.store.book[?(@.isbn)]", storeJson);
    }

    @Test
    void testCompiledFilterNotExists() {
        LOG.info(() -> "TEST: testCompiledFilterNotExists - $.store.book[?(!@.isbn)]");
        assertCompiledMatchesInterpreted("$.store.book[?(!@.isbn)]", storeJson);
    }

    @Test
    void testCompiledFilterCompareLessThan() {
        LOG.info(() -> "TEST: testCompiledFilterCompareLessThan - $.store.book[?(@.price<10)]");
        assertCompiledMatchesInterpreted("$.store.book[?(@.price<10)]", storeJson);
    }

    @Test
    void testCompiledFilterCompareGreaterThan() {
        LOG.info(() -> "TEST: testCompiledFilterCompareGreaterThan - $.store.book[?(@.price>20)]");
        assertCompiledMatchesInterpreted("$.store.book[?(@.price>20)]", storeJson);
    }

    @Test
    void testCompiledFilterCompareEquals() {
        LOG.info(() -> "TEST: testCompiledFilterCompareEquals - $.store.book[?(@.category=='fiction')]");
        assertCompiledMatchesInterpreted("$.store.book[?(@.category=='fiction')]", storeJson);
    }

    @Test
    void testCompiledFilterLogicalAnd() {
        LOG.info(() -> "TEST: testCompiledFilterLogicalAnd - $.store.book[?(@.isbn && @.price>20)]");
        assertCompiledMatchesInterpreted("$.store.book[?(@.isbn && @.price>20)]", storeJson);
    }

    @Test
    void testCompiledFilterLogicalOr() {
        LOG.info(() -> "TEST: testCompiledFilterLogicalOr - $.store.book[?(@.price<10 || @.price>20)]");
        assertCompiledMatchesInterpreted("$.store.book[?(@.price<10 || @.price>20)]", storeJson);
    }

    @Test
    void testCompiledFilterComplex() {
        LOG.info(() -> "TEST: testCompiledFilterComplex - $.store.book[?(@.isbn && (@.price<10 || @.price>20))]");
        assertCompiledMatchesInterpreted("$.store.book[?(@.isbn && (@.price<10 || @.price>20))]", storeJson);
    }

    // Union tests

    @Test
    void testCompiledUnionIndices() {
        LOG.info(() -> "TEST: testCompiledUnionIndices - $.store.book[0,1]");
        assertCompiledMatchesInterpreted("$.store.book[0,1]", storeJson);
    }

    @Test
    void testCompiledUnionProperties() {
        LOG.info(() -> "TEST: testCompiledUnionProperties - $.store['book','bicycle']");
        assertCompiledMatchesInterpreted("$.store['book','bicycle']", storeJson);
    }

    // Recursive descent tests

    @Test
    void testCompiledRecursiveDescentProperty() {
        LOG.info(() -> "TEST: testCompiledRecursiveDescentProperty - $..author");
        assertCompiledMatchesInterpreted("$..author", storeJson);
    }

    @Test
    void testCompiledRecursiveDescentPrice() {
        LOG.info(() -> "TEST: testCompiledRecursiveDescentPrice - $..price");
        assertCompiledMatchesInterpreted("$..price", storeJson);
    }

    @Test
    void testCompiledRecursiveDescentWildcard() {
        LOG.info(() -> "TEST: testCompiledRecursiveDescentWildcard - $..*");
        assertCompiledMatchesInterpreted("$..*", storeJson);
    }

    @Test
    void testCompiledRecursiveDescentWithIndex() {
        LOG.info(() -> "TEST: testCompiledRecursiveDescentWithIndex - $..book[2]");
        assertCompiledMatchesInterpreted("$..book[2]", storeJson);
    }

    // Script expression tests

    @Test
    void testCompiledScriptExpressionLastElement() {
        LOG.info(() -> "TEST: testCompiledScriptExpressionLastElement - $.store.book[(@.length-1)]");
        assertCompiledMatchesInterpreted("$.store.book[(@.length-1)]", storeJson);
    }

    // Code generation verification tests

    @Test
    void testCodeGenerationForSimpleProperty() {
        LOG.info(() -> "TEST: testCodeGenerationForSimpleProperty - verify generated code compiles");
        final var path = JsonPath.parse("$.store.bicycle.color");
        final var compiled = JsonPath.compile(path);
        assertThat(compiled).isInstanceOf(JsonPathCompiled.class);

        // Verify it works
        final var results = compiled.query(storeJson);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().string()).isEqualTo("red");
    }

    @Test
    void testCompiledToStringReconstructsPath() {
        LOG.info(() -> "TEST: testCompiledToStringReconstructsPath");
        final var path = JsonPath.parse("$.store.book[*].author");
        final var compiled = JsonPath.compile(path);

        // toString should reconstruct the path
        assertThat(compiled.toString()).isEqualTo(path.toString());
    }

    @Test
    void testCompiledAstAccessible() {
        LOG.info(() -> "TEST: testCompiledAstAccessible");
        final var path = JsonPath.parse("$.store.book");
        final var compiled = JsonPath.compile(path);

        // AST should be accessible from compiled path
        assertThat(compiled.ast()).isNotNull();
        assertThat(compiled.ast().segments()).hasSize(2);
    }

    // Edge case tests

    @Test
    void testCompiledEmptyPath() {
        LOG.info(() -> "TEST: testCompiledEmptyPath - $ with no segments");
        final var json = Json.parse("42");
        assertCompiledMatchesInterpreted("$", json);
    }

    @Test
    void testCompiledOnDifferentDocuments() {
        LOG.info(() -> "TEST: testCompiledOnDifferentDocuments - reuse compiled path");
        final var compiled = JsonPath.compile(JsonPath.parse("$.name"));

        final var doc1 = Json.parse("{\"name\": \"Alice\"}");
        final var doc2 = Json.parse("{\"name\": \"Bob\"}");

        final var results1 = compiled.query(doc1);
        final var results2 = compiled.query(doc2);

        assertThat(results1.getFirst().string()).isEqualTo("Alice");
        assertThat(results2.getFirst().string()).isEqualTo("Bob");
    }

    @Test
    void testCompiledWithSpecialCharactersInPropertyName() {
        LOG.info(() -> "TEST: testCompiledWithSpecialCharactersInPropertyName");
        final var json = Json.parse("{\"special-key\": 123}");
        assertCompiledMatchesInterpreted("$['special-key']", json);
    }

    /// Helper method to verify compiled and interpreted paths produce identical results.
    private void assertCompiledMatchesInterpreted(String pathExpr, JsonValue json) {
        final var interpreted = JsonPath.parse(pathExpr);
        final var compiled = JsonPath.compile(interpreted);

        final var interpretedResults = interpreted.query(json);
        final var compiledResults = compiled.query(json);

        LOG.fine(() -> "Path: " + pathExpr);
        LOG.fine(() -> "Interpreted results: " + interpretedResults.size());
        LOG.fine(() -> "Compiled results: " + compiledResults.size());

        assertThat(compiledResults)
                .as("Compiled results for '%s' should match interpreted results", pathExpr)
                .containsExactlyElementsOf(interpretedResults);
    }
}
