package io.github.simbo1905.json.jtd.codegen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static io.github.simbo1905.json.jtd.codegen.JtdAst.*;

/// Renders an ES2020 JavaScript module exporting `validate(instance)`.
///
/// The generated validator:
/// - Treats the root as a JSON object (non-array, non-null)
/// - Checks required `properties` presence and validates leaf `type`/`enum`
/// - Checks optional `optionalProperties` only when present
/// - Ignores additional properties (RFC 8927 "properties" form allows them)
final class EsmRenderer {
    private EsmRenderer() {}

    static String render(SchemaNode schema, String sha256Hex, String sha256Prefix8) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(sha256Hex, "sha256Hex must not be null");
        Objects.requireNonNull(sha256Prefix8, "sha256Prefix8 must not be null");

        final var sb = new StringBuilder(8 * 1024);

        sb.append("// ").append(schema.id()).append("-").append(sha256Prefix8).append(".js\n");
        sb.append("// Generated from JTD schema: ").append(schema.id()).append("\n");
        sb.append("// SHA-256: ").append(sha256Hex).append(" (prefix: ").append(sha256Prefix8).append(")\n");
        sb.append("// WARNING: Experimental - flat schemas only\n");
        sb.append("\n");

        sb.append("const SCHEMA_ID = ").append(jsString(schema.id())).append(";\n\n");

        final var enumConsts = enumConstants(schema);
        for (var e : enumConsts.entrySet()) {
            sb.append("const ").append(e.getKey()).append(" = ").append(jsStringArray(e.getValue())).append(";\n");
        }
        if (!enumConsts.isEmpty()) {
            sb.append("\n");
        }

        sb.append("function isString(v) { return typeof v === \"string\"; }\n");
        sb.append("function isBoolean(v) { return typeof v === \"boolean\"; }\n");
        sb.append("function isTimestamp(v) { return typeof v === \"string\" && !Number.isNaN(Date.parse(v)); }\n");
        sb.append("function isNumber(v) { return typeof v === \"number\" && Number.isFinite(v); }\n");
        sb.append("function isInt(v) { return Number.isInteger(v); }\n");
        sb.append("function isIntRange(v, min, max) { return isInt(v) && v >= min && v <= max; }\n\n");

        sb.append("export function validate(instance) {\n");
        sb.append("    const errors = [];\n\n");

        sb.append("    if (instance === null || typeof instance !== \"object\" || Array.isArray(instance)) {\n");
        sb.append("        errors.push({ instancePath: \"\", schemaPath: \"\" });\n");
        sb.append("        return errors;\n");
        sb.append("    }\n\n");

        final var required = new TreeMap<>(schema.properties());
        for (var p : required.values()) {
            renderRequiredProperty(sb, p, enumConsts, "/properties");
            sb.append("\n");
        }

        final var optional = new TreeMap<>(schema.optionalProperties());
        for (var p : optional.values()) {
            renderOptionalProperty(sb, p, enumConsts, "/optionalProperties");
            sb.append("\n");
        }

        sb.append("    return errors;\n");
        sb.append("}\n\n");
        sb.append("export { SCHEMA_ID };\n");

        return sb.toString();
    }

    private static Map<String, List<String>> enumConstants(SchemaNode schema) {
        final var out = new LinkedHashMap<String, List<String>>();
        final var allProps = new ArrayList<PropertyNode>();
        allProps.addAll(schema.properties().values());
        allProps.addAll(schema.optionalProperties().values());
        allProps.sort(Comparator.comparing(PropertyNode::name));

        int i = 0;
        for (var p : allProps) {
            if (p.type() instanceof EnumNode en) {
                final String base = "ENUM_" + toConstName(p.name());
                String name = base;
                while (out.containsKey(name)) {
                    i++;
                    name = base + "_" + i;
                }
                out.put(name, en.values());
            }
        }
        return out;
    }

    private static void renderRequiredProperty(StringBuilder sb, PropertyNode p, Map<String, List<String>> enumConsts, String schemaPrefix) {
        final String prop = p.name();
        final String schemaPathProp = schemaPrefix + "/" + pointerEscape(prop);

        sb.append("    // Required: ").append(prop).append("\n");
        sb.append("    if (!(\"").append(jsStringRaw(prop)).append("\" in instance)) {\n");
        sb.append("        errors.push({ instancePath: \"\", schemaPath: \"").append(schemaPathProp).append("\" });\n");
        sb.append("    } else {\n");

        renderLeafCheck(sb, "instance[" + jsString(prop) + "]", "/" + pointerEscape(prop), schemaPathProp, p.type(), enumConsts);

        sb.append("    }\n");
    }

    private static void renderOptionalProperty(StringBuilder sb, PropertyNode p, Map<String, List<String>> enumConsts, String schemaPrefix) {
        final String prop = p.name();
        final String schemaPathProp = schemaPrefix + "/" + pointerEscape(prop);

        sb.append("    // Optional: ").append(prop).append("\n");
        sb.append("    if (\"").append(jsStringRaw(prop)).append("\" in instance) {\n");

        renderLeafCheck(sb, "instance[" + jsString(prop) + "]", "/" + pointerEscape(prop), schemaPathProp, p.type(), enumConsts);

        sb.append("    }\n");
    }

    private static void renderLeafCheck(
            StringBuilder sb,
            String valueExpr,
            String instancePath,
            String schemaPathProp,
            JtdNode node,
            Map<String, List<String>> enumConsts
    ) {
        switch (node) {
            case EmptyNode ignored -> {
                // Empty schema accepts any value.
            }
            case TypeNode tn -> {
                final String type = tn.type();
                final String check = typeCheckExpr(type, valueExpr);
                sb.append("        if (!(").append(check).append(")) {\n");
                sb.append("            errors.push({ instancePath: \"").append(instancePath).append("\", schemaPath: \"")
                        .append(schemaPathProp).append("/type\" });\n");
                sb.append("        }\n");
            }
            case EnumNode en -> {
                final String constName = findEnumConst(enumConsts, en.values());
                sb.append("        if (!").append(constName).append(".includes(").append(valueExpr).append(")) {\n");
                sb.append("            errors.push({ instancePath: \"").append(instancePath).append("\", schemaPath: \"")
                        .append(schemaPathProp).append("/enum\" });\n");
                sb.append("        }\n");
            }
            default -> throw new IllegalStateException("Unexpected node in leaf position: " + node);
        }
    }

    private static String findEnumConst(Map<String, List<String>> enumConsts, List<String> values) {
        for (var e : enumConsts.entrySet()) {
            if (e.getValue().equals(values)) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("Enum constants map missing values: " + values);
    }

    private static String typeCheckExpr(String type, String valueExpr) {
        return switch (type) {
            case "string" -> "isString(" + valueExpr + ")";
            case "boolean" -> "isBoolean(" + valueExpr + ")";
            case "timestamp" -> "isTimestamp(" + valueExpr + ")";
            case "float32", "float64" -> "isNumber(" + valueExpr + ")";
            case "int8" -> "isNumber(" + valueExpr + ") && isIntRange(" + valueExpr + ", -128, 127)";
            case "uint8" -> "isNumber(" + valueExpr + ") && isIntRange(" + valueExpr + ", 0, 255)";
            case "int16" -> "isNumber(" + valueExpr + ") && isIntRange(" + valueExpr + ", -32768, 32767)";
            case "uint16" -> "isNumber(" + valueExpr + ") && isIntRange(" + valueExpr + ", 0, 65535)";
            case "int32" -> "isNumber(" + valueExpr + ") && isIntRange(" + valueExpr + ", -2147483648, 2147483647)";
            case "uint32" -> "isNumber(" + valueExpr + ") && isIntRange(" + valueExpr + ", 0, 4294967295)";
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    private static String toConstName(String propName) {
        final var sb = new StringBuilder(propName.length() + 8);
        for (int i = 0; i < propName.length(); i++) {
            final char c = propName.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append((char) (c - 32));
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(c);
            } else if (c >= '0' && c <= '9') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        if (sb.isEmpty()) {
            return "PROP";
        }
        if (sb.charAt(0) >= '0' && sb.charAt(0) <= '9') {
            sb.insert(0, "P_");
        }
        return sb.toString();
    }

    /// Escape a JSON Pointer path segment.
    private static String pointerEscape(String s) {
        return s.replace("~", "~0").replace("/", "~1");
    }

    private static String jsString(String s) {
        return "\"" + jsStringRaw(s) + "\"";
    }

    private static String jsStringRaw(String s) {
        final var sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String jsStringArray(List<String> values) {
        final var sb = new StringBuilder(values.size() * 16);
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(jsString(values.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }
}

