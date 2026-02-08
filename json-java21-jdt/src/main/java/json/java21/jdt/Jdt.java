package json.java21.jdt;

import jdk.sandbox.java.util.json.*;
import json.java21.jsonpath.JsonPath;

import java.util.*;
import java.util.logging.Logger;

/// JSON Document Transforms (JDT) engine.
///
/// JDT transforms a source JSON document using a transform specification document.
/// The transform document mirrors the structure of the source document, with special
/// `@jdt.*` directives to control the transformation.
///
/// ## Default Behavior (no directives)
/// - **Primitives**: replaced with the transform's value
/// - **Objects**: recursively merged - existing keys updated, new keys added, missing keys unchanged
/// - **Arrays**: appended to (not replaced)
///
/// ## Transform Verbs
/// - `@jdt.rename`: Renames keys without altering contents
/// - `@jdt.remove`: Removes keys from the current node
/// - `@jdt.merge`: Explicitly deep-merges an object (same as default but stated explicitly)
/// - `@jdt.replace`: Wholesale replaces the current node
///
/// ## Execution Order
/// Within a node: **Rename -> Remove -> Merge -> Replace**
///
/// ## Path Attribute
/// Use `@jdt.path` with JSONPath syntax to target specific nodes within arrays.
///
/// Usage:
/// ```java
/// JsonValue source = Json.parse(sourceJson);
/// JsonValue transform = Json.parse(transformJson);
/// JsonValue result = Jdt.transform(source, transform);
/// ```
///
/// @see <a href="https://github.com/AzureAD/microsoft-json-document-transforms">Microsoft JDT Spec</a>
public final class Jdt {

    private static final Logger LOG = Logger.getLogger(Jdt.class.getName());

    /// JDT directive prefix
    static final String JDT_PREFIX = "@jdt.";

    /// JDT verbs
    static final String JDT_RENAME = "@jdt.rename";
    static final String JDT_REMOVE = "@jdt.remove";
    static final String JDT_MERGE = "@jdt.merge";
    static final String JDT_REPLACE = "@jdt.replace";

    /// JDT attributes
    static final String JDT_PATH = "@jdt.path";
    static final String JDT_VALUE = "@jdt.value";

    private Jdt() {
        // Static utility class
    }

    /// Transforms a source JSON document using the given transform specification.
    ///
    /// @param source the source JSON document to transform
    /// @param transform the transform specification document
    /// @return the transformed JSON document
    /// @throws NullPointerException if source or transform is null
    /// @throws JdtException if the transform specification is invalid
    public static JsonValue transform(JsonValue source, JsonValue transform) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(transform, "transform must not be null");

        LOG.fine(() -> "Transforming source with transform specification");
        LOG.finer(() -> "Source: " + Json.toDisplayString(source, 2));
        LOG.finer(() -> "Transform: " + Json.toDisplayString(transform, 2));

        final var result = applyTransform(source, transform);
        LOG.finer(() -> "Result: " + Json.toDisplayString(result, 2));
        return result;
    }

    /// Applies a transform to a source value.
    static JsonValue applyTransform(JsonValue source, JsonValue transform) {
        // If transform is not an object, it's a direct replacement (default behavior for primitives)
        if (!(transform instanceof JsonObject transformObj)) {
            return transform;
        }

        // Check if this transform object contains JDT directives
        final var hasJdtDirectives = transformObj.members().keySet().stream()
                .anyMatch(k -> k.startsWith(JDT_PREFIX));

        if (hasJdtDirectives) {
            return applyJdtDirectives(source, transformObj);
        }

        // Default behavior: merge objects, replace primitives
        if (source instanceof JsonObject sourceObj) {
            return mergeObjects(sourceObj, transformObj);
        } else if (source instanceof JsonArray sourceArr) {
            // Arrays are appended by default when transform is an object targeting the array
            // But if transform is just an object with keys, it replaces the array
            return transformObj;
        } else {
            // Primitive source replaced by object transform
            return transformObj;
        }
    }

    /// Applies JDT directives from a transform object.
    private static JsonValue applyJdtDirectives(JsonValue source, JsonObject transformObj) {
        final var members = transformObj.members();
        var result = source;

        // Execution order: Rename -> Remove -> Merge -> Replace
        // But also process non-JDT keys as default merge operations

        // 1. Rename
        if (members.containsKey(JDT_RENAME)) {
            result = applyRename(result, members.get(JDT_RENAME));
        }

        // 2. Remove
        if (members.containsKey(JDT_REMOVE)) {
            result = applyRemove(result, members.get(JDT_REMOVE));
        }

        // 3. Merge (explicit)
        if (members.containsKey(JDT_MERGE)) {
            result = applyMerge(result, members.get(JDT_MERGE));
        }

        // 4. Replace
        if (members.containsKey(JDT_REPLACE)) {
            result = applyReplace(result, members.get(JDT_REPLACE));
        }

        // Also process non-JDT keys as recursive transforms (default merge behavior)
        for (final var entry : members.entrySet()) {
            final var key = entry.getKey();
            if (!key.startsWith(JDT_PREFIX) && result instanceof JsonObject resultObj) {
                final var sourceValue = resultObj.members().get(key);
                final var transformValue = entry.getValue();

                if (sourceValue != null) {
                    // Recurse into existing key
                    final var newValue = applyTransform(sourceValue, transformValue);
                    result = setObjectKey(resultObj, key, newValue);
                } else {
                    // Add new key
                    result = setObjectKey(resultObj, key, transformValue);
                }
            }
        }

        return result;
    }

    /// Applies the @jdt.rename directive.
    static JsonValue applyRename(JsonValue source, JsonValue renameSpec) {
        if (!(source instanceof JsonObject sourceObj)) {
            LOG.fine(() -> "Cannot rename on non-object: " + source.getClass().getSimpleName());
            return source;
        }

        // renameSpec can be:
        // - Object: { "oldName": "newName", ... }
        // - Array: [{ "oldName": "newName" }, ...]
        // - Object with @jdt.path: { "@jdt.path": "...", "@jdt.value": "newName" }

        if (renameSpec instanceof JsonObject renameObj) {
            if (renameObj.members().containsKey(JDT_PATH)) {
                return applyRenameWithPath(sourceObj, renameObj);
            }
            return applyRenameMapping(sourceObj, renameObj);
        } else if (renameSpec instanceof JsonArray renameArr) {
            var result = sourceObj;
            for (final var item : renameArr.elements()) {
                if (item instanceof JsonObject itemObj) {
                    result = (JsonObject) applyRename(result, itemObj);
                }
            }
            return result;
        }

        throw new JdtException("@jdt.rename value must be an object or array, got: " + 
                renameSpec.getClass().getSimpleName());
    }

    private static JsonObject applyRenameMapping(JsonObject source, JsonObject renameMap) {
        final var result = new LinkedHashMap<>(source.members());

        for (final var entry : renameMap.members().entrySet()) {
            final var oldName = entry.getKey();
            if (oldName.startsWith(JDT_PREFIX)) continue;

            if (!(entry.getValue() instanceof JsonString newNameStr)) {
                throw new JdtException("@jdt.rename value for key '" + oldName + 
                        "' must be a string, got: " + entry.getValue().getClass().getSimpleName());
            }

            final var newName = newNameStr.string();
            if (result.containsKey(oldName)) {
                final var value = result.remove(oldName);
                result.put(newName, value);
                LOG.finer(() -> "Renamed key '" + oldName + "' to '" + newName + "'");
            }
        }

        return JsonObject.of(result);
    }

    private static JsonObject applyRenameWithPath(JsonObject source, JsonObject renameObj) {
        final var pathValue = renameObj.members().get(JDT_PATH);
        final var valueValue = renameObj.members().get(JDT_VALUE);

        if (!(pathValue instanceof JsonString pathStr)) {
            throw new JdtException("@jdt.path must be a string");
        }
        if (!(valueValue instanceof JsonString newNameStr)) {
            throw new JdtException("@jdt.value for rename must be a string");
        }

        final var path = pathStr.string();
        final var newName = newNameStr.string();

        // Use JsonPath to find matching nodes and rename them
        final var jsonPath = JsonPath.parse(path);
        final var matches = jsonPath.query(source);

        // For rename with path, we need to find the parent and rename the key
        // This is complex - for now, handle simple cases
        LOG.fine(() -> "Rename with path '" + path + "' to '" + newName + "' - " + matches.size() + " matches");

        // TODO: Implement path-based rename properly
        return source;
    }

    /// Applies the @jdt.remove directive.
    static JsonValue applyRemove(JsonValue source, JsonValue removeSpec) {
        if (!(source instanceof JsonObject sourceObj)) {
            LOG.fine(() -> "Cannot remove from non-object: " + source.getClass().getSimpleName());
            return source;
        }

        // removeSpec can be:
        // - String: single key name to remove
        // - Boolean true: remove all keys (set to null)
        // - Array of strings: multiple keys to remove
        // - Object with @jdt.path: path-based removal

        if (removeSpec instanceof JsonString removeStr) {
            return removeKey(sourceObj, removeStr.string());
        } else if (removeSpec instanceof JsonBoolean removeBool) {
            if (removeBool.bool()) {
                LOG.finer(() -> "Removing all keys from object");
                return JsonNull.of();
            }
            return source;
        } else if (removeSpec instanceof JsonArray removeArr) {
            var result = sourceObj;
            for (final var item : removeArr.elements()) {
                if (item instanceof JsonString itemStr) {
                    result = removeKey(result, itemStr.string());
                } else if (item instanceof JsonObject itemObj) {
                    result = (JsonObject) applyRemove(result, itemObj);
                } else {
                    throw new JdtException("@jdt.remove array elements must be strings or objects, got: " + 
                            item.getClass().getSimpleName());
                }
            }
            return result;
        } else if (removeSpec instanceof JsonObject removeObj) {
            if (removeObj.members().containsKey(JDT_PATH)) {
                return applyRemoveWithPath(sourceObj, removeObj);
            }
            throw new JdtException("@jdt.remove object must contain @jdt.path");
        }

        throw new JdtException("@jdt.remove value must be string, boolean, array, or object with @jdt.path, got: " + 
                removeSpec.getClass().getSimpleName());
    }

    private static JsonObject removeKey(JsonObject source, String key) {
        final var result = new LinkedHashMap<>(source.members());
        if (result.remove(key) != null) {
            LOG.finer(() -> "Removed key '" + key + "'");
        }
        return JsonObject.of(result);
    }

    private static JsonObject applyRemoveWithPath(JsonObject source, JsonObject removeObj) {
        final var pathValue = removeObj.members().get(JDT_PATH);

        if (!(pathValue instanceof JsonString pathStr)) {
            throw new JdtException("@jdt.path must be a string");
        }

        final var path = pathStr.string();
        final var jsonPath = JsonPath.parse(path);
        final var matches = jsonPath.query(source);

        LOG.fine(() -> "Remove with path '" + path + "' - " + matches.size() + " matches");

        // Remove matched nodes from source
        // This requires traversing and rebuilding the object
        return removeMatchedNodes(source, matches);
    }

    private static JsonObject removeMatchedNodes(JsonObject source, List<JsonValue> toRemove) {
        final var result = new LinkedHashMap<String, JsonValue>();

        for (final var entry : source.members().entrySet()) {
            final var value = entry.getValue();
            if (!toRemove.contains(value)) {
                if (value instanceof JsonObject childObj) {
                    result.put(entry.getKey(), removeMatchedNodes(childObj, toRemove));
                } else if (value instanceof JsonArray childArr) {
                    result.put(entry.getKey(), removeMatchedNodesFromArray(childArr, toRemove));
                } else {
                    result.put(entry.getKey(), value);
                }
            } else {
                LOG.finer(() -> "Removed matched node at key '" + entry.getKey() + "'");
            }
        }

        return JsonObject.of(result);
    }

    private static JsonArray removeMatchedNodesFromArray(JsonArray source, List<JsonValue> toRemove) {
        final var result = new ArrayList<JsonValue>();

        for (final var element : source.elements()) {
            if (!toRemove.contains(element)) {
                if (element instanceof JsonObject childObj) {
                    result.add(removeMatchedNodes(childObj, toRemove));
                } else if (element instanceof JsonArray childArr) {
                    result.add(removeMatchedNodesFromArray(childArr, toRemove));
                } else {
                    result.add(element);
                }
            }
        }

        return JsonArray.of(result);
    }

    /// Applies the @jdt.merge directive.
    static JsonValue applyMerge(JsonValue source, JsonValue mergeSpec) {
        // mergeSpec can be:
        // - Object: merge into source
        // - Array: apply each merge in sequence, or append if double-bracketed
        // - Object with @jdt.path: path-based merge

        if (mergeSpec instanceof JsonObject mergeObj) {
            if (mergeObj.members().containsKey(JDT_PATH)) {
                return applyMergeWithPath(source, mergeObj);
            }
            if (source instanceof JsonObject sourceObj) {
                return mergeObjects(sourceObj, mergeObj);
            }
            // Non-object source gets replaced
            return mergeObj;
        } else if (mergeSpec instanceof JsonArray mergeArr) {
            // Check for double-bracket array (explicit array value)
            if (isDoubleBracketArray(mergeArr)) {
                return mergeArr.elements().getFirst();
            }
            // Apply each merge operation
            var result = source;
            for (final var item : mergeArr.elements()) {
                result = applyMerge(result, item);
            }
            return result;
        } else {
            // Primitive merge replaces the value
            return mergeSpec;
        }
    }

    private static JsonValue applyMergeWithPath(JsonValue source, JsonObject mergeObj) {
        final var pathValue = mergeObj.members().get(JDT_PATH);
        final var valueValue = mergeObj.members().get(JDT_VALUE);

        if (!(pathValue instanceof JsonString pathStr)) {
            throw new JdtException("@jdt.path must be a string");
        }

        final var path = pathStr.string();
        final var jsonPath = JsonPath.parse(path);
        final var matches = jsonPath.query(source);

        LOG.fine(() -> "Merge with path '" + path + "' - " + matches.size() + " matches");

        if (valueValue == null) {
            throw new JdtException("@jdt.merge with @jdt.path requires @jdt.value");
        }

        // Apply merge to each matched node
        return applyToMatches(source, matches, match -> {
            if (match instanceof JsonObject matchObj && valueValue instanceof JsonObject valueObj) {
                return mergeObjects(matchObj, valueObj);
            }
            return valueValue;
        });
    }

    /// Applies the @jdt.replace directive.
    static JsonValue applyReplace(JsonValue source, JsonValue replaceSpec) {
        // replaceSpec can be:
        // - Any value: replace source with this value
        // - Object with @jdt.path: path-based replacement
        // - Array: if double-bracketed, use inner array as replacement value

        if (replaceSpec instanceof JsonObject replaceObj) {
            if (replaceObj.members().containsKey(JDT_PATH)) {
                return applyReplaceWithPath(source, replaceObj);
            }
            // Plain object replaces the source
            return replaceObj;
        } else if (replaceSpec instanceof JsonArray replaceArr) {
            // Check for double-bracket array (explicit array value)
            if (isDoubleBracketArray(replaceArr)) {
                return replaceArr.elements().getFirst();
            }
            // Single array replaces directly
            return replaceArr;
        } else {
            // Primitive replacement
            return replaceSpec;
        }
    }

    private static JsonValue applyReplaceWithPath(JsonValue source, JsonObject replaceObj) {
        final var pathValue = replaceObj.members().get(JDT_PATH);
        final var valueValue = replaceObj.members().get(JDT_VALUE);

        if (!(pathValue instanceof JsonString pathStr)) {
            throw new JdtException("@jdt.path must be a string");
        }

        final var path = pathStr.string();
        final var jsonPath = JsonPath.parse(path);
        final var matches = jsonPath.query(source);

        LOG.fine(() -> "Replace with path '" + path + "' - " + matches.size() + " matches");

        if (valueValue == null) {
            throw new JdtException("@jdt.replace with @jdt.path requires @jdt.value");
        }

        // Replace each matched node with the value
        return applyToMatches(source, matches, match -> valueValue);
    }

    /// Merges two objects, with transform values overriding source values.
    static JsonObject mergeObjects(JsonObject source, JsonObject transform) {
        final var result = new LinkedHashMap<>(source.members());

        for (final var entry : transform.members().entrySet()) {
            final var key = entry.getKey();
            if (key.startsWith(JDT_PREFIX)) continue;

            final var transformValue = entry.getValue();
            final var sourceValue = result.get(key);

            if (sourceValue == null) {
                // New key - but check if transform has JDT directives that need processing
                if (transformValue instanceof JsonObject transformObj && 
                        transformObj.members().keySet().stream().anyMatch(k -> k.startsWith(JDT_PREFIX))) {
                    // JDT directives on a new key - apply them to create the new value
                    result.put(key, applyTransform(JsonObject.of(Map.of()), transformObj));
                } else {
                    result.put(key, transformValue);
                }
            } else if (sourceValue instanceof JsonObject && transformValue instanceof JsonObject transformObj) {
                // Recursive transform - use applyTransform to handle JDT directives
                result.put(key, applyTransform(sourceValue, transformObj));
            } else if (sourceValue instanceof JsonArray sourceArr && transformValue instanceof JsonArray transformArr) {
                // Append arrays
                result.put(key, appendArrays(sourceArr, transformArr));
            } else {
                // Replace value
                result.put(key, transformValue);
            }
        }

        return JsonObject.of(result);
    }

    /// Appends two arrays.
    static JsonArray appendArrays(JsonArray source, JsonArray toAppend) {
        final var result = new ArrayList<>(source.elements());
        result.addAll(toAppend.elements());
        return JsonArray.of(result);
    }

    /// Checks if an array is double-bracketed (array containing a single array).
    private static boolean isDoubleBracketArray(JsonArray arr) {
        final var elements = arr.elements();
        return elements.size() == 1 && elements.getFirst() instanceof JsonArray;
    }

    /// Sets a key in an object, returning a new object.
    private static JsonObject setObjectKey(JsonObject source, String key, JsonValue value) {
        final var result = new LinkedHashMap<>(source.members());
        result.put(key, value);
        return JsonObject.of(result);
    }

    /// Applies a transformation function to all nodes matching the given matches.
    private static JsonValue applyToMatches(JsonValue source, List<JsonValue> matches, 
            java.util.function.Function<JsonValue, JsonValue> transformer) {
        if (matches.isEmpty()) {
            return source;
        }

        return transformMatchingNodes(source, matches, transformer);
    }

    private static JsonValue transformMatchingNodes(JsonValue node, List<JsonValue> matches,
            java.util.function.Function<JsonValue, JsonValue> transformer) {
        
        // Check if this node is a match
        for (final var match : matches) {
            if (node == match) {
                return transformer.apply(node);
            }
        }

        // Recurse into children
        if (node instanceof JsonObject obj) {
            final var result = new LinkedHashMap<String, JsonValue>();
            for (final var entry : obj.members().entrySet()) {
                result.put(entry.getKey(), transformMatchingNodes(entry.getValue(), matches, transformer));
            }
            return JsonObject.of(result);
        } else if (node instanceof JsonArray arr) {
            final var result = new ArrayList<JsonValue>();
            for (final var element : arr.elements()) {
                result.add(transformMatchingNodes(element, matches, transformer));
            }
            return JsonArray.of(result);
        }

        return node;
    }
}
