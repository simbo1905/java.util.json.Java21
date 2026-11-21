package io.github.simbo1905.json.jtd.codegen;

import jdk.incubator.java.util.json.*;

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
        if (metadata != null && metadata.asMap().containsKey("id")) {
            id = getString(metadata, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("metadata.id must be non-blank");
            }
        } else {
            id = "JtdSchema";
        }

        // Two-pass definitions handling per JTD_CODEGEN_SPEC.md section 3.2:
        // pass 1 registers all definition names so refs resolve regardless of
        // declaration order (forward references); pass 2 compiles each definition.
        final Map<String, JtdNode> definitions = new LinkedHashMap<>();
        final Set<String> knownDefinitionNames;
        if (root.asMap().containsKey("definitions")) {
            final var defsObj = getObjectOrNull(root, "definitions");
            if (defsObj != null) {
                knownDefinitionNames = Set.copyOf(defsObj.asMap().keySet());
                for (var e : defsObj.asMap().entrySet()) {
                    definitions.put(e.getKey(), parseSchema(e.getKey(), e.getValue(), false));
                }
            } else {
                knownDefinitionNames = Set.of();
            }
        } else {
            knownDefinitionNames = Set.of();
        }

        final JtdNode rootSchema = parseSchema("root", root, true);

        // Compile-time invariant per spec section 3.4: every ref name must
        // resolve to an entry in definitions, wherever the ref appears.
        validateRefs(rootSchema, knownDefinitionNames, "root");
        for (var e : definitions.entrySet()) {
            validateRefs(e.getValue(), knownDefinitionNames, "/definitions/" + e.getKey());
        }

        return new RootNode(id, definitions, rootSchema);
    }

    private static void validateRefs(JtdNode node, Set<String> knownDefinitionNames, String path) {
        switch (node) {
            case RefNode rn -> {
                if (!knownDefinitionNames.contains(rn.ref())) {
                    throw new IllegalArgumentException("ref '" + rn.ref() + "' at schema path '" + path +
                        "' does not match any definition; known definitions: " +
                        String.join(", ", new TreeSet<>(knownDefinitionNames)));
                }
            }
            case NullableNode nn -> validateRefs(nn.wrapped(), knownDefinitionNames, path + "/nullable");
            case ElementsNode en -> validateRefs(en.schema(), knownDefinitionNames, path + "/elements");
            case ValuesNode vn -> validateRefs(vn.schema(), knownDefinitionNames, path + "/values");
            case PropertiesNode pn -> {
                for (var e : pn.properties().entrySet()) {
                    validateRefs(e.getValue(), knownDefinitionNames, path + "/properties/" + e.getKey());
                }
                for (var e : pn.optionalProperties().entrySet()) {
                    validateRefs(e.getValue(), knownDefinitionNames, path + "/optionalProperties/" + e.getKey());
                }
            }
            case DiscriminatorNode dn -> {
                for (var e : dn.mapping().entrySet()) {
                    validateRefs(e.getValue(), knownDefinitionNames, path + "/mapping/" + e.getKey());
                }
            }
            case EnumNode ignored -> { }
            case TypeNode ignored -> { }
            case EmptyNode ignored -> { }
        }
    }

    private static JtdNode parseSchema(String propName, JsonValue schemaValue, boolean isRoot) {
        if (!(schemaValue instanceof JsonObject schema)) {
            throw new IllegalArgumentException("Schema for '" + propName + "' must be a JSON object");
        }

        // Compile-time rejection per spec section 3.4: definitions is a root-only key
        if (!isRoot && schema.asMap().containsKey("definitions")) {
            throw new IllegalArgumentException("definitions is only allowed at the root of a JTD schema; found nested at '" + propName + "'");
        }

        // Check for nullable wrapper first
        boolean isNullable = false;
        if (schema.asMap().containsKey("nullable")) {
            final var nullableVal = schema.asMap().get("nullable");
            if (nullableVal instanceof JsonBoolean jb && jb.asBoolean()) {
                isNullable = true;
            }
        }

        JtdNode coreNode;

        // 1. Ref
        if (schema.asMap().containsKey("ref")) {
            final var ref = stringValue(schema.asMap().get("ref"), propName, "ref");
            coreNode = new RefNode(ref);
        }
        // 2. Type
        else if (schema.asMap().containsKey("type")) {
            final var typeStr = stringValue(schema.asMap().get("type"), propName, "type");
            final var normalized = typeStr.toLowerCase(Locale.ROOT).trim();
            if (!ALLOWED_TYPES.contains(normalized)) {
                throw new IllegalArgumentException("Unknown type: '" + typeStr + 
                    "', expected one of: " + String.join(", ", ALLOWED_TYPES));
            }
            coreNode = new TypeNode(normalized);
        }
        // 3. Enum
        else if (schema.asMap().containsKey("enum")) {
            final var enumValues = enumValues(schema.asMap().get("enum"), propName);
            coreNode = new EnumNode(List.copyOf(enumValues));
        }
        // 4. Elements (arrays)
        else if (schema.asMap().containsKey("elements")) {
            final var elementsVal = schema.asMap().get("elements");
            final var elementSchema = parseSchema(propName + "[]", elementsVal, false);
            coreNode = new ElementsNode(elementSchema);
        }
        // 5. Values (string->value maps)
        else if (schema.asMap().containsKey("values")) {
            final var valuesVal = schema.asMap().get("values");
            final var valueSchema = parseSchema(propName + "{}", valuesVal, false);
            coreNode = new ValuesNode(valueSchema);
        }
        // 6. Discriminator (tagged unions)
        else if (schema.asMap().containsKey("discriminator")) {
            final var discVal = stringValue(schema.asMap().get("discriminator"), propName, "discriminator");
            
            if (!schema.asMap().containsKey("mapping")) {
                throw new IllegalArgumentException("discriminator requires mapping");
            }
            
            final var mappingObj = getObjectOrNull(schema, "mapping");
            if (mappingObj == null) {
                throw new IllegalArgumentException("mapping must be an object");
            }
            
            final Map<String, JtdNode> mapping = new LinkedHashMap<>();
            for (var e : mappingObj.asMap().entrySet()) {
                final var variant = parseSchema(propName + "." + e.getKey(), e.getValue(), false);
                // Compile-time rejection per spec section 3.4: mapping values must be
                // properties form schemas (not nullable, not any other form)
                if (!(variant instanceof PropertiesNode variantProps)) {
                    throw new IllegalArgumentException("discriminator mapping value for '" + e.getKey() +
                        "' at '" + propName + "' must be a properties form schema; got: " +
                        variant.getClass().getSimpleName());
                }
                // Compile-time rejection per spec section 3.4: the tag key is validated
                // by the discriminator itself and must not appear in the variant
                if (variantProps.properties().containsKey(discVal) ||
                    variantProps.optionalProperties().containsKey(discVal)) {
                    throw new IllegalArgumentException("discriminator tag '" + discVal +
                        "' must not appear in variant '" + e.getKey() +
                        "' properties at '" + propName + "'; the discriminator validates the tag key itself");
                }
                mapping.put(e.getKey(), variant);
            }
            
            coreNode = new DiscriminatorNode(discVal, mapping);
        }
        // 7. Properties
        else if (hasPropertiesLikeKeys(schema)) {
            final Map<String, JtdNode> props = new LinkedHashMap<>();
            if (schema.asMap().containsKey("properties")) {
                final var p = getObjectOrNull(schema, "properties");
                if (p != null) {
                    for (var e : p.asMap().entrySet()) {
                        props.put(e.getKey(), parseSchema(propName + "." + e.getKey(), e.getValue(), false));
                    }
                }
            }

            final Map<String, JtdNode> optionalProps = new LinkedHashMap<>();
            if (schema.asMap().containsKey("optionalProperties")) {
                final var op = getObjectOrNull(schema, "optionalProperties");
                if (op != null) {
                    for (var e : op.asMap().entrySet()) {
                        optionalProps.put(e.getKey(), parseSchema(propName + "." + e.getKey(), e.getValue(), false));
                    }
                }
            }

            // Compile-time rejection per spec section 3.4: a key must appear in at
            // most one of properties or optionalProperties
            final var overlap = new TreeSet<>(props.keySet());
            overlap.retainAll(optionalProps.keySet());
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException("properties and optionalProperties at '" + propName +
                    "' share keys: " + overlap +
                    "; a key must appear in at most one of properties or optionalProperties");
            }

            boolean additional = false;
            if (schema.asMap().containsKey("additionalProperties")) {
                final var ap = schema.asMap().get("additionalProperties");
                if (ap instanceof JsonBoolean b) {
                    additional = b.asBoolean();
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
        // "additionalProperties" alone is NOT a properties form trigger:
        // {"additionalProperties": true} is the empty form and accepts any JSON value.
        return schema.asMap().containsKey("properties") ||
               schema.asMap().containsKey("optionalProperties");
    }

    private static JsonObject getObjectOrNull(JsonObject obj, String key) {
        final var v = obj.asMap().get(key);
        if (v == null) return null;
        if (!(v instanceof JsonObject o)) {
            throw new IllegalArgumentException("Expected '" + key + "' to be an object");
        }
        return o;
    }

    private static String getString(JsonObject obj, String key) {
        final var v = obj.asMap().get(key);
        if (!(v instanceof JsonString js)) {
            throw new IllegalArgumentException("Expected '" + key + "' to be a string");
        }
        return js.asString();
    }

    private static String stringValue(JsonValue v, String container, String key) {
        if (!(v instanceof JsonString js)) {
            throw new IllegalArgumentException("Expected '" + container + "." + key + "' to be a string");
        }
        return js.asString();
    }

    private static List<String> enumValues(JsonValue v, String propName) {
        if (!(v instanceof JsonArray arr)) {
            throw new IllegalArgumentException("Expected '" + propName + ".enum' to be an array");
        }
        final var out = new ArrayList<String>();
        for (int i = 0; i < arr.asList().size(); i++) {
            final var el = arr.get(i);
            if (!(el instanceof JsonString js)) {
                throw new IllegalArgumentException("Expected '" + propName + ".enum[" + i + "]' to be a string");
            }
            out.add(js.asString());
        }
        // Compile-time rejections per spec section 3.4: enum must be a non-empty
        // array of unique strings
        if (out.isEmpty()) {
            throw new IllegalArgumentException("enum at '" + propName +
                "' is empty; enum must contain at least one unique string value");
        }
        final var seen = new HashSet<String>();
        final var duplicates = new LinkedHashSet<String>();
        for (final var value : out) {
            if (!seen.add(value)) {
                duplicates.add(value);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("enum at '" + propName + "' contains duplicate values: " +
                duplicates + "; enum values must be unique");
        }
        return out;
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "string", "boolean", "timestamp", "int8", "uint8", "int16", "uint16",
            "int32", "uint32", "float32", "float64"
    );
}
