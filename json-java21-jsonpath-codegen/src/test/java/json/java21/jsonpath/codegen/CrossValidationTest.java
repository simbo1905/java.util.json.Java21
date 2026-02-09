package json.java21.jsonpath.codegen;

import jdk.sandbox.java.util.json.*;
import json.java21.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/// Cross-validates the codegen JsonPath implementation against the interpreter.
///
/// For each expression + document pair, both the interpreter and codegen are
/// run, and the results must match exactly.
class CrossValidationTest extends CodegenTestBase {

    private static final Logger LOG = Logger.getLogger(CrossValidationTest.class.getName());

    static final String STORE_JSON = """
        {
          "store": {
            "book": [
              {"category": "reference", "author": "Nigel Rees", "title": "Sayings of the Century", "price": 8.95},
              {"category": "fiction", "author": "Evelyn Waugh", "title": "Sword of Honour", "price": 12.99},
              {"category": "fiction", "author": "Herman Melville", "title": "Moby Dick", "isbn": "0-553-21311-3", "price": 8.99},
              {"category": "fiction", "author": "J. R. R. Tolkien", "title": "The Lord of the Rings", "isbn": "0-395-19395-8", "price": 22.99}
            ],
            "bicycle": {"color": "red", "price": 19.95}
          }
        }
        """;

    static Stream<Arguments> expressions() {
        return Stream.of(
            // Property access
            Arguments.of("$.store.bicycle.color", STORE_JSON),
            Arguments.of("$.store.bicycle.price", STORE_JSON),

            // Array index
            Arguments.of("$.store.book[0]", STORE_JSON),
            Arguments.of("$.store.book[1]", STORE_JSON),
            Arguments.of("$.store.book[-1]", STORE_JSON),
            Arguments.of("$.store.book[3]", STORE_JSON),

            // Wildcard
            Arguments.of("$.store.book[*].title", STORE_JSON),
            Arguments.of("$.store.book[*].author", STORE_JSON),
            Arguments.of("$.store.*", STORE_JSON),

            // Recursive descent
            Arguments.of("$..price", STORE_JSON),
            Arguments.of("$..author", STORE_JSON),
            Arguments.of("$..title", STORE_JSON),

            // Array slice
            Arguments.of("$.store.book[:2]", STORE_JSON),
            Arguments.of("$.store.book[1:3]", STORE_JSON),
            Arguments.of("$.store.book[::2]", STORE_JSON),
            Arguments.of("$.store.book[::-1]", STORE_JSON),

            // Filter with exists
            Arguments.of("$.store.book[?(@.isbn)]", STORE_JSON),
            Arguments.of("$.store.book[?(@.isbn)].title", STORE_JSON),

            // Filter with comparison
            Arguments.of("$.store.book[?(@.price < 10)]", STORE_JSON),
            Arguments.of("$.store.book[?(@.price < 10)].title", STORE_JSON),
            Arguments.of("$.store.book[?(@.price > 20)]", STORE_JSON),

            // Filter with logic
            Arguments.of("$.store.book[?(@.isbn && @.price < 10)]", STORE_JSON),

            // Union
            Arguments.of("$.store.book[0,1]", STORE_JSON),

            // Script
            Arguments.of("$.store.book[(@.length-1)]", STORE_JSON),

            // Edge cases
            Arguments.of("$.store.book[99]", STORE_JSON),  // out of bounds
            Arguments.of("$.nonexistent", STORE_JSON),      // missing key
            Arguments.of("$", STORE_JSON)                   // root only
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expressions")
    void codegenMatchesInterpreter(String expression, String json) {
        LOG.info(() -> "TEST: codegenMatchesInterpreter - " + expression);

        final var doc = Json.parse(json);

        // Interpreter result
        final var interpreted = JsonPath.parse(expression).query(doc);

        // Codegen result
        final var compiled = JsonPathCodegen.compile(expression);
        final var generated = compiled.query(doc);

        LOG.fine(() -> "Interpreter: " + interpreted.size() + " results, Codegen: " + generated.size() + " results");

        assertThat(generated)
            .as("codegen results for '%s' must match interpreter", expression)
            .isEqualTo(interpreted);
    }

    @Test
    void toStringReturnsOriginalExpression() {
        LOG.info(() -> "TEST: toStringReturnsOriginalExpression");

        final var expression = "$.store.book[*].title";
        final var compiled = JsonPathCodegen.compile(expression);

        assertThat(compiled.toString()).isEqualTo(expression);
    }

    @Test
    void compileWithStatsReportsClassfileSize() {
        LOG.info(() -> "TEST: compileWithStatsReportsClassfileSize");

        final var result = JsonPathCodegen.compileWithStats("$.store.book[0].title");

        assertThat(result.classfileBytes()).isGreaterThan(0);
        assertThat(result.query()).isNotNull();
        LOG.info(() -> "Classfile size: " + result.classfileBytes() + " bytes");
    }
}
