package json.java21.transforms;

import java.util.Objects;

final class TransformSyntax {

    private TransformSyntax() {}

    static final String SYNTAX_PREFIX = "@jdt.";

    static final String VERB_REMOVE = "@jdt.remove";
    static final String VERB_REPLACE = "@jdt.replace";
    static final String VERB_MERGE = "@jdt.merge";
    static final String VERB_RENAME = "@jdt.rename";

    static final String ATTR_PATH = "@jdt.path";
    static final String ATTR_VALUE = "@jdt.value";

    static boolean isSyntaxKey(String key) {
        return key != null && key.startsWith(SYNTAX_PREFIX);
    }

    static String syntaxSuffixOrNull(String key) {
        if (!isSyntaxKey(key)) return null;
        return key.substring(SYNTAX_PREFIX.length());
    }

    static String normalizePathString(String path) {
        Objects.requireNonNull(path, "path must not be null");
        final var trimmed = path.trim();
        if (trimmed.startsWith("@")) {
            return "$" + trimmed.substring(1);
        }
        return trimmed;
    }
}

