package json.java21.transforms;

import json.java21.jsonpath.JsonPath;
import jdk.sandbox.java.util.json.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static json.java21.transforms.TransformSyntax.*;

final class TransformCompiler {

    private TransformCompiler() {}

    static TransformAst.ObjectTransform compileObject(JsonObject transformObject) {
        Objects.requireNonNull(transformObject, "transformObject must not be null");

        validateVerbsAtObjectLevel(transformObject);

        final var nonVerbMembers = new LinkedHashMap<String, JsonValue>();
        final var childObjects = new LinkedHashMap<String, TransformAst.ObjectTransform>();

        final var removes = new ArrayList<TransformAst.RemoveOp>();
        final var replaces = new ArrayList<TransformAst.ReplaceOp>();
        final var merges = new ArrayList<TransformAst.MergeOp>();
        final var renames = new ArrayList<TransformAst.RenameOp>();

        for (final var entry : transformObject.members().entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();

            if (VERB_REMOVE.equals(key)) {
                removes.addAll(compileRemove(value));
                continue;
            }
            if (VERB_REPLACE.equals(key)) {
                replaces.addAll(compileReplace(value));
                continue;
            }
            if (VERB_MERGE.equals(key)) {
                merges.addAll(compileMerge(value));
                continue;
            }
            if (VERB_RENAME.equals(key)) {
                renames.addAll(compileRename(value));
                continue;
            }

            if (isSyntaxKey(key)) {
                // Unknown @jdt.* key at object level: invalid verb
                throw new JsonTransformException("invalid transform verb: '" + key + "'");
            }

            nonVerbMembers.put(key, value);
            if (value instanceof JsonObject childObj) {
                childObjects.put(key, compileObject(childObj));
            }
        }

        return new TransformAst.ObjectTransform(nonVerbMembers, childObjects, removes, replaces, merges, renames);
    }

    private static void validateVerbsAtObjectLevel(JsonObject transformObject) {
        for (final var key : transformObject.members().keySet()) {
            if (!isSyntaxKey(key)) continue;
            if (VERB_REMOVE.equals(key) || VERB_REPLACE.equals(key) || VERB_MERGE.equals(key) || VERB_RENAME.equals(key)) {
                continue;
            }
            throw new JsonTransformException("invalid transform verb: '" + key + "'");
        }
    }

    private static List<TransformAst.RemoveOp> compileRemove(JsonValue value) {
        return switch (value) {
            case JsonArray arr -> {
                final var ops = new ArrayList<TransformAst.RemoveOp>();
                for (final var element : arr.elements()) {
                    if (element instanceof JsonArray) {
                        throw new JsonTransformException("invalid @jdt.remove value: array");
                    }
                    ops.addAll(compileRemove(element));
                }
                yield ops;
            }
            case JsonString s -> List.of(new TransformAst.RemoveOp.ByName(s.string()));
            case JsonBoolean b -> b.bool() ? List.of(new TransformAst.RemoveOp.RemoveThis()) : List.of();
            case JsonObject obj -> List.of(compileRemoveWithAttributes(obj));
            default -> throw new JsonTransformException("invalid @jdt.remove value: " + value.getClass().getSimpleName());
        };
    }

    private static TransformAst.RemoveOp compileRemoveWithAttributes(JsonObject obj) {
        final var attrs = parseAttributeObject(obj, Set.of(ATTR_PATH));
        final var pathVal = attrs.attributes().get(ATTR_PATH);
        if (!(pathVal instanceof JsonString s)) {
            throw new JsonTransformException("@jdt.path must be a string");
        }
        final var rawPath = s.string();
        final var normalized = normalizePathString(rawPath);
        final var path = JsonPath.parse(normalized);
        return new TransformAst.RemoveOp.ByPath(rawPath, path);
    }

    private static List<TransformAst.ReplaceOp> compileReplace(JsonValue value) {
        if (value instanceof JsonArray arr) {
            final var ops = new ArrayList<TransformAst.ReplaceOp>();
            for (final var element : arr.elements()) {
                ops.add(compileReplaceElement(element));
            }
            return ops;
        }
        return List.of(compileReplaceElement(value));
    }

    private static TransformAst.ReplaceOp compileReplaceElement(JsonValue value) {
        if (value instanceof JsonObject obj) {
            final var attrs = parseAttributeObjectOrEmpty(obj, Set.of(ATTR_PATH, ATTR_VALUE));
            if (attrs.isPresent()) {
                final var pathVal = attrs.attributes().get(ATTR_PATH);
                final var valueVal = attrs.attributes().get(ATTR_VALUE);
                if (!(pathVal instanceof JsonString s)) {
                    throw new JsonTransformException("@jdt.path must be a string");
                }
                final var rawPath = s.string();
                final var normalized = normalizePathString(rawPath);
                final var path = JsonPath.parse(normalized);
                return new TransformAst.ReplaceOp.ByPath(rawPath, path, valueVal);
            }
        }
        return new TransformAst.ReplaceOp.ReplaceThis(value);
    }

    private static List<TransformAst.MergeOp> compileMerge(JsonValue value) {
        if (value instanceof JsonArray arr) {
            final var ops = new ArrayList<TransformAst.MergeOp>();
            for (final var element : arr.elements()) {
                ops.add(compileMergeElement(element));
            }
            return ops;
        }
        return List.of(compileMergeElement(value));
    }

    private static TransformAst.MergeOp compileMergeElement(JsonValue value) {
        if (value instanceof JsonObject obj) {
            final var attrs = parseAttributeObjectOrEmpty(obj, Set.of(ATTR_PATH, ATTR_VALUE));
            if (attrs.isPresent()) {
                final var pathVal = attrs.attributes().get(ATTR_PATH);
                final var valueVal = attrs.attributes().get(ATTR_VALUE);
                if (!(pathVal instanceof JsonString s)) {
                    throw new JsonTransformException("@jdt.path must be a string");
                }
                final var rawPath = s.string();
                final var normalized = normalizePathString(rawPath);
                final var path = JsonPath.parse(normalized);
                return new TransformAst.MergeOp.ByPath(rawPath, path, compileMergeValue(valueVal));
            }
            return new TransformAst.MergeOp.MergeThis(new TransformAst.MergeOp.Value.TransformObjectValue(obj, compileObject(obj)));
        }
        return new TransformAst.MergeOp.MergeThis(new TransformAst.MergeOp.Value.Raw(value));
    }

    private static TransformAst.MergeOp.Value compileMergeValue(JsonValue value) {
        if (value instanceof JsonObject obj) {
            return new TransformAst.MergeOp.Value.TransformObjectValue(obj, compileObject(obj));
        }
        return new TransformAst.MergeOp.Value.Raw(value);
    }

    private static List<TransformAst.RenameOp> compileRename(JsonValue value) {
        if (value instanceof JsonArray arr) {
            final var ops = new ArrayList<TransformAst.RenameOp>();
            for (final var element : arr.elements()) {
                if (element instanceof JsonArray) {
                    throw new JsonTransformException("invalid @jdt.rename value: array");
                }
                ops.addAll(compileRename(element));
            }
            return ops;
        }
        return List.of(compileRenameElement(value));
    }

    private static TransformAst.RenameOp compileRenameElement(JsonValue value) {
        if (!(value instanceof JsonObject obj)) {
            throw new JsonTransformException("invalid @jdt.rename value: " + value.getClass().getSimpleName());
        }

        final var attrs = parseAttributeObjectOrEmpty(obj, Set.of(ATTR_PATH, ATTR_VALUE));
        if (attrs.isPresent()) {
            final var pathVal = attrs.attributes().get(ATTR_PATH);
            final var valueVal = attrs.attributes().get(ATTR_VALUE);
            if (!(pathVal instanceof JsonString s)) {
                throw new JsonTransformException("@jdt.path must be a string");
            }
            if (!(valueVal instanceof JsonString v)) {
                throw new JsonTransformException("@jdt.value must be a string for @jdt.rename");
            }
            final var rawPath = s.string();
            final var normalized = normalizePathString(rawPath);
            final var path = JsonPath.parse(normalized);
            return new TransformAst.RenameOp.ByPath(rawPath, path, v.string());
        }

        // Direct mapping: { "Old": "New", ... }
        final var mapping = new LinkedHashMap<String, String>();
        for (final var entry : obj.members().entrySet()) {
            if (!(entry.getValue() instanceof JsonString s)) {
                throw new JsonTransformException("rename mapping values must be strings");
            }
            mapping.put(entry.getKey(), s.string());
        }
        return new TransformAst.RenameOp.Mapping(mapping);
    }

    private record AttrParseResult(Map<String, JsonValue> attributes, boolean isPresent) {}

    private static AttrParseResult parseAttributeObjectOrEmpty(JsonObject obj, Set<String> allowedAttributes) {
        // Detect whether this object is using attribute syntax at all.
        boolean hasAnyAttr = obj.members().containsKey(ATTR_PATH) || obj.members().containsKey(ATTR_VALUE);
        if (!hasAnyAttr) {
            // Still need to reject unknown @jdt.* keys (invalid attribute) to match the reference tests.
            for (final var key : obj.members().keySet()) {
                if (isSyntaxKey(key)) {
                    throw new JsonTransformException("invalid attribute: '" + key + "'");
                }
            }
            return new AttrParseResult(Map.of(), false);
        }
        final var parsed = parseAttributeObject(obj, allowedAttributes);
        return new AttrParseResult(parsed.attributes(), true);
    }

    private record ParsedAttributes(Map<String, JsonValue> attributes) {}

    private static ParsedAttributes parseAttributeObject(JsonObject obj, Set<String> allowedAttributes) {
        Objects.requireNonNull(obj, "obj must not be null");
        Objects.requireNonNull(allowedAttributes, "allowedAttributes must not be null");

        boolean hasNonAttr = false;
        final var attrs = new LinkedHashMap<String, JsonValue>();

        for (final var entry : obj.members().entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();

            if (isSyntaxKey(key)) {
                if (!allowedAttributes.contains(key)) {
                    throw new JsonTransformException("invalid attribute: '" + key + "'");
                }
                attrs.put(key, value);
            } else {
                hasNonAttr = true;
            }
        }

        if (hasNonAttr) {
            throw new JsonTransformException("attribute objects must contain only @jdt.* attributes");
        }

        if (allowedAttributes.contains(ATTR_PATH) && !attrs.containsKey(ATTR_PATH)) {
            // For all current uses, path is required when attributes are in play.
            throw new JsonTransformException("missing required attribute: @jdt.path");
        }

        if (allowedAttributes.contains(ATTR_VALUE) && !attrs.containsKey(ATTR_VALUE)) {
            throw new JsonTransformException("missing required attribute: @jdt.value");
        }

        // Some verbs allow only a subset; enforce here.
        if (allowedAttributes.equals(Set.of(ATTR_PATH)) && attrs.size() != 1) {
            throw new JsonTransformException("@jdt.remove only supports @jdt.path");
        }

        return new ParsedAttributes(attrs);
    }
}

