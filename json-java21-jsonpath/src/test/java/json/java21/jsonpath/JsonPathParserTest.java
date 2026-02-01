package json.java21.jsonpath;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Unit tests for JsonPathParser - tests parsing of JsonPath strings to AST
/// Based on examples from [...](https://goessner.net/articles/JsonPath/)
class JsonPathParserTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathParserTest.class.getName());

    // ========== Basic path parsing ==========

    @Test
    void testParseRootOnly() {
        LOG.info(() -> "TEST: testParseRootOnly - parse $");
        final var ast = JsonPathParser.parse("$");
        assertThat(ast).isInstanceOf(JsonPathAst.Root.class);
        assertThat(ast.segments()).isEmpty();
    }

    @Test
    void testParseSingleProperty() {
        LOG.info(() -> "TEST: testParseSingleProperty - parse $.store");
        final var ast = JsonPathParser.parse("$.store");
        assertThat(ast.segments()).hasSize(1);
        assertThat(ast.segments().getFirst()).isInstanceOf(JsonPathAst.PropertyAccess.class);
        final var prop = (JsonPathAst.PropertyAccess) ast.segments().getFirst();
        assertThat(prop.name()).isEqualTo("store");
    }

    @Test
    void testParseNestedProperties() {
        LOG.info(() -> "TEST: testParseNestedProperties - parse $.store.book");
        final var ast = JsonPathParser.parse("$.store.book");
        assertThat(ast.segments()).hasSize(2);
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(0)).name()).isEqualTo("store");
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(1)).name()).isEqualTo("book");
    }

    @Test
    void testParseBracketNotation() {
        LOG.info(() -> "TEST: testParseBracketNotation - parse $['store']");
        final var ast = JsonPathParser.parse("$['store']");
        assertThat(ast.segments()).hasSize(1);
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().getFirst()).name()).isEqualTo("store");
    }

    @Test
    void testParseBracketNotationWithDoubleQuotes() {
        LOG.info(() -> "TEST: testParseBracketNotationWithDoubleQuotes - parse $[\"store\"]");
        final var ast = JsonPathParser.parse("$[\"store\"]");
        assertThat(ast.segments()).hasSize(1);
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().getFirst()).name()).isEqualTo("store");
    }

    // ========== Array index parsing ==========

    @Test
    void testParseArrayIndex() {
        LOG.info(() -> "TEST: testParseArrayIndex - parse $.store.book[0]");
        final var ast = JsonPathParser.parse("$.store.book[0]");
        assertThat(ast.segments()).hasSize(3);
        assertThat(ast.segments().get(2)).isInstanceOf(JsonPathAst.ArrayIndex.class);
        final var index = (JsonPathAst.ArrayIndex) ast.segments().get(2);
        assertThat(index.index()).isEqualTo(0);
    }

    @Test
    void testParseNegativeArrayIndex() {
        LOG.info(() -> "TEST: testParseNegativeArrayIndex - parse $..book[-1]");
        final var ast = JsonPathParser.parse("$..book[-1]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.ArrayIndex.class);
        final var index = (JsonPathAst.ArrayIndex) ast.segments().get(1);
        assertThat(index.index()).isEqualTo(-1);
    }

    @Test
    void testParseThirdBook() {
        LOG.info(() -> "TEST: testParseThirdBook - parse $..book[2] (third book)");
        final var ast = JsonPathParser.parse("$..book[2]");
        assertThat(ast.segments()).hasSize(2);
        // First segment is recursive descent for book
        assertThat(ast.segments().get(0)).isInstanceOf(JsonPathAst.RecursiveDescent.class);
        // Second segment is index 2
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.ArrayIndex.class);
        assertThat(((JsonPathAst.ArrayIndex) ast.segments().get(1)).index()).isEqualTo(2);
    }

    // ========== Wildcard parsing ==========

    @Test
    void testParseWildcard() {
        LOG.info(() -> "TEST: testParseWildcard - parse $.store.*");
        final var ast = JsonPathParser.parse("$.store.*");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Wildcard.class);
    }

    @Test
    void testParseWildcardInBrackets() {
        LOG.info(() -> "TEST: testParseWildcardInBrackets - parse $.store.book[*]");
        final var ast = JsonPathParser.parse("$.store.book[*]");
        assertThat(ast.segments()).hasSize(3);
        assertThat(ast.segments().get(2)).isInstanceOf(JsonPathAst.Wildcard.class);
    }

    @Test
    void testParseAllAuthors() {
        LOG.info(() -> "TEST: testParseAllAuthors - parse $.store.book[*].author");
        final var ast = JsonPathParser.parse("$.store.book[*].author");
        assertThat(ast.segments()).hasSize(4);
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(0)).name()).isEqualTo("store");
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(1)).name()).isEqualTo("book");
        assertThat(ast.segments().get(2)).isInstanceOf(JsonPathAst.Wildcard.class);
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(3)).name()).isEqualTo("author");
    }

    // ========== Recursive descent parsing ==========

    @Test
    void testParseRecursiveDescent() {
        LOG.info(() -> "TEST: testParseRecursiveDescent - parse $..author");
        final var ast = JsonPathParser.parse("$..author");
        assertThat(ast.segments()).hasSize(1);
        assertThat(ast.segments().getFirst()).isInstanceOf(JsonPathAst.RecursiveDescent.class);
        final var descent = (JsonPathAst.RecursiveDescent) ast.segments().getFirst();
        assertThat(descent.target()).isInstanceOf(JsonPathAst.PropertyAccess.class);
        assertThat(((JsonPathAst.PropertyAccess) descent.target()).name()).isEqualTo("author");
    }

    @Test
    void testParseRecursiveDescentWithWildcard() {
        LOG.info(() -> "TEST: testParseRecursiveDescentWithWildcard - parse $..*");
        final var ast = JsonPathParser.parse("$..*");
        assertThat(ast.segments()).hasSize(1);
        assertThat(ast.segments().getFirst()).isInstanceOf(JsonPathAst.RecursiveDescent.class);
        final var descent = (JsonPathAst.RecursiveDescent) ast.segments().getFirst();
        assertThat(descent.target()).isInstanceOf(JsonPathAst.Wildcard.class);
    }

    @Test
    void testParseStorePrice() {
        LOG.info(() -> "TEST: testParseStorePrice - parse $.store..price");
        final var ast = JsonPathParser.parse("$.store..price");
        assertThat(ast.segments()).hasSize(2);
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(0)).name()).isEqualTo("store");
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.RecursiveDescent.class);
        final var descent = (JsonPathAst.RecursiveDescent) ast.segments().get(1);
        assertThat(((JsonPathAst.PropertyAccess) descent.target()).name()).isEqualTo("price");
    }

    // ========== Slice parsing ==========

    @Test
    void testParseSlice() {
        LOG.info(() -> "TEST: testParseSlice - parse $..book[:2] (first two books)");
        final var ast = JsonPathParser.parse("$..book[:2]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.ArraySlice.class);
        final var slice = (JsonPathAst.ArraySlice) ast.segments().get(1);
        assertThat(slice.start()).isNull();
        assertThat(slice.end()).isEqualTo(2);
        assertThat(slice.step()).isNull();
    }

    @Test
    void testParseSliceFromEnd() {
        LOG.info(() -> "TEST: testParseSliceFromEnd - parse $..book[-1:] (last book)");
        final var ast = JsonPathParser.parse("$..book[-1:]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.ArraySlice.class);
        final var slice = (JsonPathAst.ArraySlice) ast.segments().get(1);
        assertThat(slice.start()).isEqualTo(-1);
        assertThat(slice.end()).isNull();
        assertThat(slice.step()).isNull();
    }

    @Test
    void testParseSliceWithStep() {
        LOG.info(() -> "TEST: testParseSliceWithStep - parse $.book[0:10:2] (every other book)");
        final var ast = JsonPathParser.parse("$.book[0:10:2]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.ArraySlice.class);
        final var slice = (JsonPathAst.ArraySlice) ast.segments().get(1);
        assertThat(slice.start()).isEqualTo(0);
        assertThat(slice.end()).isEqualTo(10);
        assertThat(slice.step()).isEqualTo(2);
    }

    // ========== Union parsing ==========

    @Test
    void testParseUnionIndices() {
        LOG.info(() -> "TEST: testParseUnionIndices - parse $..book[0,1] (first two books)");
        final var ast = JsonPathParser.parse("$..book[0,1]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Union.class);
        final var union = (JsonPathAst.Union) ast.segments().get(1);
        assertThat(union.selectors()).hasSize(2);
        assertThat(((JsonPathAst.ArrayIndex) union.selectors().get(0)).index()).isEqualTo(0);
        assertThat(((JsonPathAst.ArrayIndex) union.selectors().get(1)).index()).isEqualTo(1);
    }

    @Test
    void testParseUnionProperties() {
        LOG.info(() -> "TEST: testParseUnionProperties - parse $.store['book','bicycle']");
        final var ast = JsonPathParser.parse("$.store['book','bicycle']");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Union.class);
        final var union = (JsonPathAst.Union) ast.segments().get(1);
        assertThat(union.selectors()).hasSize(2);
        assertThat(((JsonPathAst.PropertyAccess) union.selectors().get(0)).name()).isEqualTo("book");
        assertThat(((JsonPathAst.PropertyAccess) union.selectors().get(1)).name()).isEqualTo("bicycle");
    }

    // ========== Filter parsing ==========

    @Test
    void testParseFilterExists() {
        LOG.info(() -> "TEST: testParseFilterExists - parse $..book[?(@.isbn)] (books with ISBN)");
        final var ast = JsonPathParser.parse("$..book[?(@.isbn)]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Filter.class);
        final var filter = (JsonPathAst.Filter) ast.segments().get(1);
        assertThat(filter.expression()).isInstanceOf(JsonPathAst.ExistsFilter.class);
    }

    @Test
    void testParseFilterComparison() {
        LOG.info(() -> "TEST: testParseFilterComparison - parse $..book[?(@.price<10)] (books cheaper than 10)");
        final var ast = JsonPathParser.parse("$..book[?(@.price<10)]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Filter.class);
        final var filter = (JsonPathAst.Filter) ast.segments().get(1);
        assertThat(filter.expression()).isInstanceOf(JsonPathAst.ComparisonFilter.class);
        final var comparison = (JsonPathAst.ComparisonFilter) filter.expression();
        assertThat(comparison.op()).isEqualTo(JsonPathAst.ComparisonOp.LT);
    }

    @Test
    void testParseFilterComparisonWithEquals() {
        LOG.info(() -> "TEST: testParseFilterComparisonWithEquals - parse $..book[?(@.category=='fiction')]");
        final var ast = JsonPathParser.parse("$..book[?(@.category=='fiction')]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Filter.class);
        final var filter = (JsonPathAst.Filter) ast.segments().get(1);
        assertThat(filter.expression()).isInstanceOf(JsonPathAst.ComparisonFilter.class);
        final var comparison = (JsonPathAst.ComparisonFilter) filter.expression();
        assertThat(comparison.op()).isEqualTo(JsonPathAst.ComparisonOp.EQ);
    }

    @Test
    void testParseFilterCurrentNode() {
        LOG.info(() -> "TEST: testParseFilterCurrentNode - parse $..book[?(@)]");
        final var ast = JsonPathParser.parse("$..book[?(@)]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Filter.class);
        final var filter = (JsonPathAst.Filter) ast.segments().get(1);
        assertThat(filter.expression()).isInstanceOf(JsonPathAst.CurrentNode.class);
    }

    @Test
    void testParseFilterLogicalNot() {
        LOG.info(() -> "TEST: testParseFilterLogicalNot - parse $..book[?(!@.isbn)]");
        final var ast = JsonPathParser.parse("$..book[?(!@.isbn)]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Filter.class);
        final var filter = (JsonPathAst.Filter) ast.segments().get(1);
        assertThat(filter.expression()).isInstanceOf(JsonPathAst.LogicalFilter.class);
        final var logical = (JsonPathAst.LogicalFilter) filter.expression();
        assertThat(logical.op()).isEqualTo(JsonPathAst.LogicalOp.NOT);
        assertThat(logical.left()).isInstanceOf(JsonPathAst.ExistsFilter.class);
    }

    @Test
    void testParseFilterLogicalAndOrWithParentheses() {
        LOG.info(() -> "TEST: testParseFilterLogicalAndOrWithParentheses - parse $..book[?(@.isbn && (@.price<10 || @.price>20))]");
        final var ast = JsonPathParser.parse("$..book[?(@.isbn && (@.price<10 || @.price>20))]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.Filter.class);
        final var filter = (JsonPathAst.Filter) ast.segments().get(1);

        assertThat(filter.expression()).isInstanceOf(JsonPathAst.LogicalFilter.class);
        final var andExpr = (JsonPathAst.LogicalFilter) filter.expression();
        assertThat(andExpr.op()).isEqualTo(JsonPathAst.LogicalOp.AND);
        assertThat(andExpr.left()).isInstanceOf(JsonPathAst.ExistsFilter.class);
        assertThat(andExpr.right()).isInstanceOf(JsonPathAst.LogicalFilter.class);

        final var orExpr = (JsonPathAst.LogicalFilter) andExpr.right();
        assertThat(orExpr.op()).isEqualTo(JsonPathAst.LogicalOp.OR);
        assertThat(orExpr.left()).isInstanceOf(JsonPathAst.ComparisonFilter.class);
        assertThat(orExpr.right()).isInstanceOf(JsonPathAst.ComparisonFilter.class);
    }

    // ========== Script expression parsing ==========

    @Test
    void testParseScriptExpression() {
        LOG.info(() -> "TEST: testParseScriptExpression - parse $..book[(@.length-1)] (last book)");
        final var ast = JsonPathParser.parse("$..book[(@.length-1)]");
        assertThat(ast.segments()).hasSize(2);
        assertThat(ast.segments().get(1)).isInstanceOf(JsonPathAst.ScriptExpression.class);
        final var script = (JsonPathAst.ScriptExpression) ast.segments().get(1);
        assertThat(script.script()).isEqualTo("@.length-1");
    }

    // ========== Complex paths ==========

    @Test
    void testParsePropertyAfterArrayIndex() {
        LOG.info(() -> "TEST: testParsePropertyAfterArrayIndex - parse $.store.book[0].title");
        final var ast = JsonPathParser.parse("$.store.book[0].title");
        assertThat(ast.segments()).hasSize(4);
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(0)).name()).isEqualTo("store");
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(1)).name()).isEqualTo("book");
        assertThat(((JsonPathAst.ArrayIndex) ast.segments().get(2)).index()).isEqualTo(0);
        assertThat(((JsonPathAst.PropertyAccess) ast.segments().get(3)).name()).isEqualTo("title");
    }

    // ========== Error cases ==========

    @Test
    void testParseEmptyStringThrows() {
        LOG.info(() -> "TEST: testParseEmptyStringThrows");
        assertThatThrownBy(() -> JsonPathParser.parse(""))
            .isInstanceOf(JsonPathParseException.class)
            .hasMessageContaining("must start with $");
    }

    @Test
    void testParseNullThrows() {
        LOG.info(() -> "TEST: testParseNullThrows");
        assertThatThrownBy(() -> JsonPathParser.parse(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testParseMissingRootThrows() {
        LOG.info(() -> "TEST: testParseMissingRootThrows");
        assertThatThrownBy(() -> JsonPathParser.parse("store.book"))
            .isInstanceOf(JsonPathParseException.class)
            .hasMessageContaining("must start with $");
    }

    @ParameterizedTest
    @ValueSource(strings = {"$.", "$[", "$...", "$.store[", "$.store."})
    void testParseIncompletePathThrows(String path) {
        LOG.info(() -> "TEST: testParseIncompletePathThrows - " + path);
        assertThatThrownBy(() -> JsonPathParser.parse(path))
            .isInstanceOf(JsonPathParseException.class);
    }

    public static void main(String[] args) {
        final var storeDoc = """
        { "store": {
            "book": [
              { "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
              },
              { "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
              },
              { "category": "fiction",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "ISBN": "0-553-21311-3",
                "price": 8.99
              },
              { "category": "fiction",
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

        final JsonValue doc = Json.parse(storeDoc);

        final var path = args.length > 0 ? args[0] : "$.store.book";
        final var compiled = JsonPath.parse(path);
        final var matches = compiled.query(doc);

        System.out.println("path: " + path);
        System.out.println("matches: " + matches.size());
        matches.forEach(v -> System.out.println(Json.toDisplayString(v, 2)));
    }
}
