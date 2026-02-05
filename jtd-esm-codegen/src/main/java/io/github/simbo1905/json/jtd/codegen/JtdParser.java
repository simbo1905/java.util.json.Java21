package io.github.simbo1905.json.jtd.codegen;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.github.simbo1905.json.jtd.codegen.JtdAst.*;

/// Parses a deliberately-limited subset of JTD (RFC 8927) for code generation.
///
/// This parser is *not* the general-purpose validator in `json-java21-jtd`.
/// It exists to support the experimental code generator and intentionally rejects
/// most of JTD.
final class JtdParser {
    private JtdParser() {}

    static SchemaNode parseString(String jtdJson) {
        Objects.requireNonNull(jtdJson, "jtdJson must not be null");
        return parseValue(Json.parse(jtdJson));
    }

    static SchemaNode parseValue(JsonValue rootValue) {
        Objects.requireNonNull(rootValue, "rootValue must not be null");
        if (!(rootValue instanceof JsonObject root)) {
            throw new IllegalArgumentException("JTD schema must be a JSON object");
        }

        rejectUnsupportedKeys(root, "elements", "values", "discriminator", "mapping", "ref", "definitions");

        final var metadata = getObjectOrNull(root, "metadata");
        if (metadata == null) {
            throw new IllegalArgumentException("JTD schema missing required key: metadata.id");
        }
        final var id = getString(metadata, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("metadata.id must be non-blank");
        }

        final Map<String, PropertyNode> properties = parsePropertiesBlock(root, "properties");
        final Map<String, PropertyNode> optionalProperties = parsePropertiesBlock(root, "optionalProperties");

        // Reject additional unknown top-level keys (keeps the tool intentionally strict/limited).
        final Set<String> allowedRoot = Set.of("properties", "optionalProperties", "metadata");
        for (String k : root.members().keySet()) {
            if (!allowedRoot.contains(k)) {
                throw unsupported("unknown key '" + k + "'");
            }
        }

        return new SchemaNode(id, Map.copyOf(properties), Map.copyOf(optionalProperties));
    }

    private static Map<String, PropertyNode> parsePropertiesBlock(JsonObject root, String key) {
        final var block = getObjectOrNull(root, key);
        if (block == null) {
            return Map.of();
        }

        final var out = new LinkedHashMap<String, PropertyNode>();
        for (var e : block.members().entrySet()) {
            final String propName = e.getKey();
            final JsonValue propSchemaValue = e.getValue();
            if (!(propSchemaValue instanceof JsonObject propSchemaObj)) {
                throw new IllegalArgumentException("Schema for '" + key + "." + propName + "' must be a JSON object");
            }
            final JtdNode type = parsePropertySchema(propName, propSchemaObj, key);
            out.put(propName, new PropertyNode(propName, type));
        }
        return out;
    }

    private static JtdNode parsePropertySchema(String propName, JsonObject propSchema, String containerKey) {
        // Explicitly reject unsupported features inside property schemas too.
        rejectUnsupportedKeys(propSchema, "elements", "values", "discriminator", "mapping", "ref", "definitions");

        if (propSchema.members().isEmpty()) {
            return new EmptyNode();
        }

        if (propSchema.members().containsKey("properties") || propSchema.members().containsKey("optionalProperties")) {
            throw unsupported("properties");
        }

        // Only allow leaf forms: type or enum.
        final boolean hasType = propSchema.members().containsKey("type");
        final boolean hasEnum = propSchema.members().containsKey("enum");
        if (hasType && hasEnum) {
            throw new IllegalArgumentException("Property '" + propName + "' must not specify both 'type' and 'enum'");
        }
        if (!hasType && !hasEnum) {
            // Any other leaf form is unsupported for this tool.
            final var keys = propSchema.members().keySet().stream().sorted().toList();
            throw unsupported("schema keys " + keys);
        }

        if (hasType) {
            final var typeStr = stringValue(propSchema.members().get("type"), containerKey, propName, "type");
            final var normalized = typeStr.toLowerCase(Locale.ROOT).trim();
            if (!ALLOWED_TYPES.contains(normalized)) {
                throw new IllegalArgumentException("Unsupported JTD type: '" + typeStr + "', expected one of: " + ALLOWED_TYPES);
            }
            rejectUnknownKeys(propSchema, Set.of("type"));
            return new TypeNode(normalized);
        }

        final var enumValues = enumValues(propSchema.members().get("enum"), containerKey, propName);
        rejectUnknownKeys(propSchema, Set.of("enum"));
        return new EnumNode(List.copyOf(enumValues));
    }

    private static void rejectUnknownKeys(JsonObject obj, Set<String> allowedKeys) {
        for (String k : obj.members().keySet()) {
            if (!allowedKeys.contains(k)) {
                throw unsupported("unknown key '" + k + "'");
            }
        }
    }

    private static void rejectUnsupportedKeys(JsonObject obj, String... keys) {
        for (String k : keys) {
            if (obj.members().containsKey(k)) {
                throw unsupported(k);
            }
        }
    }

    private static JsonObject getObjectOrNull(JsonObject obj, String key) {
        final var v = obj.members().get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof JsonObject o)) {
            throw new IllegalArgumentException("Expected '" + key + "' to be an object");
        }
        return o;
    }

    private static String getString(JsonObject obj, String key) {
        final var v = obj.members().get(key);
        if (!(v instanceof JsonString js)) {
            throw new IllegalArgumentException("Expected 'metadata." + key + "' to be a string");
        }
        return js.string();
    }

    private static String stringValue(JsonValue v, String container, String name, String key) {
        if (!(v instanceof JsonString js)) {
            throw new IllegalArgumentException("Expected '" + container + "." + name + "." + key + "' to be a string");
        }
        return js.string();
    }

    private static List<String> enumValues(JsonValue v, String containerKey, String propName) {
        if (!(v instanceof JsonArray arr)) {
            throw new IllegalArgumentException("Expected '" + containerKey + "." + propName + ".enum' to be an array");
        }
        final var out = new ArrayList<String>();
        for (int i = 0; i < arr.elements().size(); i++) {
            final var el = arr.element(i);
            if (!(el instanceof JsonString js)) {
                throw new IllegalArgumentException("Expected '" + containerKey + "." + propName + ".enum[" + i + "]' to be a string");
            }
            out.add(js.string());
        }
        return out;
    }

    private static IllegalArgumentException unsupported(String feature) {
        return new IllegalArgumentException(
                "Unsupported JTD feature: " + feature + ". This experimental tool only supports flat schemas with properties, optionalProperties, type, and enum."
        );
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "string",
            "boolean",
            "timestamp",
            "int8",
            "int16",
            "int32",
            "uint8",
            "uint16",
            "uint32",
            "float32",
            "float64"
    );
}

