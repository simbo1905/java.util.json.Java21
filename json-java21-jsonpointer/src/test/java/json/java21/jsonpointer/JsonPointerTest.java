package json.java21.jsonpointer;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonNull;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// RFC 6901 JSON Pointer specification tests.
///
/// Section 5 of RFC 6901 defines an example document and a table of pointer
/// expressions with their expected resolved values. Every entry in that table
/// is covered here, plus additional edge-case tests required by the issue.
///
/// @see <a href="https://www.rfc-editor.org/rfc/rfc6901#section-5">RFC 6901 §5</a>
class JsonPointerTest extends JsonPointerLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPointerTest.class.getName());

    // -------------------------------------------------------------------------
    // RFC 6901 §5 example document
    // -------------------------------------------------------------------------

    private static final String RFC_DOCUMENT = """
            {
              "foo":   ["bar", "baz"],
              "":      0,
              "a/b":   1,
              "c%d":   2,
              "e^f":   3,
              "g|h":   4,
              "i\\\\j": 5,
              "k\\"l":  6,
              " ":     7,
              "m~n":   8
            }
            """;

    private static JsonValue rfcDoc() {
        return Json.parse(RFC_DOCUMENT);
    }

    // -------------------------------------------------------------------------
    // RFC 6901 §5 example table — every row
    // -------------------------------------------------------------------------

    @Test
    void emptyPointerResolvesToWholeDocument() {
        LOG.info("TEST: emptyPointerResolvesToWholeDocument");
        final var doc = rfcDoc();
        final var result = JsonPointer.parse("").resolve(doc);
        assertThat(result).isInstanceOf(JsonObject.class);
        assertThat(result).isSameAs(doc);
    }

    @Test
    void fooResolvesToArray() {
        LOG.info("TEST: fooResolvesToArray");
        final var result = JsonPointer.parse("/foo").resolve(rfcDoc());
        assertThat(result).isInstanceOf(JsonArray.class);
        assertThat(((JsonArray) result).elements()).hasSize(2);
    }

    @Test
    void fooZeroResolvesToFirstArrayElement() {
        LOG.info("TEST: fooZeroResolvesToFirstArrayElement");
        final var result = JsonPointer.parse("/foo/0").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("\"bar\"");
    }

    @Test
    void slashAloneResolvesToEmptyStringKey() {
        LOG.info("TEST: slashAloneResolvesToEmptyStringKey");
        final var result = JsonPointer.parse("/").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("0");
    }

    @Test
    void tilde1DecodesSlashInKey() {
        LOG.info("TEST: tilde1DecodesSlashInKey - /a~1b resolves key 'a/b'");
        final var result = JsonPointer.parse("/a~1b").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("1");
    }

    @Test
    void percentInKeyIsLiteral() {
        LOG.info("TEST: percentInKeyIsLiteral - /c%d resolves key 'c%d'");
        final var result = JsonPointer.parse("/c%d").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("2");
    }

    @Test
    void caretInKeyIsLiteral() {
        LOG.info("TEST: caretInKeyIsLiteral - /e^f resolves key 'e^f'");
        final var result = JsonPointer.parse("/e^f").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("3");
    }

    @Test
    void pipeInKeyIsLiteral() {
        LOG.info("TEST: pipeInKeyIsLiteral - /g|h resolves key 'g|h'");
        final var result = JsonPointer.parse("/g|h").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("4");
    }

    @Test
    void backslashInKeyIsLiteral() {
        LOG.info("TEST: backslashInKeyIsLiteral - /i\\\\j resolves key with backslash");
        // The JSON key is "i\j" (one backslash). The pointer token is also "i\j".
        final var result = JsonPointer.parse("/i\\j").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("5");
    }

    @Test
    void doubleQuoteInKeyIsLiteral() {
        LOG.info("TEST: doubleQuoteInKeyIsLiteral - /k\\\"l resolves key with quote");
        // The JSON key is k"l. The pointer token is k"l.
        final var result = JsonPointer.parse("/k\"l").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("6");
    }

    @Test
    void spaceKeyIsLiteral() {
        LOG.info("TEST: spaceKeyIsLiteral - '/ ' resolves key ' '");
        final var result = JsonPointer.parse("/ ").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("7");
    }

    @Test
    void tilde0DecodesTildeInKey() {
        LOG.info("TEST: tilde0DecodesTildeInKey - /m~0n resolves key 'm~n'");
        final var result = JsonPointer.parse("/m~0n").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("8");
    }

    // -------------------------------------------------------------------------
    // Escape ordering — tricky token
    // -------------------------------------------------------------------------

    @Test
    void escapeOrderingTildeOneZeroDecodesToTildeOne() {
        LOG.info("TEST: escapeOrderingTildeOneZeroDecodesToTildeOne - ~01 decodes to ~1 not /");
        // ~01 must decode as: first ~1→/, then ~0→~ ... wait, that is wrong.
        // RFC says: replace ~1 first, then ~0.
        // ~01 contains the subsequence ~0 and the digit 1.
        // Step 1 (replace ~1): ~01 has no ~1 at position 0 (it is ~0), so no change: ~01
        // Step 2 (replace ~0): ~0 → ~: result is ~1
        // So ~01 decodes to the string "~1".
        final var token = JsonPointer.decodeToken("~01", "dummy");
        assertThat(token).isEqualTo("~1");
    }

    @Test
    void escapeOrderingTildeOneDecodesToSlash() {
        LOG.info("TEST: escapeOrderingTildeOneDecodesToSlash");
        assertThat(JsonPointer.decodeToken("~1", "dummy")).isEqualTo("/");
    }

    @Test
    void escapeOrderingTildeZeroDecodesToTilde() {
        LOG.info("TEST: escapeOrderingTildeZeroDecodesToTilde");
        assertThat(JsonPointer.decodeToken("~0", "dummy")).isEqualTo("~");
    }

    // -------------------------------------------------------------------------
    // tokens() accessor
    // -------------------------------------------------------------------------

    @Test
    void tokensForEmptyPointerIsEmpty() {
        LOG.info("TEST: tokensForEmptyPointerIsEmpty");
        assertThat(JsonPointer.parse("").tokens()).isEqualTo(List.of());
    }

    @Test
    void tokensForSimplePointer() {
        LOG.info("TEST: tokensForSimplePointer");
        assertThat(JsonPointer.parse("/foo/bar").tokens()).isEqualTo(List.of("foo", "bar"));
    }

    @Test
    void tokensAreDecoded() {
        LOG.info("TEST: tokensAreDecoded");
        assertThat(JsonPointer.parse("/a~1b/m~0n").tokens()).isEqualTo(List.of("a/b", "m~n"));
    }

    @Test
    void tokensListIsUnmodifiable() {
        LOG.info("TEST: tokensListIsUnmodifiable");
        final var tokens = JsonPointer.parse("/foo").tokens();
        assertThatThrownBy(() -> tokens.add("bar"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------
    // toString() round-trip
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "round-trip: {0}")
    @ValueSource(strings = {"", "/", "/foo", "/foo/0", "/a~1b", "/m~0n", "/a~1b/m~0n"})
    void toStringRoundTrip(String pointer) {
        LOG.info(() -> "TEST: toStringRoundTrip - " + pointer);
        assertThat(JsonPointer.parse(pointer).toString()).isEqualTo(pointer);
    }

    // -------------------------------------------------------------------------
    // exists()
    // -------------------------------------------------------------------------

    @Test
    void existsTrueForValidPath() {
        LOG.info("TEST: existsTrueForValidPath");
        assertThat(JsonPointer.parse("/foo").exists(rfcDoc())).isTrue();
    }

    @Test
    void existsFalseForMissingKey() {
        LOG.info("TEST: existsFalseForMissingKey");
        assertThat(JsonPointer.parse("/nonexistent").exists(rfcDoc())).isFalse();
    }

    @Test
    void existsFalseForOutOfBoundsIndex() {
        LOG.info("TEST: existsFalseForOutOfBoundsIndex");
        assertThat(JsonPointer.parse("/foo/5").exists(rfcDoc())).isFalse();
    }

    // -------------------------------------------------------------------------
    // Invalid pointer syntax
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "invalid syntax: \"{0}\"")
    @ValueSource(strings = {"foo", "foo/bar", "~", "/foo/~2", "/foo/~", "/~"})
    void invalidPointerThrowsSyntaxException(String pointer) {
        LOG.info(() -> "TEST: invalidPointerThrowsSyntaxException - " + pointer);
        assertThatThrownBy(() -> JsonPointer.parse(pointer))
                .isInstanceOf(JsonPointerSyntaxException.class);
    }

    @Test
    void syntaxExceptionCarriesPointerString() {
        LOG.info("TEST: syntaxExceptionCarriesPointerString");
        final var pointer = "bad";
        assertThatThrownBy(() -> JsonPointer.parse(pointer))
                .isInstanceOf(JsonPointerSyntaxException.class)
                .satisfies(ex -> assertThat(((JsonPointerSyntaxException) ex).pointer())
                        .isEqualTo(pointer));
    }

    @Test
    void nullPointerThrowsNpe() {
        LOG.info("TEST: nullPointerThrowsNpe");
        assertThatThrownBy(() -> JsonPointer.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------------------------------------------------------------
    // Resolution failures
    // -------------------------------------------------------------------------

    @Test
    void missingObjectKeyThrowsResolutionException() {
        LOG.info("TEST: missingObjectKeyThrowsResolutionException");
        assertThatThrownBy(() -> JsonPointer.parse("/missing").resolve(rfcDoc()))
                .isInstanceOf(JsonPointerResolutionException.class);
    }

    @Test
    void arrayIndexOutOfBoundsThrowsResolutionException() {
        LOG.info("TEST: arrayIndexOutOfBoundsThrowsResolutionException");
        assertThatThrownBy(() -> JsonPointer.parse("/foo/2").resolve(rfcDoc()))
                .isInstanceOf(JsonPointerResolutionException.class);
    }

    @Test
    void nonNumericArrayTokenThrowsResolutionException() {
        LOG.info("TEST: nonNumericArrayTokenThrowsResolutionException");
        assertThatThrownBy(() -> JsonPointer.parse("/foo/bar").resolve(rfcDoc()))
                .isInstanceOf(JsonPointerResolutionException.class);
    }

    @Test
    void traversingPrimitiveThrowsResolutionException() {
        LOG.info("TEST: traversingPrimitiveThrowsResolutionException");
        // "/foo/0" resolves to the string "bar"; going one level deeper should fail.
        assertThatThrownBy(() -> JsonPointer.parse("/foo/0/nested").resolve(rfcDoc()))
                .isInstanceOf(JsonPointerResolutionException.class);
    }

    @Test
    void dashTokenOnArrayFailsResolution() {
        LOG.info("TEST: dashTokenOnArrayFailsResolution - '-' is not a valid RFC 6901 index");
        // '-' is defined as the append sentinel in RFC 6902 only.
        assertThatThrownBy(() -> JsonPointer.parse("/foo/-").resolve(rfcDoc()))
                .isInstanceOf(JsonPointerResolutionException.class);
    }

    @Test
    void nullDocumentThrowsNpe() {
        LOG.info("TEST: nullDocumentThrowsNpe");
        assertThatThrownBy(() -> JsonPointer.parse("/foo").resolve(null))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------------------------------------------------------------
    // Array index edge cases
    // -------------------------------------------------------------------------

    @Test
    void arrayIndexOneIsValid() {
        LOG.info("TEST: arrayIndexOneIsValid");
        final var result = JsonPointer.parse("/foo/1").resolve(rfcDoc());
        assertThat(result.toString()).isEqualTo("\"baz\"");
    }

    @Test
    void leadingZeroArrayIndexFailsResolution() {
        LOG.info("TEST: leadingZeroArrayIndexFailsResolution - '01' is not a valid RFC 6901 index");
        // RFC 6901 §6 forbids leading zeros for non-zero indices.
        assertThatThrownBy(() -> JsonPointer.parse("/foo/01").resolve(rfcDoc()))
                .isInstanceOf(JsonPointerResolutionException.class);
    }

    @Test
    void negativeArrayIndexFailsResolution() {
        LOG.info("TEST: negativeArrayIndexFailsResolution");
        assertThatThrownBy(() -> JsonPointer.parse("/foo/-1").resolve(rfcDoc()))
                .isInstanceOf(JsonPointerResolutionException.class);
    }

    // -------------------------------------------------------------------------
    // JSON null value is resolvable
    // -------------------------------------------------------------------------

    @Test
    void nullValueInDocumentIsResolvable() {
        LOG.info("TEST: nullValueInDocumentIsResolvable");
        final var doc = Json.parse("{\"key\": null}");
        final var result = JsonPointer.parse("/key").resolve(doc);
        assertThat(result).isInstanceOf(JsonNull.class);
    }

    // -------------------------------------------------------------------------
    // Nested document
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "nested resolve: {0} -> {1}")
    @CsvSource({
            "/a,     1",
            "/b/c,   2",
            "/b/d/0, 3",
            "/b/d/1, 4"
    })
    void nestedDocumentResolution(String pointer, String expectedJson) {
        LOG.info(() -> "TEST: nestedDocumentResolution - " + pointer + " -> " + expectedJson);
        final var doc = Json.parse("{\"a\":1,\"b\":{\"c\":2,\"d\":[3,4]}}");
        final var result = JsonPointer.parse(pointer.trim()).resolve(doc);
        assertThat(result.toString()).isEqualTo(expectedJson.trim());
    }
}
