package json.java21.jdt;

import jdk.sandbox.java.util.json.*;

import java.util.LinkedHashMap;
import java.util.logging.Logger;

import json.java21.jdt.JdtAst.*;

/// Parses a JSON transform document into a [JdtAst.JdtNode] tree.
///
/// The parser examines each object in the transform for `@jdt.*` directive
/// keys and builds the appropriate AST node type.
final class JdtAstParser {

    private static final Logger LOG = Logger.getLogger(JdtAstParser.class.getName());

    private JdtAstParser() {}

    /// Parses a transform JsonValue into an AST node.
    ///
    /// @param transform the transform specification value
    /// @return the root AST node
    static JdtNode parse(JsonValue transform) {
        if (!(transform instanceof JsonObject transformObj)) {
            // Non-object transforms are direct replacements
            return new ReplacementNode(transform);
        }

        final var members = transformObj.members();

        // Check if this object has any JDT directives
        final var hasDirectives = members.keySet().stream()
                .anyMatch(k -> k.startsWith(Jdt.JDT_PREFIX));

        if (hasDirectives) {
            return parseDirectiveNode(transformObj);
        }

        // No directives: this is a default merge node
        return parseMergeNode(transformObj);
    }

    private static DirectiveNode parseDirectiveNode(JsonObject transformObj) {
        final var members = transformObj.members();

        final var rename = members.get(Jdt.JDT_RENAME);
        final var remove = members.get(Jdt.JDT_REMOVE);
        final var merge = members.get(Jdt.JDT_MERGE);
        final var replace = members.get(Jdt.JDT_REPLACE);

        // Parse non-directive keys as child transforms
        final var children = new LinkedHashMap<String, JdtNode>();
        for (final var entry : members.entrySet()) {
            if (!entry.getKey().startsWith(Jdt.JDT_PREFIX)) {
                children.put(entry.getKey(), parse(entry.getValue()));
            }
        }

        LOG.finer(() -> "Parsed DirectiveNode: rename=" + (rename != null) +
                " remove=" + (remove != null) + " merge=" + (merge != null) +
                " replace=" + (replace != null) + " children=" + children.size());

        return new DirectiveNode(rename, remove, merge, replace, children);
    }

    private static MergeNode parseMergeNode(JsonObject transformObj) {
        final var children = new LinkedHashMap<String, JdtNode>();
        for (final var entry : transformObj.members().entrySet()) {
            children.put(entry.getKey(), parse(entry.getValue()));
        }

        LOG.finer(() -> "Parsed MergeNode with " + children.size() + " children");

        return new MergeNode(children);
    }
}
