package io.github.simbo1905.json.jtd.codegen;

import jdk.sandbox.java.util.json.*;

import java.util.*;

import static io.github.simbo1905.json.jtd.codegen.JtdAst.*;

/// Parses JTD (RFC 8927) schemas for code generation.
/// Supports all schema forms including elements, values, discriminator, and nullable.
final class JtdParser {
    private JtdParser() {}

    static RootNode parseString(String jtdJson) {
        Objects.requireNonNull(jtdJson, "jtdJson must not be null");
        return parseValue(Json.parse(jtdJson));
    }

    static RootNode parseValue(JsonValue rootValue) {
        Objects.requireNonNull(rootValue, "rootValue must not be null");
        if (!(rootValue instanceof JsonObject root)) {
            throw new IllegalArgumentException("JTD schema must be a JSON object");
        }

        final var metadata = getObjectOrNull(root, "metadata");
        final String id;
        if (metadata != null && metadata.members().containsKey("id")) {
            id = getString(metadata, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("metadata.id must be non-blank");
            }
        } else {
            id = "JtdSchema";
        }

        final Map<String, JtdNode> definitions = new LinkedHashMap<>();
        if (root.members().containsKey("definitions")) {
            final var defsObj = getObjectOrNull(root, "definitions");
            if (defsObj != null) {
                for (var e : defsObj.members().entrySet()) {
                    definitions.put(e.getKey(), parseSchema(e.getKey(), e.getValue(), true));
                }
            }
        }

        final JtdNode rootSchema = parseSchema("root", root, false);

        return new RootNode(id, definitions, rootSchema);
    }

    private static JtdNode parseSchema(String propName, JsonValue schemaValue, boolean inDefinitions) {
        if (!(schemaValue instanceof JsonObject schema)) {
            throw new IllegalArgumentException("Schema for '" + propName + "' must be a JSON object");
        }

        // Check for nullable wrapper first
        boolean isNullable = false;
        if (schema.members().containsKey("nullable")) {
            final var nullableVal = schema.members().get("nullable");
            if (nullableVal instanceof JsonBoolean jb && jb.bool()) {
                isNullable = true;
            }
        }

        JtdNode coreNode;

        // 1. Ref
        if (schema.members().containsKey("ref")) {
            final var ref = stringValue(schema.members().get("ref"), propName, "ref");
            coreNode = new RefNode(ref);
        }
        // 2. Type
        else if (schema.members().containsKey("type")) {
            final var typeStr = stringValue(schema.members().get("type"), propName, "type");
            final var normalized = typeStr.toLowerCase(Locale.ROOT).trim();
            if (!ALLOWED_TYPES.contains(normalized)) {
                throw new IllegalArgumentException("Unknown type: '" + typeStr + 
                    "', expected one of: " + String.join(", ", ALLOWED_TYPES));
            }
            coreNode = new TypeNode(normalized);
        }
        // 3. Enum
        else if (schema.members().containsKey("enum")) {
            final var enumValues = enumValues(schema.members().get("enum"), propName);
            coreNode = new EnumNode(List.copyOf(enumValues));
        }
        // 4. Elements (arrays)
        else if (schema.members().containsKey("elements")) {
            final var elementsVal = schema.members().get("elements");
            final var elementSchema = parseSchema(propName + "[]", elementsVal, inDefinitions);
            coreNode = new ElementsNode(elementSchema);
        }
        // 5. Values (string->value maps)
        else if (schema.members().containsKey("values")) {
            final var valuesVal = schema.members().get("values");
            final var valueSchema = parseSchema(propName + "{}", valuesVal, inDefinitions);
            coreNode = new ValuesNode(valueSchema);
        }
        // 6. Discriminator (tagged unions)
        else if (schema.members().containsKey("discriminator")) {
            final var discVal = stringValue(schema.members().get("discriminator"), propName, "discriminator");
            
            if (!schema.members().containsKey("mapping")) {
                throw new IllegalArgumentException("discriminator requires mapping");
            }
            
            final var mappingObj = getObjectOrNull(schema, "mapping");
            if (mappingObj == null) {
                throw new IllegalArgumentException("mapping must be an object");
            }
            
            final Map<String, JtdNode> mapping = new LinkedHashMap<>();
            for (var e : mappingObj.members().entrySet()) {
                mapping.put(e.getKey(), parseSchema(propName + "." + e.getKey(), e.getValue(), inDefinitions));
            }
            
            coreNode = new DiscriminatorNode(discVal, mapping);
        }
        // 7. Properties
        else if (hasPropertiesLikeKeys(schema)) {
            final Map<String, JtdNode> props = new LinkedHashMap<>();
            if (schema.members().containsKey("properties")) {
                final var p = getObjectOrNull(schema, "properties");
                if (p != null) {
                    for (var e : p.members().entrySet()) {
                        props.put(e.getKey(), parseSchema(propName + "." + e.getKey(), e.getValue(), inDefinitions));
                    }
                }
            }

            final Map<String, JtdNode> optionalProps = new LinkedHashMap<>();
            if (schema.members().containsKey("optionalProperties")) {
                final var op = getObjectOrNull(schema, "optionalProperties");
                if (op != null) {
                    for (var e : op.members().entrySet()) {
                        optionalProps.put(e.getKey(), parseSchema(propName + "." + e.getKey(), e.getValue(), inDefinitions));
                    }
                }
            }

            boolean additional = false;
            if (schema.members().containsKey("additionalProperties")) {
                final var ap = schema.members().get("additionalProperties");
                if (ap instanceof JsonBoolean b) {
                    additional = b.bool();
                }
            }

            coreNode = new PropertiesNode(props, optionalProps, additional);
        }
        // 8. Empty (accepts anything)
        else {
            coreNode = new EmptyNode();
        }

        // Wrap in nullable if needed
        if (isNullable && !(coreNode instanceof EmptyNode)) {
            return new NullableNode(coreNode);
        }
        return coreNode;
    }

    private static boolean hasPropertiesLikeKeys(JsonObject schema) {
        return schema.members().containsKey("properties") ||
               schema.members().containsKey("optionalProperties") ||
               schema.members().containsKey("additionalProperties");
    }

    private static JsonObject getObjectOrNull(JsonObject obj, String key) {
        final var v = obj.members().get(key);
        if (v == null) return null;
        if (!(v instanceof JsonObject o)) {
            throw new IllegalArgumentException("Expected '" + key + "' to be an object");
        }
        return o;
    }

    private static String getString(JsonObject obj, String key) {
        final var v = obj.members().get(key);
        if (!(v instanceof JsonString js)) {
            throw new IllegalArgumentException("Expected '" + key + "' to be a string");
        }
        return js.string();
    }

    private static String stringValue(JsonValue v, String container, String key) {
        if (!(v instanceof JsonString js)) {
            throw new IllegalArgumentException("Expected '" + container + "." + key + "' to be a string");
        }
        return js.string();
    }

    private static List<String> enumValues(JsonValue v, String propName) {
        if (!(v instanceof JsonArray arr)) {
            throw new IllegalArgumentException("Expected '" + propName + ".enum' to be an array");
        }
        final var out = new ArrayList<String>();
        for (int i = 0; i < arr.elements().size(); i++) {
            final var el = arr.element(i);
            if (!(el instanceof JsonString js)) {
                throw new IllegalArgumentException("Expected '" + propName + ".enum[" + i + "]' to be a string");
            }
            out.add(js.string());
        }
        return out;
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "string", "boolean", "timestamp", "int8", "uint8", "int16", "uint16",
            "int32", "uint32", "float32", "float64"
    );
}
