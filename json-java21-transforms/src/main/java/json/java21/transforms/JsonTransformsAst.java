package json.java21.transforms;

import jdk.sandbox.java.util.json.JsonValue;
import json.java21.jsonpath.JsonPath;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// AST representation for JSON Transform specifications.
/// Based on the Microsoft JSON Document Transforms specification:
/// https://github.com/Microsoft/json-document-transforms/wiki
///
/// A transform specification is a JSON document that describes operations
/// (rename, remove, replace, merge) to apply to a source document.
sealed interface JsonTransformsAst {

    /// Root of a transform specification containing the top-level operations.
    record TransformRoot(List<TransformNode> nodes, JsonPath pathSelector) implements JsonTransformsAst {
        public TransformRoot {
            Objects.requireNonNull(nodes, "nodes must not be null");
            nodes = List.copyOf(nodes); // defensive copy
            // pathSelector can be null (no @jdt.path at root)
        }

        /// Convenience constructor without path selector.
        public TransformRoot(List<TransformNode> nodes) {
            this(nodes, null);
        }
    }

    /// A node in the transform tree - either an operation or a nested object transform.
    sealed interface TransformNode permits
            PropertyTransform,
            PathTransform {}

    /// Transform for a specific property key in the source document.
    record PropertyTransform(String key, TransformOperation operation) implements TransformNode {
        public PropertyTransform {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(operation, "operation must not be null");
        }
    }

    /// Transform that uses a JsonPath to select targets.
    /// The @jdt.path attribute specifies which nodes to transform.
    record PathTransform(JsonPath path, List<TransformNode> nodes) implements TransformNode {
        public PathTransform {
            Objects.requireNonNull(path, "path must not be null");
            Objects.requireNonNull(nodes, "nodes must not be null");
            nodes = List.copyOf(nodes); // defensive copy
        }
    }

    /// An operation to apply to a target property or value.
    sealed interface TransformOperation permits
            ValueOp,
            RemoveOp,
            RenameOp,
            ReplaceOp,
            MergeOp,
            NestedTransform,
            CompoundOp {}

    /// @jdt.value - Sets the value of a property.
    /// If the property does not exist, it will be created.
    record ValueOp(JsonValue value) implements TransformOperation {
        public ValueOp {
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    /// @jdt.remove - Removes a property from the document.
    /// The boolean value indicates whether to remove (true) or not (false).
    record RemoveOp(boolean remove) implements TransformOperation {}

    /// @jdt.rename - Renames a property to a new name.
    record RenameOp(String newName) implements TransformOperation {
        public RenameOp {
            Objects.requireNonNull(newName, "newName must not be null");
            if (newName.isEmpty()) {
                throw new IllegalArgumentException("newName must not be empty");
            }
        }
    }

    /// @jdt.replace - Replaces a property value with a new value.
    /// Unlike ValueOp, this only works if the property already exists.
    record ReplaceOp(JsonValue value) implements TransformOperation {
        public ReplaceOp {
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    /// @jdt.merge - Performs a deep merge of an object with the existing value.
    record MergeOp(JsonValue mergeValue) implements TransformOperation {
        public MergeOp {
            Objects.requireNonNull(mergeValue, "mergeValue must not be null");
        }
    }

    /// Nested transform for object properties.
    /// When a transform object contains nested properties without @jdt.* attributes,
    /// they represent recursive transforms on nested objects.
    record NestedTransform(List<TransformNode> children) implements TransformOperation {
        public NestedTransform {
            Objects.requireNonNull(children, "children must not be null");
            children = List.copyOf(children); // defensive copy
        }
    }

    /// Compound operation - multiple operations on the same property.
    /// For example: rename and then set a value.
    record CompoundOp(List<TransformOperation> operations) implements TransformOperation {
        public CompoundOp {
            Objects.requireNonNull(operations, "operations must not be null");
            if (operations.size() < 2) {
                throw new IllegalArgumentException("CompoundOp must have at least 2 operations");
            }
            operations = List.copyOf(operations); // defensive copy
        }
    }

    /// Constants for the transform directive keys.
    enum Directive {
        PATH("@jdt.path"),
        VALUE("@jdt.value"),
        REMOVE("@jdt.remove"),
        RENAME("@jdt.rename"),
        REPLACE("@jdt.replace"),
        MERGE("@jdt.merge");

        private final String key;

        Directive(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        /// Checks if a string is any transform directive.
        static boolean isDirective(String key) {
            return key.startsWith("@jdt.");
        }

        /// Parses a directive from a key string.
        /// @return the Directive or null if not recognized
        static Directive fromKey(String key) {
            for (Directive d : values()) {
                if (d.key.equals(key)) {
                    return d;
                }
            }
            return null;
        }
    }
}
