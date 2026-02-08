package json.java21.jdt;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// AST representation for JSON Document Transform specifications.
///
/// A JDT transform document is parsed into an immutable tree of [JdtNode] records.
/// Each node represents either a directive-bearing transform, a default merge,
/// or a direct replacement. The AST can be walked with exhaustive switch expressions
/// for bytecode codegen, ESM codegen, or interpretation.
///
/// ## Node Types
/// - [DirectiveNode]: Contains `@jdt.*` directives (rename, remove, merge, replace)
///   plus child transforms for non-directive keys
/// - [MergeNode]: Default object-to-object deep merge (no directives)
/// - [ReplacementNode]: Direct value replacement (primitive or array)
///
/// ## Parse Entry Point
/// Use [JdtAstParser.parse] to convert a `JsonValue` transform into a [JdtNode].
public interface JdtAst {

    /// A node in the JDT transform AST.
    sealed interface JdtNode permits DirectiveNode, MergeNode, ReplacementNode {}

    /// A transform node containing `@jdt.*` directives.
    ///
    /// Directives execute in order: Rename -> Remove -> Merge -> Replace.
    /// After directives, child transforms are applied as recursive merges.
    ///
    /// @param rename the rename directive spec (null if absent)
    /// @param remove the remove directive spec (null if absent)
    /// @param merge the merge directive spec (null if absent)
    /// @param replace the replace directive spec (null if absent)
    /// @param children non-directive key transforms to apply after directives
    record DirectiveNode(
        JsonValue rename,
        JsonValue remove,
        JsonValue merge,
        JsonValue replace,
        Map<String, JdtNode> children
    ) implements JdtNode {
        public DirectiveNode {
            Objects.requireNonNull(children, "children must not be null");
            children = Map.copyOf(children);
        }
    }

    /// Default object merge: recursively merge transform keys into the source object.
    ///
    /// @param children the key-to-transform mapping for recursive merge
    record MergeNode(Map<String, JdtNode> children) implements JdtNode {
        public MergeNode {
            Objects.requireNonNull(children, "children must not be null");
            children = Map.copyOf(children);
        }
    }

    /// Direct replacement: the transform value replaces the source wholesale.
    ///
    /// @param value the replacement value (primitive, array, or object without directives)
    record ReplacementNode(JsonValue value) implements JdtNode {
        public ReplacementNode {
            Objects.requireNonNull(value, "value must not be null");
        }
    }
}
