package json.java21.transforms;

import jdk.sandbox.java.util.json.*;
import json.java21.jsonpath.JsonPath;
import json.java21.jsonpath.JsonPathParseException;
import json.java21.transforms.JsonTransformsAst.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/// Parser for JSON Transform specifications.
/// Converts JSON documents into an immutable AST representation.
///
/// Based on the Microsoft JSON Document Transforms specification:
/// https://github.com/Microsoft/json-document-transforms/wiki
final class JsonTransformsParser {

    private static final Logger LOG = Logger.getLogger(JsonTransformsParser.class.getName());

    /// Parses a JSON value into a TransformRoot AST.
    /// @param transform the transform specification as a JsonValue
    /// @return the parsed TransformRoot
    /// @throws NullPointerException if transform is null
    /// @throws JsonTransformsParseException if the transform is invalid
    static TransformRoot parse(JsonValue transform) {
        Objects.requireNonNull(transform, "transform must not be null");
        LOG.fine(() -> "Parsing transform specification");

        if (!(transform instanceof JsonObject obj)) {
            throw new JsonTransformsParseException(
                "Transform specification must be a JSON object, got: " + transform.getClass().getSimpleName());
        }

        return parseRoot(obj);
    }

    /// Parses the root level of a transform specification.
    private static TransformRoot parseRoot(JsonObject obj) {
        final Map<String, JsonValue> members = obj.members();
        final List<TransformNode> nodes = new ArrayList<>();
        JsonPath pathSelector = null;

        // Check for root-level @jdt.path
        if (members.containsKey(Directive.PATH.key())) {
            final JsonValue pathValue = members.get(Directive.PATH.key());
            if (!(pathValue instanceof JsonString pathStr)) {
                throw new JsonTransformsParseException(
                    "@jdt.path must be a string, got: " + pathValue.getClass().getSimpleName());
            }
            pathSelector = parseJsonPath(pathStr.string());
            LOG.fine(() -> "Root @jdt.path: " + pathStr.string());
        }

        // Parse all other members
        for (final var entry : members.entrySet()) {
            final String key = entry.getKey();
            final JsonValue value = entry.getValue();

            // Skip the @jdt.path we already processed
            if (key.equals(Directive.PATH.key())) {
                continue;
            }

            // Parse as a property transform
            final TransformNode node = parseTransformNode(key, value);
            nodes.add(node);
        }

        LOG.fine(() -> "Parsed " + nodes.size() + " transform nodes at root");
        return new TransformRoot(nodes, pathSelector);
    }

    /// Parses a transform node (key-value pair from the transform object).
    private static TransformNode parseTransformNode(String key, JsonValue value) {
        LOG.finer(() -> "Parsing transform node: " + key);

        // If the value is not an object, it's an implicit @jdt.value
        if (!(value instanceof JsonObject obj)) {
            return new PropertyTransform(key, new ValueOp(value));
        }

        // Check if this object contains directives
        final TransformOperation operation = parseTransformObject(obj);
        return new PropertyTransform(key, operation);
    }

    /// Parses a transform object that may contain directives or nested properties.
    private static TransformOperation parseTransformObject(JsonObject obj) {
        final Map<String, JsonValue> members = obj.members();
        final List<TransformOperation> operations = new ArrayList<>();
        final List<TransformNode> nestedNodes = new ArrayList<>();

        // First pass: collect all directives
        for (final var entry : members.entrySet()) {
            final String key = entry.getKey();
            final JsonValue value = entry.getValue();

            final Directive directive = Directive.fromKey(key);
            if (directive != null) {
                final TransformOperation op = parseDirective(directive, value, obj);
                if (op != null) {
                    operations.add(op);
                }
            } else if (Directive.isDirective(key)) {
                // Unknown directive
                throw new JsonTransformsParseException("Unknown transform directive: " + key);
            } else {
                // Regular property - will be treated as nested transform
                final TransformNode node = parseTransformNode(key, value);
                nestedNodes.add(node);
            }
        }

        // If we have both directives and nested nodes, combine them
        if (!nestedNodes.isEmpty()) {
            operations.add(new NestedTransform(nestedNodes));
        }

        // Return the appropriate operation type
        if (operations.isEmpty()) {
            // No directives, treat as a value set
            return new ValueOp(obj);
        } else if (operations.size() == 1) {
            return operations.getFirst();
        } else {
            return new CompoundOp(operations);
        }
    }

    /// Parses a single directive.
    private static TransformOperation parseDirective(Directive directive, JsonValue value, JsonObject context) {
        LOG.finer(() -> "Parsing directive: " + directive.key());

        return switch (directive) {
            case PATH -> {
                // @jdt.path at nested level creates a PathTransform
                if (!(value instanceof JsonString pathStr)) {
                    throw new JsonTransformsParseException(
                        "@jdt.path must be a string, got: " + value.getClass().getSimpleName());
                }
                final JsonPath path = parseJsonPath(pathStr.string());
                
                // Collect the other operations in this object for the path transform
                final List<TransformNode> pathNodes = new ArrayList<>();
                for (final var entry : context.members().entrySet()) {
                    if (!entry.getKey().equals(Directive.PATH.key())) {
                        pathNodes.add(parseTransformNode(entry.getKey(), entry.getValue()));
                    }
                }
                
                // PathTransform is special - it should be the only operation
                // We return null here and handle it specially in parseTransformObject
                yield null; // Path is handled at a higher level
            }

            case VALUE -> new ValueOp(value);

            case REMOVE -> {
                if (!(value instanceof JsonBoolean bool)) {
                    throw new JsonTransformsParseException(
                        "@jdt.remove must be a boolean, got: " + value.getClass().getSimpleName());
                }
                yield new RemoveOp(bool.bool());
            }

            case RENAME -> {
                if (!(value instanceof JsonString str)) {
                    throw new JsonTransformsParseException(
                        "@jdt.rename must be a string, got: " + value.getClass().getSimpleName());
                }
                yield new RenameOp(str.string());
            }

            case REPLACE -> new ReplaceOp(value);

            case MERGE -> {
                if (!(value instanceof JsonObject)) {
                    throw new JsonTransformsParseException(
                        "@jdt.merge must be an object, got: " + value.getClass().getSimpleName());
                }
                yield new MergeOp(value);
            }
        };
    }

    /// Parses a JsonPath expression with error handling.
    private static JsonPath parseJsonPath(String pathExpr) {
        try {
            return JsonPath.parse(pathExpr);
        } catch (JsonPathParseException e) {
            throw new JsonTransformsParseException(
                "Invalid JsonPath expression '" + pathExpr + "': " + e.getMessage(), e);
        }
    }
}
