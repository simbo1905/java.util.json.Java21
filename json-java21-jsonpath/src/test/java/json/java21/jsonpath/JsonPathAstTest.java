package json.java21.jsonpath;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Unit tests for JsonPathAst record types
class JsonPathAstTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathAstTest.class.getName());

    @Test
    void testRootCreation() {
        LOG.info(() -> "TEST: testRootCreation");
        final var root = new JsonPathAst.Root(List.of());
        assertThat(root.segments()).isEmpty();
    }

    @Test
    void testRootWithSegments() {
        LOG.info(() -> "TEST: testRootWithSegments");
        final var segments = List.<JsonPathAst.Segment>of(
            new JsonPathAst.PropertyAccess("store"),
            new JsonPathAst.PropertyAccess("book")
        );
        final var root = new JsonPathAst.Root(segments);
        assertThat(root.segments()).hasSize(2);
    }

    @Test
    void testRootDefensiveCopy() {
        LOG.info(() -> "TEST: testRootDefensiveCopy");
        final var mutableList = new java.util.ArrayList<JsonPathAst.Segment>();
        mutableList.add(new JsonPathAst.PropertyAccess("store"));
        final var root = new JsonPathAst.Root(mutableList);
        mutableList.add(new JsonPathAst.PropertyAccess("book"));
        // Root should have defensive copy, so adding to original list doesn't affect it
        assertThat(root.segments()).hasSize(1);
    }

    @Test
    void testPropertyAccess() {
        LOG.info(() -> "TEST: testPropertyAccess");
        final var prop = new JsonPathAst.PropertyAccess("author");
        assertThat(prop.name()).isEqualTo("author");
    }

    @Test
    void testPropertyAccessNullThrows() {
        LOG.info(() -> "TEST: testPropertyAccessNullThrows");
        assertThatThrownBy(() -> new JsonPathAst.PropertyAccess(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testArrayIndex() {
        LOG.info(() -> "TEST: testArrayIndex");
        final var index = new JsonPathAst.ArrayIndex(0);
        assertThat(index.index()).isEqualTo(0);
        
        final var negIndex = new JsonPathAst.ArrayIndex(-1);
        assertThat(negIndex.index()).isEqualTo(-1);
    }

    @Test
    void testArraySlice() {
        LOG.info(() -> "TEST: testArraySlice");
        final var slice = new JsonPathAst.ArraySlice(0, 2, null);
        assertThat(slice.start()).isEqualTo(0);
        assertThat(slice.end()).isEqualTo(2);
        assertThat(slice.step()).isNull();
    }

    @Test
    void testWildcard() {
        LOG.info(() -> "TEST: testWildcard");
        final var wildcard = new JsonPathAst.Wildcard();
        assertThat(wildcard).isNotNull();
    }

    @Test
    void testRecursiveDescent() {
        LOG.info(() -> "TEST: testRecursiveDescent");
        final var descent = new JsonPathAst.RecursiveDescent(
            new JsonPathAst.PropertyAccess("author")
        );
        assertThat(descent.target()).isInstanceOf(JsonPathAst.PropertyAccess.class);
    }

    @Test
    void testFilter() {
        LOG.info(() -> "TEST: testFilter");
        final var filter = new JsonPathAst.Filter(
            new JsonPathAst.ExistsFilter(
                new JsonPathAst.PropertyPath(List.of("ISBN"))
            )
        );
        assertThat(filter.expression()).isInstanceOf(JsonPathAst.ExistsFilter.class);
    }

    @Test
    void testUnion() {
        LOG.info(() -> "TEST: testUnion");
        final var union = new JsonPathAst.Union(List.of(
            new JsonPathAst.ArrayIndex(0),
            new JsonPathAst.ArrayIndex(1)
        ));
        assertThat(union.selectors()).hasSize(2);
    }

    @Test
    void testUnionRequiresAtLeastTwoSelectors() {
        LOG.info(() -> "TEST: testUnionRequiresAtLeastTwoSelectors");
        assertThatThrownBy(() -> new JsonPathAst.Union(List.of(new JsonPathAst.ArrayIndex(0))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least 2");
    }

    @Test
    void testComparisonFilter() {
        LOG.info(() -> "TEST: testComparisonFilter");
        final var filter = new JsonPathAst.ComparisonFilter(
            new JsonPathAst.PropertyPath(List.of("price")),
            JsonPathAst.ComparisonOp.LT,
            new JsonPathAst.LiteralValue(10)
        );
        assertThat(filter.op()).isEqualTo(JsonPathAst.ComparisonOp.LT);
    }

    @Test
    void testPropertyPath() {
        LOG.info(() -> "TEST: testPropertyPath");
        final var path = new JsonPathAst.PropertyPath(List.of("store", "book", "price"));
        assertThat(path.properties()).containsExactly("store", "book", "price");
    }

    @Test
    void testPropertyPathRequiresAtLeastOneProperty() {
        LOG.info(() -> "TEST: testPropertyPathRequiresAtLeastOneProperty");
        assertThatThrownBy(() -> new JsonPathAst.PropertyPath(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one property");
    }

    @Test
    void testComparisonOps() {
        LOG.info(() -> "TEST: testComparisonOps");
        assertThat(JsonPathAst.ComparisonOp.EQ.symbol()).isEqualTo("==");
        assertThat(JsonPathAst.ComparisonOp.NE.symbol()).isEqualTo("!=");
        assertThat(JsonPathAst.ComparisonOp.LT.symbol()).isEqualTo("<");
        assertThat(JsonPathAst.ComparisonOp.LE.symbol()).isEqualTo("<=");
        assertThat(JsonPathAst.ComparisonOp.GT.symbol()).isEqualTo(">");
        assertThat(JsonPathAst.ComparisonOp.GE.symbol()).isEqualTo(">=");
    }

    @Test
    void testLogicalOps() {
        LOG.info(() -> "TEST: testLogicalOps");
        assertThat(JsonPathAst.LogicalOp.AND.symbol()).isEqualTo("&&");
        assertThat(JsonPathAst.LogicalOp.OR.symbol()).isEqualTo("||");
        assertThat(JsonPathAst.LogicalOp.NOT.symbol()).isEqualTo("!");
    }
}
