package json.java21.jsonpath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/// Tests for the JsonPath ESM renderer.
///
/// Verifies that the generated JavaScript is syntactically valid and
/// produces the correct function structure for each segment type.
class JsonPathEsmRendererTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathEsmRendererTest.class.getName());

    @Test
    void renderSimplePropertyAccess() {
        LOG.info(() -> "TEST: renderSimplePropertyAccess");

        final var esm = renderExpression("$.store.bicycle.color");

        assertThat(esm).contains("export function query(root)");
        assertThat(esm).contains("\"store\"");
        assertThat(esm).contains("\"bicycle\"");
        assertThat(esm).contains("\"color\"");
        assertThat(esm).contains("results.push(");
        assertThat(esm).contains("return results;");
    }

    @Test
    void renderWildcard() {
        LOG.info(() -> "TEST: renderWildcard");

        final var esm = renderExpression("$.store.book[*].title");

        assertThat(esm).contains("Array.isArray");
        assertThat(esm).contains("\"title\"");
    }

    @Test
    void renderRecursiveDescent() {
        LOG.info(() -> "TEST: renderRecursiveDescent");

        final var esm = renderExpression("$..price");

        assertThat(esm).contains("function _descent");
        assertThat(esm).contains("\"price\"");
        assertThat(esm).contains("Object.values");
    }

    @Test
    void renderFilter() {
        LOG.info(() -> "TEST: renderFilter");

        final var esm = renderExpression("$.store.book[?(@.price < 10)]");

        assertThat(esm).contains("Array.isArray");
        assertThat(esm).contains("?.[\"price\"]");
        assertThat(esm).contains("< 10");
    }

    @Test
    void renderArraySlice() {
        LOG.info(() -> "TEST: renderArraySlice");

        final var esm = renderExpression("$.store.book[:2]");

        assertThat(esm).contains("for (let");
    }

    @Test
    void renderArrayIndex() {
        LOG.info(() -> "TEST: renderArrayIndex");

        final var esm = renderExpression("$.store.book[0]");

        assertThat(esm).contains("Array.isArray");
    }

    @Test
    void renderNegativeArrayIndex() {
        LOG.info(() -> "TEST: renderNegativeArrayIndex");

        final var esm = renderExpression("$.store.book[-1]");

        assertThat(esm).contains(".length + -1");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "$.store.bicycle.color",
        "$.store.book[*].title",
        "$..price",
        "$.store.book[?(@.isbn)]",
        "$.store.book[?(@.price < 10)]",
        "$.store.book[:2]",
        "$.store.book[0]",
        "$.store.book[-1]",
        "$.store.book[0,1]",
        "$.store.book[(@.length-1)]",
        "$"
    })
    void renderedModuleIsStructurallyValid(String expression) {
        LOG.info(() -> "TEST: renderedModuleIsStructurallyValid - " + expression);

        final var esm = renderExpression(expression);

        assertThat(esm).startsWith("// Generated JsonPath query:");
        assertThat(esm).contains("export function query(root)");
        assertThat(esm).contains("const results = [];");
        assertThat(esm).contains("return results;");
        assertThat(esm).endsWith("}\n");

        LOG.fine(() -> "Generated ESM:\n" + esm);
    }

    private static String renderExpression(String expression) {
        final var parsed = JsonPath.parse(expression);
        return JsonPathEsmRenderer.render(parsed.ast(), expression);
    }
}
