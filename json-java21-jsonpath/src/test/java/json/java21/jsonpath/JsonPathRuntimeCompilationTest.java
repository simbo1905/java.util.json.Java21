package json.java21.jsonpath;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPathRuntimeCompilationTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathRuntimeCompilationTest.class.getName());

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

    @Test
    void testCompileIdempotent() {
        LOG.info(() -> "TEST: testCompileIdempotent");

        final var ast = JsonPath.parse("$.store.book[*].author");
        final var compiled = ast.compile();

        assertThat(compiled.compile()).isSameAs(compiled);
        assertThat(compiled.toString()).isEqualTo(ast.toString());

        final boolean canCompile = ToolProvider.getSystemJavaCompiler() != null;
        if (canCompile) {
            assertThat(compiled).isInstanceOf(JsonPathCompiled.class);
        } else {
            assertThat(compiled).isSameAs(ast);
        }
    }

    @Test
    void testCompiledMatchesAstForRepresentativeExpressions() {
        LOG.info(() -> "TEST: testCompiledMatchesAstForRepresentativeExpressions");

        final JsonValue doc = Json.parse(STORE_JSON);

        final List<String> expressions = List.of(
                "$",
                "$.store",
                "$.store.bicycle.color",
                "$.store.book[*].author",
                "$..price",
                "$..book[2].title",
                "$..book[-1:]",
                "$..book[0,1]",
                "$..book[:2]",
                "$..book[?(@.isbn)]",
                "$..book[?(@.price<10)].title",
                "$.store['book','bicycle']",
                "$..*",
                "$..book[(@.length-1)].title",
                "$.store.book[::-1].title"
        );

        for (final var expr : expressions) {
            final var ast = JsonPath.parse(expr);
            final var compiled = ast.compile();

            final var expected = ast.query(doc);
            final var actual = compiled.query(doc);

            assertThat(actual)
                    .as("compiled query results match AST for %s", expr)
                    .isEqualTo(expected);
        }
    }

    @Test
    void testGeneratedSourceEmitsLogicalOperators() {
        LOG.info(() -> "TEST: testGeneratedSourceEmitsLogicalOperators");

        final var ast = JsonPathParser.parse("$[?(@.a == true && (@.b == true || @.c == true))]");
        final var src = JsonPathCompiler.toJavaSourceForTests(ast);

        assertThat(src).contains("&&");
        assertThat(src).contains("||");
        assertThat(src).contains("JsonPathRuntime.compareComparable");
        assertThat(src).contains("JsonPathRuntime.resolvePropertyPath");
    }
}

