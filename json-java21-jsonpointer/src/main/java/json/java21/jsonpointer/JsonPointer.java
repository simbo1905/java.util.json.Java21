package json.java21.jsonpointer;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/// RFC 6901 JSON Pointer — an exact-address path into a JSON document.
///
/// A JSON Pointer is a Unicode string of zero or more reference tokens, each
/// prefixed by `/`. The empty string `""` identifies the root document.
/// Each token names an object member or an array index.
///
/// Escaping (RFC 6901 §3):
///
/// - `~1` → `/`
/// - `~0` → `~`
///
/// The replacement order matters: `~1` is decoded before `~0` so that `~01`
/// decodes to the literal string `~1` rather than `/`.
///
/// Usage:
/// ```java
/// JsonValue doc = Json.parse(jsonText);
/// JsonValue value = JsonPointer.parse("/foo/0").resolve(doc);
/// boolean found = JsonPointer.parse("/foo/0").exists(doc);
/// ```
///
/// @see <a href="https://www.rfc-editor.org/rfc/rfc6901">RFC 6901 — JSON Pointer</a>
public final class JsonPointer {

    private static final Logger LOG = Logger.getLogger(JsonPointer.class.getName());

    private final String raw;
    private final List<String> tokens;

    private JsonPointer(String raw, List<String> tokens) {
        this.raw = raw;
        this.tokens = List.copyOf(tokens);
    }

    /// Parses a JSON Pointer string.
    ///
    /// The empty string `""` is valid and identifies the root document. Any
    /// non-empty pointer must begin with `/`. Escape sequences must be
    /// well-formed: `~` must be followed by `0` or `1`.
    ///
    /// @param pointer the JSON Pointer string; must not be `null`
    /// @return a `JsonPointer` instance
    /// @throws NullPointerException       if `pointer` is `null`
    /// @throws JsonPointerSyntaxException if `pointer` is not a valid JSON Pointer
    public static JsonPointer parse(String pointer) {
        Objects.requireNonNull(pointer, "pointer must not be null");
        LOG.fine(() -> "parsing JSON Pointer: \"" + pointer + "\"");

        if (pointer.isEmpty()) {
            return new JsonPointer(pointer, List.of());
        }

        if (pointer.charAt(0) != '/') {
            throw new JsonPointerSyntaxException(
                    "non-empty JSON Pointer must start with '/'", pointer);
        }

        // Split on '/' — the first char is '/' so the first segment is always empty;
        // skip it by starting at index 1.
        final var segments = pointer.substring(1).split("/", -1);
        final var tokenList = new ArrayList<String>(segments.length);
        for (final var segment : segments) {
            tokenList.add(decodeToken(segment, pointer));
        }

        LOG.fine(() -> "parsed " + tokenList.size() + " token(s) from \"" + pointer + "\"");
        return new JsonPointer(pointer, tokenList);
    }

    /// Returns the decoded reference tokens of this pointer.
    ///
    /// The returned list is unmodifiable. An empty list means the pointer
    /// identifies the root document.
    ///
    /// @return immutable list of decoded reference tokens
    public List<String> tokens() {
        return tokens;
    }

    /// Resolves this pointer against `document`, returning the identified value.
    ///
    /// @param document the JSON document to resolve against; must not be `null`
    /// @return the `JsonValue` identified by this pointer
    /// @throws NullPointerException            if `document` is `null`
    /// @throws JsonPointerResolutionException  if no value exists at this pointer
    public JsonValue resolve(JsonValue document) {
        Objects.requireNonNull(document, "document must not be null");
        LOG.fine(() -> "resolving pointer \"" + raw + "\" against document");

        return resolveInternal(document).orElseThrow(
                () -> new JsonPointerResolutionException(
                        "pointer does not identify an existing value", raw, null));
    }

    /// Returns `true` if this pointer identifies an existing value in `document`.
    ///
    /// @param document the JSON document; must not be `null`
    /// @return `true` when the pointed-to value exists, `false` otherwise
    /// @throws NullPointerException if `document` is `null`
    public boolean exists(JsonValue document) {
        Objects.requireNonNull(document, "document must not be null");
        return resolveInternal(document).isPresent();
    }

    /// Returns the original JSON Pointer string as passed to [#parse(String)].
    ///
    /// The returned value is a valid JSON Pointer and satisfies:
    /// `JsonPointer.parse(p.toString()).equals(p)`.
    ///
    /// @return the canonical pointer string
    @Override
    public String toString() {
        return raw;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JsonPointer other && raw.equals(other.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /// Attempts to resolve this pointer; returns empty if any step fails.
    private Optional<JsonValue> resolveInternal(JsonValue document) {
        var current = document;
        for (final var token : tokens) {
            final var next = step(current, token);
            if (next.isEmpty()) {
                LOG.fine(() -> "resolution failed at token \"" + token
                        + "\" for pointer \"" + raw + "\"");
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.of(current);
    }

    /// Applies a single reference token to `node`, returning the resulting value
    /// or empty if the step cannot be taken.
    private static Optional<JsonValue> step(JsonValue node, String token) {
        return switch (node) {
            case JsonObject obj -> {
                final var value = obj.members().get(token);
                yield Optional.ofNullable(value);
            }
            case JsonArray arr -> {
                final var elements = arr.elements();
                final var index = parseArrayIndex(token, elements.size());
                yield index >= 0 ? Optional.of(elements.get(index)) : Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    /// Parses an array reference token.
    ///
    /// Returns the non-negative index when the token is a valid non-negative
    /// integer within bounds, or `-1` in every other case:
    ///
    /// - `-` is the special "append" token defined by RFC 6902; it is not a
    ///   valid resolution target in RFC 6901.
    /// - Leading zeros are forbidden (e.g. `"01"` is invalid per RFC 6901 §4).
    /// - Negative numbers are not permitted.
    /// - Any non-numeric token applied to an array fails resolution.
    ///
    /// @param token  the reference token
    /// @param length the array length
    /// @return the index, or `-1` on failure
    private static int parseArrayIndex(String token, int length) {
        if (token.isEmpty()) {
            return -1;
        }
        // '-' is the RFC 6902 append sentinel; not resolvable in RFC 6901
        if ("-".equals(token)) {
            return -1;
        }
        // Leading zeros are forbidden for non-zero values
        if (token.length() > 1 && token.charAt(0) == '0') {
            return -1;
        }
        // Must be all digits
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return -1;
            }
        }
        try {
            final var index = Integer.parseInt(token);
            return (index >= 0 && index < length) ? index : -1;
        } catch (NumberFormatException e) {
            // Overflow — value too large to be a valid index
            return -1;
        }
    }

    /// Decodes a single reference token according to RFC 6901 §3.
    ///
    /// The replacement order is mandated by the RFC:
    ///
    /// 1. `~1` → `/`
    /// 2. `~0` → `~`
    ///
    /// This order ensures that `~01` becomes `~1` (not `/`).
    ///
    /// @param token   the raw (encoded) reference token segment
    /// @param pointer the full pointer string (used in exception messages)
    /// @return the decoded token
    /// @throws JsonPointerSyntaxException if the token contains an invalid `~` escape
    static String decodeToken(String token, String pointer) {
        if (!token.contains("~")) {
            return token;
        }
        // Validate: every '~' must be followed by '0' or '1'
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '~') {
                if (i + 1 >= token.length()) {
                    throw new JsonPointerSyntaxException(
                            "invalid escape sequence: '~' at end of token", pointer);
                }
                final var next = token.charAt(i + 1);
                if (next != '0' && next != '1') {
                    throw new JsonPointerSyntaxException(
                            "invalid escape sequence '~" + next + "' in token", pointer);
                }
            }
        }
        // Decode in the RFC-mandated order: ~1 first, then ~0
        return token.replace("~1", "/").replace("~0", "~");
    }
}
