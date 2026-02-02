package json.java21.transforms;

import json.java21.jsonpath.JsonPathLocationStep;
import jdk.sandbox.java.util.json.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

final class TransformPatch {

    private TransformPatch() {}

    static JsonValue removeAt(JsonValue root, List<JsonPathLocationStep> location) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(location, "location must not be null");
        if (location.isEmpty()) {
            throw new IllegalArgumentException("removeAt cannot remove the root; handle at caller");
        }
        return removeAt0(root, location, 0);
    }

    static JsonValue replaceAt(JsonValue root, List<JsonPathLocationStep> location, JsonValue newValue) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(newValue, "newValue must not be null");
        if (location.isEmpty()) {
            return newValue;
        }
        return replaceAt0(root, location, 0, newValue);
    }

    static JsonArray append(JsonArray original, JsonArray toAppend) {
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(toAppend, "toAppend must not be null");
        if (toAppend.elements().isEmpty()) return original;
        final var out = new ArrayList<JsonValue>(original.elements().size() + toAppend.elements().size());
        out.addAll(original.elements());
        out.addAll(toAppend.elements());
        return JsonArray.of(out);
    }

    static JsonObject renameKey(JsonObject obj, String oldName, String newName) {
        Objects.requireNonNull(obj, "obj must not be null");
        Objects.requireNonNull(oldName, "oldName must not be null");
        Objects.requireNonNull(newName, "newName must not be null");

        if (!obj.members().containsKey(oldName)) {
            return obj;
        }

        final var out = new LinkedHashMap<String, JsonValue>(obj.members().size());
        for (final var entry : obj.members().entrySet()) {
            if (entry.getKey().equals(oldName)) {
                out.put(newName, entry.getValue());
            } else {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return JsonObject.of(out);
    }

    private static JsonValue removeAt0(JsonValue current, List<JsonPathLocationStep> location, int index) {
        final var step = location.get(index);
        final boolean isLast = index == location.size() - 1;

        if (isLast) {
            return switch (step) {
                case JsonPathLocationStep.Property p -> {
                    if (!(current instanceof JsonObject obj)) yield current;
                    if (!obj.members().containsKey(p.name())) yield current;
                    final var out = new LinkedHashMap<String, JsonValue>(obj.members().size());
                    for (final var entry : obj.members().entrySet()) {
                        if (!entry.getKey().equals(p.name())) {
                            out.put(entry.getKey(), entry.getValue());
                        }
                    }
                    yield JsonObject.of(out);
                }
                case JsonPathLocationStep.Index idx -> {
                    if (!(current instanceof JsonArray arr)) yield current;
                    final int i = idx.index();
                    if (i < 0 || i >= arr.elements().size()) yield current;
                    final var out = new ArrayList<JsonValue>(Math.max(0, arr.elements().size() - 1));
                    for (int j = 0; j < arr.elements().size(); j++) {
                        if (j != i) out.add(arr.elements().get(j));
                    }
                    yield JsonArray.of(out);
                }
            };
        }

        return switch (step) {
            case JsonPathLocationStep.Property p -> {
                if (!(current instanceof JsonObject obj)) yield current;
                final var child = obj.members().get(p.name());
                if (child == null) yield current;
                final var newChild = removeAt0(child, location, index + 1);
                if (newChild == child) yield current;
                final var out = new LinkedHashMap<String, JsonValue>(obj.members());
                out.put(p.name(), newChild);
                yield JsonObject.of(out);
            }
            case JsonPathLocationStep.Index idx -> {
                if (!(current instanceof JsonArray arr)) yield current;
                final int i = idx.index();
                if (i < 0 || i >= arr.elements().size()) yield current;
                final var child = arr.elements().get(i);
                final var newChild = removeAt0(child, location, index + 1);
                if (newChild == child) yield current;
                final var out = new ArrayList<JsonValue>(arr.elements());
                out.set(i, newChild);
                yield JsonArray.of(out);
            }
        };
    }

    private static JsonValue replaceAt0(JsonValue current, List<JsonPathLocationStep> location, int index, JsonValue newValue) {
        final var step = location.get(index);
        final boolean isLast = index == location.size() - 1;

        if (isLast) {
            return switch (step) {
                case JsonPathLocationStep.Property p -> {
                    if (!(current instanceof JsonObject obj)) yield current;
                    if (!obj.members().containsKey(p.name())) yield current;
                    final var out = new LinkedHashMap<String, JsonValue>(obj.members());
                    out.put(p.name(), newValue);
                    yield JsonObject.of(out);
                }
                case JsonPathLocationStep.Index idx -> {
                    if (!(current instanceof JsonArray arr)) yield current;
                    final int i = idx.index();
                    if (i < 0 || i >= arr.elements().size()) yield current;
                    final var out = new ArrayList<JsonValue>(arr.elements());
                    out.set(i, newValue);
                    yield JsonArray.of(out);
                }
            };
        }

        return switch (step) {
            case JsonPathLocationStep.Property p -> {
                if (!(current instanceof JsonObject obj)) yield current;
                final var child = obj.members().get(p.name());
                if (child == null) yield current;
                final var newChild = replaceAt0(child, location, index + 1, newValue);
                if (newChild == child) yield current;
                final var out = new LinkedHashMap<String, JsonValue>(obj.members());
                out.put(p.name(), newChild);
                yield JsonObject.of(out);
            }
            case JsonPathLocationStep.Index idx -> {
                if (!(current instanceof JsonArray arr)) yield current;
                final int i = idx.index();
                if (i < 0 || i >= arr.elements().size()) yield current;
                final var child = arr.elements().get(i);
                final var newChild = replaceAt0(child, location, index + 1, newValue);
                if (newChild == child) yield current;
                final var out = new ArrayList<JsonValue>(arr.elements());
                out.set(i, newChild);
                yield JsonArray.of(out);
            }
        };
    }
}

