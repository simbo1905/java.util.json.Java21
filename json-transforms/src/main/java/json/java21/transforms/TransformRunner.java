package json.java21.transforms;

import json.java21.jsonpath.JsonPathMatch;
import json.java21.jsonpath.JsonPathLocationStep;
import jdk.sandbox.java.util.json.*;

import java.util.*;
import java.util.logging.Logger;

import static json.java21.transforms.TransformPatch.*;

final class TransformRunner {

    private static final Logger LOG = Logger.getLogger(TransformRunner.class.getName());

    private TransformRunner() {}

    static JsonValue applyAtDocumentRoot(JsonObject source, TransformAst.ObjectTransform transform) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(transform, "transform must not be null");
        final var applied = processTransform(source, transform, true);
        return applied.value();
    }

    private record Applied(JsonValue value, boolean halt) {
        Applied {
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    private static Applied processTransform(JsonObject source, TransformAst.ObjectTransform transform, boolean isDocumentRoot) {
        // Step 1: Recurse into non-verb objects that exist in both source + transform
        final var recursedKeys = new HashSet<String>();
        JsonObject current = source;

        for (final var entry : transform.childObjects().entrySet()) {
            final var key = entry.getKey();
            final var childTransform = entry.getValue();

            final var sourceChild = current.members().get(key);
            if (sourceChild instanceof JsonObject childObj) {
                final var appliedChild = processTransform(childObj, childTransform, false);
                current = (JsonObject) replaceAt(current, List.of(new JsonPathLocationStep.Property(key)), appliedChild.value());
                recursedKeys.add(key);
            }
        }

        // Step 2: Apply verbs in priority order: Remove > Replace > Merge > Default > Rename
        final var removed = applyRemoves(current, transform.removes(), isDocumentRoot);
        if (removed.halt()) return removed;
        current = (JsonObject) removed.value();

        final var replaced = applyReplaces(current, transform.replaces(), isDocumentRoot);
        if (replaced.halt()) return replaced;
        current = (JsonObject) replaced.value();

        final var merged = applyMerges(current, transform.merges(), isDocumentRoot);
        if (merged.halt()) return merged;
        if (!(merged.value() instanceof JsonObject mergedObj)) {
            // If merge replaced this node with a non-object, no further object transforms are meaningful.
            return merged;
        }
        current = mergedObj;

        current = applyDefault(current, transform.nonVerbMembers(), recursedKeys);

        current = applyRenames(current, transform.renames(), isDocumentRoot);

        return new Applied(current, false);
    }

    private static Applied applyRemoves(JsonObject source, List<TransformAst.RemoveOp> removes, boolean isDocumentRoot) {
        JsonValue current = source;

        for (final var op : removes) {
            switch (op) {
                case TransformAst.RemoveOp.ByName byName -> {
                    if (current instanceof JsonObject obj) {
                        if (!obj.members().containsKey(byName.name())) {
                            LOG.fine(() -> "Remove by name had no effect; missing key: " + byName.name());
                        } else {
                            final var out = new LinkedHashMap<String, JsonValue>(obj.members().size());
                            for (final var entry : obj.members().entrySet()) {
                                if (!entry.getKey().equals(byName.name())) out.put(entry.getKey(), entry.getValue());
                            }
                            current = JsonObject.of(out);
                        }
                    }
                }
                case TransformAst.RemoveOp.RemoveThis ignored -> {
                    if (isDocumentRoot) {
                        throw new JsonTransformException("cannot remove the document root");
                    }
                    return new Applied(JsonNull.of(), true);
                }
                case TransformAst.RemoveOp.ByPath byPath -> {
                    if (!(current instanceof JsonValue root)) {
                        break;
                    }
                    final var matches = byPath.path().queryMatches(root);
                    if (matches.isEmpty()) {
                        LOG.fine(() -> "Remove path produced no results: " + byPath.rawPath());
                        break;
                    }
                    // Apply removals; remove from arrays in descending index order for stability.
                    final var sorted = sortForRemoval(matches);
                    for (final var match : sorted) {
                        final var location = match.location();
                        if (location.isEmpty()) {
                            if (isDocumentRoot) {
                                throw new JsonTransformException("cannot remove the document root");
                            }
                            return new Applied(JsonNull.of(), true);
                        }
                        current = removeAt(current, location);
                    }
                }
            }
        }

        return new Applied(current, false);
    }

    private static Applied applyReplaces(JsonObject source, List<TransformAst.ReplaceOp> replaces, boolean isDocumentRoot) {
        JsonValue current = source;

        for (final var op : replaces) {
            switch (op) {
                case TransformAst.ReplaceOp.ReplaceThis rt -> {
                    final var value = rt.value();
                    if (isDocumentRoot && !(value instanceof JsonObject)) {
                        throw new JsonTransformException("cannot replace the document root with a non-object");
                    }
                    return new Applied(value, true);
                }
                case TransformAst.ReplaceOp.ByPath byPath -> {
                    final var matches = byPath.path().queryMatches(current);
                    if (matches.isEmpty()) {
                        LOG.fine(() -> "Replace path produced no results: " + byPath.rawPath());
                        break;
                    }
                    for (final var match : matches) {
                        final var location = match.location();
                        if (location.isEmpty() && isDocumentRoot && !(byPath.value() instanceof JsonObject)) {
                            throw new JsonTransformException("cannot replace the document root with a non-object");
                        }
                        current = replaceAt(current, location, byPath.value());
                        if (location.isEmpty()) {
                            return new Applied(current, true);
                        }
                    }
                }
            }
        }

        return new Applied(current, false);
    }

    private static Applied applyMerges(JsonObject source, List<TransformAst.MergeOp> merges, boolean isDocumentRoot) {
        JsonValue current = source;

        for (final var op : merges) {
            switch (op) {
                case TransformAst.MergeOp.MergeThis mt -> {
                    current = applyMergeValueToNode(current, mt.value(), isDocumentRoot);
                    if (!(current instanceof JsonObject)) {
                        return new Applied(current, true);
                    }
                }
                case TransformAst.MergeOp.ByPath byPath -> {
                    final var matches = byPath.path().queryMatches(current);
                    if (matches.isEmpty()) {
                        LOG.fine(() -> "Merge path produced no results: " + byPath.rawPath());
                        break;
                    }

                    for (final var match : matches) {
                        final var location = match.location();
                        final var merged = applyMergeValueToMatched(match.value(), byPath.value(), isDocumentRoot && location.isEmpty());
                        current = replaceAt(current, location, merged);
                        if (location.isEmpty() && !(current instanceof JsonObject)) {
                            return new Applied(current, true);
                        }
                    }
                }
            }
        }

        return new Applied(current, false);
    }

    private static JsonValue applyMergeValueToNode(JsonValue currentNode, TransformAst.MergeOp.Value mergeValue, boolean isDocumentRoot) {
        return switch (mergeValue) {
            case TransformAst.MergeOp.Value.Raw raw -> {
                final var v = raw.value();
                if (isDocumentRoot && !(v instanceof JsonObject)) {
                    throw new JsonTransformException("cannot replace the document root with a non-object");
                }
                yield v;
            }
            case TransformAst.MergeOp.Value.TransformObjectValue tov -> {
                // Apply nested transforms (ProcessTransform) at this node.
                if (!(currentNode instanceof JsonObject obj)) {
                    // Reference behavior is unclear; keep current unchanged.
                    yield currentNode;
                }
                final var applied = processTransform(obj, tov.compiled(), isDocumentRoot);
                yield applied.value();
            }
        };
    }

    private static JsonValue applyMergeValueToMatched(JsonValue matched, TransformAst.MergeOp.Value mergeValue, boolean isDocumentRootForMatch) {
        return switch (mergeValue) {
            case TransformAst.MergeOp.Value.Raw raw -> {
                final var v = raw.value();
                if (matched instanceof JsonArray a && v instanceof JsonArray b) {
                    yield append(a, b);
                }
                if (isDocumentRootForMatch && !(v instanceof JsonObject) && matched instanceof JsonObject) {
                    // Mirror root restriction when replacing the true root object.
                    throw new JsonTransformException("cannot replace the document root with a non-object");
                }
                yield v;
            }
            case TransformAst.MergeOp.Value.TransformObjectValue tov -> {
                if (matched instanceof JsonObject obj) {
                    final var applied = processTransform(obj, tov.compiled(), isDocumentRootForMatch);
                    yield applied.value();
                }
                yield matched;
            }
        };
    }

    private static JsonObject applyDefault(JsonObject source, Map<String, JsonValue> nonVerbMembers, Set<String> recursedKeys) {
        JsonObject current = source;

        for (final var entry : nonVerbMembers.entrySet()) {
            final var key = entry.getKey();
            final var tVal = entry.getValue();

            if (recursedKeys.contains(key) && current.members().get(key) instanceof JsonObject && tVal instanceof JsonObject) {
                continue;
            }

            final var sVal = current.members().get(key);
            if (sVal == null) {
                final var out = new LinkedHashMap<String, JsonValue>(current.members());
                out.put(key, tVal);
                current = JsonObject.of(out);
                continue;
            }

            if (sVal instanceof JsonArray sArr && tVal instanceof JsonArray tArr) {
                final var out = new LinkedHashMap<String, JsonValue>(current.members());
                out.put(key, append(sArr, tArr));
                current = JsonObject.of(out);
                continue;
            }

            if (!(sVal instanceof JsonObject) || !(tVal instanceof JsonObject)) {
                final var out = new LinkedHashMap<String, JsonValue>(current.members());
                out.put(key, tVal);
                current = JsonObject.of(out);
            }
        }

        return current;
    }

    private static JsonObject applyRenames(JsonObject source, List<TransformAst.RenameOp> renames, boolean isDocumentRoot) {
        JsonObject current = source;

        for (final var op : renames) {
            switch (op) {
                case TransformAst.RenameOp.Mapping mapping -> {
                    for (final var entry : mapping.renames().entrySet()) {
                        final var oldName = entry.getKey();
                        final var newName = entry.getValue();
                        if (!current.members().containsKey(oldName)) {
                            LOG.fine(() -> "Rename mapping skipped; missing key: " + oldName);
                            continue;
                        }
                        current = renameKey(current, oldName, newName);
                    }
                }
                case TransformAst.RenameOp.ByPath byPath -> {
                    final var matches = byPath.path().queryMatches(current);
                    if (matches.isEmpty()) {
                        LOG.fine(() -> "Rename path produced no results: " + byPath.rawPath());
                        break;
                    }
                    for (final var match : matches) {
                        final var loc = match.location();
                        if (loc.isEmpty()) {
                            throw new JsonTransformException("cannot rename the root node");
                        }
                        final var last = loc.getLast();
                        if (!(last instanceof JsonPathLocationStep.Property p)) {
                            throw new JsonTransformException("cannot rename array elements");
                        }
                        final var parentLoc = loc.subList(0, loc.size() - 1);
                        final var parent = resolve(current, parentLoc);
                        if (!(parent instanceof JsonObject parentObj)) {
                            throw new JsonTransformException("rename target is not an object member");
                        }
                        final var updatedParent = renameKey(parentObj, p.name(), byPath.newName());
                        current = (JsonObject) replaceAt(current, parentLoc, updatedParent);
                    }
                }
            }
        }

        return current;
    }

    private static JsonValue resolve(JsonValue root, List<JsonPathLocationStep> location) {
        JsonValue cur = root;
        for (final var step : location) {
            cur = switch (step) {
                case JsonPathLocationStep.Property p -> cur instanceof JsonObject obj ? obj.members().get(p.name()) : null;
                case JsonPathLocationStep.Index idx -> cur instanceof JsonArray arr ? arr.elements().get(idx.index()) : null;
            };
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }

    private static List<JsonPathMatch> sortForRemoval(List<JsonPathMatch> matches) {
        final var sorted = new ArrayList<JsonPathMatch>(matches);
        sorted.sort((a, b) -> compareRemovalLocations(b.location(), a.location())); // descending
        return sorted;
    }

    private static int compareRemovalLocations(List<JsonPathLocationStep> a, List<JsonPathLocationStep> b) {
        final int min = Math.min(a.size(), b.size());
        for (int i = 0; i < min; i++) {
            final var sa = a.get(i);
            final var sb = b.get(i);
            if (!sa.equals(sb)) {
                // Only attempt special ordering for array indices at same depth
                if (sa instanceof JsonPathLocationStep.Index ia && sb instanceof JsonPathLocationStep.Index ib) {
                    return Integer.compare(ia.index(), ib.index());
                }
                // Otherwise keep stable, but reversed comparator already applied at caller
                return 0;
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}

