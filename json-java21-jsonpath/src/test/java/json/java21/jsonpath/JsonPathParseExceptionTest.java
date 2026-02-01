package json.java21.jsonpath;

import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Unit tests for JsonPathParseException formatting and details.
class JsonPathParseExceptionTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathParseExceptionTest.class.getName());

    @Test
    void testMessageWithPathAndPositionIncludesNearChar() {
        LOG.info(() -> "TEST: testMessageWithPathAndPositionIncludesNearChar");

        final var path = "store.book";
        assertThatThrownBy(() -> JsonPath.parse(path))
            .isInstanceOf(JsonPathParseException.class)
            .satisfies(e -> {
                final var ex = (JsonPathParseException) e;
                assertThat(ex.path()).isEqualTo(path);
                assertThat(ex.position()).isEqualTo(0);
                assertThat(ex.getMessage()).contains("at position 0");
                assertThat(ex.getMessage()).contains("in path: " + path);
                assertThat(ex.getMessage()).contains("near 's'");
            });
    }

    @Test
    void testMessageWithPositionAtEndDoesNotIncludeNearChar() {
        LOG.info(() -> "TEST: testMessageWithPositionAtEndDoesNotIncludeNearChar");

        final var path = "$.";
        assertThatThrownBy(() -> JsonPath.parse(path))
            .isInstanceOf(JsonPathParseException.class)
            .satisfies(e -> {
                final var ex = (JsonPathParseException) e;
                assertThat(ex.path()).isEqualTo(path);
                assertThat(ex.position()).isEqualTo(path.length());
                assertThat(ex.getMessage()).contains("at position " + path.length());
                assertThat(ex.getMessage()).contains("in path: " + path);
                assertThat(ex.getMessage()).doesNotContain("near '");
            });
    }

    @Test
    void testSimpleConstructorHasNoPositionOrPath() {
        LOG.info(() -> "TEST: testSimpleConstructorHasNoPositionOrPath");

        final var ex = new JsonPathParseException("boom");
        assertThat(ex.position()).isEqualTo(-1);
        assertThat(ex.path()).isNull();
        assertThat(ex.getMessage()).isEqualTo("boom");
    }
}
