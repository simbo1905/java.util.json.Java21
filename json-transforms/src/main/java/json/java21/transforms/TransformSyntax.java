package json.java21.transforms;

import java.util.Objects;

sealed interface TransformSyntax permits TransformSyntax.Nothing {
    enum Nothing implements TransformSyntax { INSTANCE }

    String SYNTAX_PREFIX = "@jdt.";

    String VERB_REMOVE = "@jdt.remove";
    String VERB_REPLACE = "@jdt.replace";
    String VERB_MERGE = "@jdt.merge";
    String VERB_RENAME = "@jdt.rename";

    String ATTR_PATH = "@jdt.path";
    String ATTR_VALUE = "@jdt.value";

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

