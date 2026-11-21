package json.java21.jsonpath;

import jdk.incubator.java.util.json.Json;
import jdk.incubator.java.util.json.JsonValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Parity tests for the interpreted and bytecode-generated JsonPath engines.
///
/// Every expression in the module's existing test corpus runs against a set of
/// documents through both engines; the compiled engine's results must be
/// element-for-element identical to the interpreted engine's results.
/// This is the contract documented in the module README ("Two Engines").
class JsonPathCompilerParityTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathCompilerParityTest.class.getName());

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

    private static final String NUMBERS_JSON = """
        {"numbers": [3, 1, 4, 1, 5, 9, 2, 6], "nested": {"numbers": [7]}}
        """;

    private static final String EMPTY_JSON = """
        {"empty": [], "nullobject": null, "text": "hello"}
        """;

    private static List<JsonValue> documents() {
        return List.of(
                Json.parse(STORE_JSON),
                Json.parse(NUMBERS_JSON),
                Json.parse(EMPTY_JSON),
                Json.parse("42"),
                Json.parse("[1, 2, 3]"),
                Json.parse("{}"));
    }

    /// The full expression corpus reused from the existing JsonPath test catalogue.
    static Stream<String> expressions() {
        return Stream.of(
                // root and property access
                "$",
                "$.store",
                "$.store.bicycle",
                "$.store.bicycle.color",
                "$['store']['book']",
                "$.nonexistent",
                "$['special-key']",
                // array indices
                "$.store.book[0]",
                "$.store.book[3]",
                "$.store.book[-1]",
                "$.store.book[100]",
                "$.store.book[0].title",
                "$.numbers[2]",
                "$.numbers[-9]",
                // slices
                "$.store.book[:2]",
                "$.store.book[0:4:2]",
                "$.store.book[::-1]",
                "$.store.book[-1:]",
                "$.store.book[1:]",
                "$.store.book[::2]",
                "$.store.book[10:20]",
                "$.numbers[:3]",
                "$.numbers[::-2]",
                // wildcards
                "$.store.*",
                "$.store.book[*]",
                "$.store.book[*].author",
                "$.numbers[*]",
                "$[*]",
                "$.*",
                // filters
                "$.store.book[?(@.isbn)]",
                "$.store.book[?(!@.isbn)]",
                "$.store.book[?(@.price<10)]",
                "$.store.book[?(@.price > 20)]",
                "$.store.book[?(@.category=='fiction')]",
                "$.store.book[?(@.category!=\"reference\")]",
                "$.store.book[?(@.price<=12.99)]",
                "$.store.book[?(@.price>=8.95)]",
                "$.store.book[?(@.isbn && @.price>20)]",
                "$.store.book[?(@.price<10 || @.price>20)]",
                "$.store.book[?(@.isbn && (@.price<10 || @.price>20))]",
                "$.store.book[?(@)]",
                "$..book[?(@.price>20)]",
                "$..book[?(@.price<10)]",
                "$.store.book[?(@.title=='Moby Dick')].price",
                // unions
                "$.store.book[0,1]",
                "$.store.book[3,0]",
                "$.store['book','bicycle']",
                "$.store['color','bicycle']",
                "$..book[0,1]",
                // recursive descent
                "$..author",
                "$..price",
                "$..*",
                "$..book[2]",
                "$.store..price",
                "$..book[0,1].title",
                "$..book[-1]",
                "$..book[(@.length-1)]",
                "$..book[:2].title",
                "$.store.book[(@.length-1)].title",
                // unsupported/edge script and odd targets: results must still match
                "$.store.book[(@.length)]",
                // scalars and empty documents
                "$.text",
                "$.empty",
                "$.empty[*]",
                "$.numbers.nested");
    }

    @ParameterizedTest
    @MethodSource("expressions")
    void interpretedAndGeneratedEnginesProduceIdenticalResults(String pathExpression) {
        LOG.info(() -> "TEST: interpretedAndGeneratedEnginesProduceIdenticalResults - " + pathExpression);
        for (final var document : documents()) {
            final var interpreted = JsonPath.parse(pathExpression);
            final var compiled = JsonPathCodegen.compile(interpreted);
            assertThat(compiled).isInstanceOf(JsonPathCompiled.class);

            final var interpretedResults = interpreted.query(document);
            final var compiledResults = compiled.query(document);

            LOG.fine(() -> "Path: " + pathExpression
                    + " | interpreted results: " + interpretedResults
                    + " | compiled results: " + compiledResults);

            assertThat(compiledResults)
                    .as("Compiled results for '%s' should match interpreted results", pathExpression)
                    .containsExactlyElementsOf(interpretedResults);
        }
    }
}
