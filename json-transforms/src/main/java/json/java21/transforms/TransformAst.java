package json.java21.transforms;

import json.java21.jsonpath.JsonPath;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.List;
import java.util.Map;
import java.util.Objects;

sealed interface TransformAst {

    record ObjectTransform(
            Map<String, JsonValue> nonVerbMembers,
            Map<String, ObjectTransform> childObjects,
            List<RemoveOp> removes,
            List<ReplaceOp> replaces,
            List<MergeOp> merges,
            List<RenameOp> renames
    ) implements TransformAst {
        ObjectTransform {
            Objects.requireNonNull(nonVerbMembers, "nonVerbMembers must not be null");
            Objects.requireNonNull(childObjects, "childObjects must not be null");
            Objects.requireNonNull(removes, "removes must not be null");
            Objects.requireNonNull(replaces, "replaces must not be null");
            Objects.requireNonNull(merges, "merges must not be null");
            Objects.requireNonNull(renames, "renames must not be null");
            nonVerbMembers = Map.copyOf(nonVerbMembers);
            childObjects = Map.copyOf(childObjects);
            removes = List.copyOf(removes);
            replaces = List.copyOf(replaces);
            merges = List.copyOf(merges);
            renames = List.copyOf(renames);
        }
    }

    sealed interface RemoveOp extends TransformAst permits RemoveOp.ByName, RemoveOp.RemoveThis, RemoveOp.ByPath {
        record ByName(String name) implements RemoveOp {
            ByName {
                Objects.requireNonNull(name, "name must not be null");
            }
        }

        record RemoveThis() implements RemoveOp {}

        record ByPath(String rawPath, JsonPath path) implements RemoveOp {
            ByPath {
                Objects.requireNonNull(rawPath, "rawPath must not be null");
                Objects.requireNonNull(path, "path must not be null");
            }
        }
    }

    sealed interface ReplaceOp extends TransformAst permits ReplaceOp.ReplaceThis, ReplaceOp.ByPath {
        record ReplaceThis(JsonValue value) implements ReplaceOp {
            ReplaceThis {
                Objects.requireNonNull(value, "value must not be null");
            }
        }

        record ByPath(String rawPath, JsonPath path, JsonValue value) implements ReplaceOp {
            ByPath {
                Objects.requireNonNull(rawPath, "rawPath must not be null");
                Objects.requireNonNull(path, "path must not be null");
                Objects.requireNonNull(value, "value must not be null");
            }
        }
    }

    sealed interface MergeOp extends TransformAst permits MergeOp.MergeThis, MergeOp.ByPath {

        record MergeThis(Value value) implements MergeOp {
            MergeThis {
                Objects.requireNonNull(value, "value must not be null");
            }
        }

        record ByPath(String rawPath, JsonPath path, Value value) implements MergeOp {
            ByPath {
                Objects.requireNonNull(rawPath, "rawPath must not be null");
                Objects.requireNonNull(path, "path must not be null");
                Objects.requireNonNull(value, "value must not be null");
            }
        }

        sealed interface Value permits Value.Raw, Value.TransformObjectValue {
            record Raw(JsonValue value) implements Value {
                Raw {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            record TransformObjectValue(JsonObject rawObject, ObjectTransform compiled) implements Value {
                TransformObjectValue {
                    Objects.requireNonNull(rawObject, "rawObject must not be null");
                    Objects.requireNonNull(compiled, "compiled must not be null");
                }
            }
        }
    }

    sealed interface RenameOp extends TransformAst permits RenameOp.Mapping, RenameOp.ByPath {
        record Mapping(Map<String, String> renames) implements RenameOp {
            Mapping {
                Objects.requireNonNull(renames, "renames must not be null");
                renames = Map.copyOf(renames);
            }
        }

        record ByPath(String rawPath, JsonPath path, String newName) implements RenameOp {
            ByPath {
                Objects.requireNonNull(rawPath, "rawPath must not be null");
                Objects.requireNonNull(path, "path must not be null");
                Objects.requireNonNull(newName, "newName must not be null");
            }
        }
    }
}

