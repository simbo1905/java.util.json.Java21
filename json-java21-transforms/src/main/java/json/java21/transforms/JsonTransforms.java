package json.java21.transforms;

import jdk.sandbox.java.util.json.*;
import json.java21.jsonpath.JsonPath;
import json.java21.transforms.JsonTransformsAst.*;

import java.util.*;
import java.util.logging.Logger;

/// JSON Transforms - applies transformation specifications to JSON documents.
///
/// Based on the Microsoft JSON Document Transforms specification:
/// https://github.com/Microsoft/json-document-transforms/wiki
///
/// Usage:
/// ```java
/// JsonValue source = Json.parse("{\"name\": \"Alice\", \"age\": 30}");
/// JsonValue transform = Json.parse("{\"name\": {\"@jdt.rename\": \"fullName\"}}");
///
/// JsonTransforms transformer = JsonTransforms.parse(transform);
/// JsonValue result = transformer.apply(source);
/// ```
///
/// The transform specification supports these operations:
/// - `@jdt.path`: JsonPath selector for targeting specific nodes
/// - `@jdt.value`: Set or create a property value
/// - `@jdt.remove`: Remove a property
/// - `@jdt.rename`: Rename a property
/// - `@jdt.replace`: Replace a property value (only if it exists)
/// - `@jdt.merge`: Deep merge an object into an existing value
public final class JsonTransforms {

    private static final Logger LOG = Logger.getLogger(JsonTransforms.class.getName());

    private final TransformRoot ast;

    private JsonTransforms(TransformRoot ast) {
        this.ast = ast;
    }

    /// Parses a JSON transform specification and returns a reusable transformer.
    /// @param transform the transform specification as a JsonValue
    /// @return a compiled JsonTransforms that can be applied to multiple documents
    /// @throws NullPointerException if transform is null
    /// @throws JsonTransformsParseException if the transform is invalid
    public static JsonTransforms parse(JsonValue transform) {
        Objects.requireNonNull(transform, "transform must not be null");
        LOG.fine(() -> "Parsing transform specification");
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        return new JsonTransforms(ast);
    }

    /// Parses a JSON transform specification from a string.
    /// @param transformJson the transform specification as a JSON string
    /// @return a compiled JsonTransforms that can be applied to multiple documents
    /// @throws NullPointerException if transformJson is null
    /// @throws JsonParseException if the JSON is malformed
    /// @throws JsonTransformsParseException if the transform is invalid
    public static JsonTransforms parse(String transformJson) {
        Objects.requireNonNull(transformJson, "transformJson must not be null");
        return parse(Json.parse(transformJson));
    }

    /// Applies this transform to a source JSON document.
    /// @param source the source JSON document to transform
    /// @return the transformed JSON document
    /// @throws NullPointerException if source is null
    public JsonValue apply(JsonValue source) {
        Objects.requireNonNull(source, "source must not be null");
        LOG.fine(() -> "Applying transform to source document");

        // If there's a root path selector, apply transforms only to matching nodes
        if (ast.pathSelector() != null) {
            return applyWithPathSelector(source);
        }

        // Apply transforms to the root object
        return applyTransformNodes(source, ast.nodes());
    }

    /// Applies transforms with a root-level path selector.
    private JsonValue applyWithPathSelector(JsonValue source) {
        final JsonPath path = ast.pathSelector();
        LOG.fine(() -> "Applying transform with path selector: " + path);

        // For now, path selector at root means we transform matching elements
        // This is a simplified implementation - full implementation would need
        // to track paths and modify in-place
        return applyTransformNodes(source, ast.nodes());
    }

    /// Applies a list of transform nodes to a source value.
    private JsonValue applyTransformNodes(JsonValue source, List<TransformNode> nodes) {
        if (!(source instanceof JsonObject sourceObj)) {
            LOG.fine(() -> "Source is not an object, returning as-is");
            return source;
        }

        // Build a mutable map of the source object
        final Map<String, JsonValue> result = new LinkedHashMap<>(sourceObj.members());

        // Apply each transform node
        for (final TransformNode node : nodes) {
            applyTransformNode(result, node);
        }

        return JsonObject.of(result);
    }

    /// Applies a single transform node to the result map.
    private void applyTransformNode(Map<String, JsonValue> result, TransformNode node) {
        switch (node) {
            case PropertyTransform pt -> applyPropertyTransform(result, pt);
            case PathTransform pathTransform -> applyPathTransform(result, pathTransform);
        }
    }

    /// Applies a property transform to the result map.
    private void applyPropertyTransform(Map<String, JsonValue> result, PropertyTransform pt) {
        final String key = pt.key();
        final TransformOperation operation = pt.operation();

        LOG.finer(() -> "Applying transform to property: " + key);

        switch (operation) {
            case ValueOp valueOp -> {
                // Set the value (create or overwrite)
                result.put(key, valueOp.value());
                LOG.finer(() -> "Set value for: " + key);
            }

            case RemoveOp removeOp -> {
                if (removeOp.remove()) {
                    result.remove(key);
                    LOG.finer(() -> "Removed property: " + key);
                }
            }

            case RenameOp renameOp -> {
                if (result.containsKey(key)) {
                    final JsonValue value = result.remove(key);
                    result.put(renameOp.newName(), value);
                    LOG.finer(() -> "Renamed " + key + " to " + renameOp.newName());
                }
            }

            case ReplaceOp replaceOp -> {
                if (result.containsKey(key)) {
                    result.put(key, replaceOp.value());
                    LOG.finer(() -> "Replaced value for: " + key);
                }
            }

            case MergeOp mergeOp -> {
                final JsonValue existing = result.get(key);
                final JsonValue merged = deepMerge(existing, mergeOp.mergeValue());
                result.put(key, merged);
                LOG.finer(() -> "Merged value for: " + key);
            }

            case NestedTransform nested -> {
                // Apply nested transforms to the property value
                final JsonValue existing = result.get(key);
                if (existing != null) {
                    final JsonValue transformed = applyTransformNodes(existing, nested.children());
                    result.put(key, transformed);
                    LOG.finer(() -> "Applied nested transform to: " + key);
                }
            }

            case CompoundOp compound -> {
                // Apply each operation in sequence
                for (final TransformOperation op : compound.operations()) {
                    applyPropertyTransform(result, new PropertyTransform(key, op));
                }
            }
        }
    }

    /// Applies a path transform to the result map.
    private void applyPathTransform(Map<String, JsonValue> result, PathTransform pathTransform) {
        LOG.finer(() -> "Applying path transform: " + pathTransform.path());
        
        // Convert the current result to a JsonObject for path evaluation
        final JsonObject currentObj = JsonObject.of(result);
        final List<JsonValue> matches = pathTransform.path().query(currentObj);
        
        LOG.finer(() -> "Path matched " + matches.size() + " nodes");
        
        // For each match, apply the nested transforms
        // Note: This is a simplified implementation. Full implementation would need
        // to track paths and modify the result in-place at the matched locations.
        for (final TransformNode node : pathTransform.nodes()) {
            applyTransformNode(result, node);
        }
    }

    /// Deep merges two JSON values.
    /// If both are objects, recursively merge their properties.
    /// Otherwise, the merge value takes precedence.
    private JsonValue deepMerge(JsonValue base, JsonValue merge) {
        if (base == null) {
            return merge;
        }

        if (base instanceof JsonObject baseObj && merge instanceof JsonObject mergeObj) {
            final Map<String, JsonValue> result = new LinkedHashMap<>(baseObj.members());
            
            for (final var entry : mergeObj.members().entrySet()) {
                final String key = entry.getKey();
                final JsonValue mergeValue = entry.getValue();
                final JsonValue baseValue = result.get(key);
                
                result.put(key, deepMerge(baseValue, mergeValue));
            }
            
            return JsonObject.of(result);
        }

        // For arrays and primitives, merge value wins
        return merge;
    }

    /// Returns a string representation of this transform.
    @Override
    public String toString() {
        return "JsonTransforms[nodes=" + ast.nodes().size() + 
               ", pathSelector=" + (ast.pathSelector() != null) + "]";
    }
}
