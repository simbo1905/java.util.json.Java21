package io.github.simbo1905.json.jtd.codegen;

import java.util.*;

import static io.github.simbo1905.json.jtd.codegen.JtdAst.*;

/// Generates ES2020 ESM validators per JTD_CODEGEN_SPEC.md
/// 
/// Key principles:
/// - No runtime stack - direct code emission
/// - No helper functions - inline all checks
/// - Emit only what the schema requires (no dead code)
/// - Return {instancePath, schemaPath} error objects per RFC 8927
final class EsmRenderer {
    private EsmRenderer() {}

    static String render(RootNode schema, String sha256Hex, String shaPrefix8) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(sha256Hex, "sha256Hex must not be null");
        Objects.requireNonNull(shaPrefix8, "shaPrefix8 must not be null");

        final var ctx = new RenderContext();
        ctx.sha256Hex = sha256Hex;
        ctx.shaPrefix8 = shaPrefix8;
        ctx.schemaId = schema.id();

        final var sb = new StringBuilder(8 * 1024);

        // Header
        sb.append("// ").append(schema.id()).append("-").append(shaPrefix8).append(".js\n");
        sb.append("// Generated from JTD schema: ").append(schema.id()).append("\n");
        sb.append("// SHA-256: ").append(sha256Hex).append(" (prefix: ").append(shaPrefix8).append(")\n");
        sb.append("\n");

        sb.append("const SCHEMA_ID = ").append(jsString(schema.id())).append(";\n\n");

        // Collect all enum constants used in the schema
        collectEnums(schema.rootSchema(), ctx);
        for (var def : schema.definitions().values()) {
            collectEnums(def, ctx);
        }
        generateEnumConstants(sb, ctx);

        // Generate validation functions for definitions
        for (var entry : schema.definitions().entrySet()) {
            final String defName = entry.getKey();
            final JtdNode defNode = entry.getValue();
            generateDefinitionFunction(sb, defName, defNode, ctx);
        }

        // Generate the main validate function
        sb.append("export function validate(instance) {\n");
        sb.append("    const errors = [];\n");
        
        // Emit validation logic inline for root
        final var rootCode = new StringBuilder();
        generateNodeValidation(rootCode, schema.rootSchema(), ctx, "instance", "\"\"", "\"\"", "    ", null);
        sb.append(rootCode);
        
        sb.append("    return errors;\n");
        sb.append("}\n\n");

        // Generate inline validator functions for complex nested schemas
        generateInlineFunctions(sb, ctx);

        sb.append("export { SCHEMA_ID };\n");

        return sb.toString();
    }

    private static void collectEnums(JtdNode node, RenderContext ctx) {
        switch (node) {
            case EnumNode en -> {
                final String constName = "ENUM_" + (ctx.enumCounter++);
                ctx.enumConstants.put(constName, en.values());
            }
            case ElementsNode el -> collectEnums(el.schema(), ctx);
            case ValuesNode vn -> collectEnums(vn.schema(), ctx);
            case PropertiesNode pn -> {
                pn.properties().values().forEach(n -> collectEnums(n, ctx));
                pn.optionalProperties().values().forEach(n -> collectEnums(n, ctx));
            }
            case DiscriminatorNode dn -> dn.mapping().values().forEach(n -> collectEnums(n, ctx));
            case NullableNode nn -> collectEnums(nn.wrapped(), ctx);
            default -> {} // No enums
        }
    }

    private static void generateEnumConstants(StringBuilder sb, RenderContext ctx) {
        if (ctx.enumConstants.isEmpty()) return;
        
        for (var entry : ctx.enumConstants.entrySet()) {
            sb.append("const ").append(entry.getKey()).append(" = ")
              .append(jsStringArray(entry.getValue())).append(";\n");
        }
        sb.append("\n");
    }

    private static void generateDefinitionFunction(StringBuilder sb, String defName, JtdNode node, RenderContext ctx) {
        final String safeName = toSafeName(defName);
        
        sb.append("function validate_").append(safeName).append("(v, errors, p, sp) {\n");
        generateNodeValidation(sb, node, ctx, "v", "p", "sp", "    ", null);
        sb.append("}\n\n");
    }

    /**
     * Generates validation code for a node.
     * @param discriminatorKey If non-null, this PropertiesNode is a discriminator variant and should
     *                         skip validation of the discriminator key itself
     */
    private static void generateNodeValidation(StringBuilder sb, JtdNode node, RenderContext ctx,
            String valueExpr, String pathExpr, String schemaPathExpr, String indent, String discriminatorKey) {
        
        switch (node) {
            case EmptyNode ignored -> {
                // Accepts anything - no validation
            }
            
            case NullableNode nn -> {
                if (nn.wrapped() instanceof EmptyNode) {
                    // Nullable empty - accepts anything including null, no check needed
                } else {
                    sb.append(indent).append("if (").append(valueExpr).append(" !== null) {\n");
                    generateNodeValidation(sb, nn.wrapped(), ctx, valueExpr, pathExpr, schemaPathExpr, indent + "    ", discriminatorKey);
                    sb.append(indent).append("}\n");
                }
            }
            
            case TypeNode tn -> {
                generateTypeCheck(sb, tn.type(), valueExpr, pathExpr, schemaPathExpr, indent);
            }
            
            case EnumNode en -> {
                final String constName = findEnumConst(ctx.enumConstants, en.values());
                sb.append(indent).append("if (typeof ").append(valueExpr).append(" !== \"string\" || !")
                  .append(constName).append(".includes(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({instancePath: ").append(pathExpr)
                  .append(", schemaPath: ").append(schemaPathExpr).append(" + \"/enum\"});\n");
                sb.append(indent).append("}\n");
            }
            
            case ElementsNode el -> {
                // Type guard
                sb.append(indent).append("if (!Array.isArray(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({instancePath: ").append(pathExpr)
                  .append(", schemaPath: ").append(schemaPathExpr).append("});\n");
                sb.append(indent).append("} else {\n");
                
                // Loop over elements
                sb.append(indent).append("    for (let i = 0; i < ").append(valueExpr).append(".length; i++) {\n");
                final String elemValue = valueExpr + "[i]";
                final String elemPath = pathExpr + " + \"/\" + i";
                final String elemSchemaPath = schemaPathExpr + " + \"/elements\"";
                
                if (isLeafNode(el.schema())) {
                    // Inline leaf validation
                    generateNodeValidation(sb, el.schema(), ctx, elemValue, elemPath, elemSchemaPath, indent + "        ", null);
                } else {
                    // Complex schema - needs inline function
                    final String fnName = getInlineFunctionName(el.schema(), ctx);
                    sb.append(indent).append("        ").append(fnName).append("(")
                      .append(elemValue).append(", errors, ").append(elemPath)
                      .append(", ").append(elemSchemaPath).append(");\n");
                }
                
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
            
            case PropertiesNode pn -> {
                // Type guard
                sb.append(indent).append("if (").append(valueExpr).append(" === null || typeof ")
                  .append(valueExpr).append(" !== \"object\" || Array.isArray(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({instancePath: ").append(pathExpr)
                  .append(", schemaPath: ").append(schemaPathExpr).append("});\n");
                sb.append(indent).append("} else {\n");
                
                // Required properties
                for (var entry : pn.properties().entrySet()) {
                    final String key = entry.getKey();
                    final JtdNode propSchema = entry.getValue();
                    
                    // Skip discriminator key if we're in a discriminator variant
                    if (discriminatorKey != null && key.equals(discriminatorKey)) {
                        continue;
                    }
                    
                    sb.append(indent).append("    if (!(\"").append(key).append("\" in ")
                      .append(valueExpr).append(")) {\n");
                    sb.append(indent).append("        errors.push({instancePath: ").append(pathExpr)
                      .append(", schemaPath: ").append(schemaPathExpr).append(" + \"/properties/")
                      .append(jsonPointerEscape(key)).append("\"});\n");
                    sb.append(indent).append("    }\n");
                    
                    // Validate value if present
                    sb.append(indent).append("    if (\"").append(key).append("\" in ")
                      .append(valueExpr).append(") {\n");
                    final String propValue = valueExpr + "[\"" + key + "\"]";
                    final String propPath = pathExpr + " + \"/" + jsonPointerEscape(key) + "\"";
                    final String propSchemaPath = schemaPathExpr + " + \"/properties/" + jsonPointerEscape(key) + "\"";
                    
                    if (isLeafNode(propSchema)) {
                        generateNodeValidation(sb, propSchema, ctx, propValue, propPath, propSchemaPath, indent + "        ", null);
                    } else {
                        final String fnName = getInlineFunctionName(propSchema, ctx);
                        sb.append(indent).append("        ").append(fnName).append("(")
                          .append(propValue).append(", errors, ").append(propPath)
                          .append(", ").append(propSchemaPath).append(");\n");
                    }
                    sb.append(indent).append("    }\n");
                }
                
                // Optional properties
                for (var entry : pn.optionalProperties().entrySet()) {
                    final String key = entry.getKey();
                    final JtdNode propSchema = entry.getValue();
                    
                    // Skip discriminator key if we're in a discriminator variant
                    if (discriminatorKey != null && key.equals(discriminatorKey)) {
                        continue;
                    }
                    
                    sb.append(indent).append("    if (\"").append(key).append("\" in ")
                      .append(valueExpr).append(") {\n");
                    final String propValue = valueExpr + "[\"" + key + "\"]";
                    final String propPath = pathExpr + " + \"/" + jsonPointerEscape(key) + "\"";
                    final String propSchemaPath = schemaPathExpr + " + \"/optionalProperties/" + jsonPointerEscape(key) + "\"";
                    
                    if (isLeafNode(propSchema)) {
                        generateNodeValidation(sb, propSchema, ctx, propValue, propPath, propSchemaPath, indent + "        ", null);
                    } else {
                        final String fnName = getInlineFunctionName(propSchema, ctx);
                        sb.append(indent).append("        ").append(fnName).append("(")
                          .append(propValue).append(", errors, ").append(propPath)
                          .append(", ").append(propSchemaPath).append(");\n");
                    }
                    sb.append(indent).append("    }\n");
                }
                
                // Additional properties check (if not allowed)
                if (!pn.additionalProperties()) {
                    // Build list of allowed keys (including discriminator key if applicable)
                    final Set<String> allowedKeys = new HashSet<>(pn.properties().keySet());
                    allowedKeys.addAll(pn.optionalProperties().keySet());
                    
                    if (discriminatorKey != null) {
                        allowedKeys.add(discriminatorKey);
                    }
                    
                    sb.append(indent).append("    for (const k in ").append(valueExpr).append(") {\n");
                    sb.append(indent).append("        if (").append(buildKeyCheck("k", allowedKeys)).append(") {\n");
                    sb.append(indent).append("            errors.push({instancePath: ").append(pathExpr)
                      .append(" + \"/\" + k, schemaPath: ").append(schemaPathExpr).append("});\n");
                    sb.append(indent).append("        }\n");
                    sb.append(indent).append("    }\n");
                }
                
                sb.append(indent).append("}\n");
            }
            
            case ValuesNode vn -> {
                // Type guard
                sb.append(indent).append("if (").append(valueExpr).append(" === null || typeof ")
                  .append(valueExpr).append(" !== \"object\" || Array.isArray(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({instancePath: ").append(pathExpr)
                  .append(", schemaPath: ").append(schemaPathExpr).append("});\n");
                sb.append(indent).append("} else {\n");
                
                // Loop over values
                sb.append(indent).append("    for (const k in ").append(valueExpr).append(") {\n");
                final String valValue = valueExpr + "[k]";
                final String valPath = pathExpr + " + \"/\" + k";
                final String valSchemaPath = schemaPathExpr + " + \"/values\"";
                
                if (isLeafNode(vn.schema())) {
                    generateNodeValidation(sb, vn.schema(), ctx, valValue, valPath, valSchemaPath, indent + "        ", null);
                } else {
                    final String fnName = getInlineFunctionName(vn.schema(), ctx);
                    sb.append(indent).append("        ").append(fnName).append("(")
                      .append(valValue).append(", errors, ").append(valPath)
                      .append(", ").append(valSchemaPath).append(");\n");
                }
                
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
            
            case DiscriminatorNode dn -> {
                // 5-step validation per RFC 8927
                sb.append(indent).append("if (").append(valueExpr).append(" === null || typeof ")
                  .append(valueExpr).append(" !== \"object\" || Array.isArray(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({instancePath: ").append(pathExpr)
                  .append(", schemaPath: ").append(schemaPathExpr).append("});\n");
                sb.append(indent).append("} else if (!(\"").append(dn.discriminator()).append("\" in ")
                  .append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({instancePath: ").append(pathExpr)
                  .append(", schemaPath: ").append(schemaPathExpr).append("});\n");
                sb.append(indent).append("} else if (typeof ").append(valueExpr).append("[\"").append(dn.discriminator())
                  .append("\"] !== \"string\") {\n");
                sb.append(indent).append("    errors.push({instancePath: ").append(pathExpr)
                  .append(" + \"/").append(dn.discriminator()).append("\", schemaPath: ").append(schemaPathExpr)
                  .append(" + \"/discriminator\"});\n");
                sb.append(indent).append("} else {\n");
                sb.append(indent).append("    const tag = ").append(valueExpr).append("[\"").append(dn.discriminator()).append("\"];\n");
                
                // Switch on tag
                boolean first = true;
                for (var entry : dn.mapping().entrySet()) {
                    final String tagValue = entry.getKey();
                    final JtdNode variantSchema = entry.getValue();
                    
                    if (first) {
                        sb.append(indent).append("    if (tag === ").append(jsString(tagValue)).append(") {\n");
                        first = false;
                    } else {
                        sb.append(indent).append("    } else if (tag === ").append(jsString(tagValue)).append(") {\n");
                    }
                    
                    // Generate variant validation with discriminator exemption
                    generateNodeValidation(sb, variantSchema, ctx, valueExpr, pathExpr, 
                        schemaPathExpr + " + \"/mapping/" + jsonPointerEscape(tagValue) + "\"", 
                        indent + "        ", dn.discriminator());
                }
                
                sb.append(indent).append("    } else {\n");
                sb.append(indent).append("        errors.push({instancePath: ").append(pathExpr)
                  .append(" + \"/").append(dn.discriminator()).append("\", schemaPath: ").append(schemaPathExpr)
                  .append(" + \"/mapping\"});\n");
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
            
            case RefNode rn -> {
                sb.append(indent).append("validate_").append(toSafeName(rn.ref())).append("(")
                  .append(valueExpr).append(", errors, ").append(pathExpr).append(", ").append(schemaPathExpr).append(");\n");
            }
        }
    }

    private static void generateTypeCheck(StringBuilder sb, String type, String valueExpr, 
            String pathExpr, String schemaPathExpr, String indent) {
        
        final String check = switch (type) {
            case "boolean" -> "typeof " + valueExpr + " === \"boolean\"";
            case "string" -> "typeof " + valueExpr + " === \"string\"";
            case "timestamp" -> "typeof " + valueExpr + " === \"string\" && /^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:(\\d{2}|60)(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$/.test(" + valueExpr + ")";
            case "float32", "float64" -> "typeof " + valueExpr + " === \"number\" && Number.isFinite(" + valueExpr + ")";
            case "int8" -> "typeof " + valueExpr + " === \"number\" && Number.isInteger(" + valueExpr + ") && " + valueExpr + " >= -128 && " + valueExpr + " <= 127";
            case "uint8" -> "typeof " + valueExpr + " === \"number\" && Number.isInteger(" + valueExpr + ") && " + valueExpr + " >= 0 && " + valueExpr + " <= 255";
            case "int16" -> "typeof " + valueExpr + " === \"number\" && Number.isInteger(" + valueExpr + ") && " + valueExpr + " >= -32768 && " + valueExpr + " <= 32767";
            case "uint16" -> "typeof " + valueExpr + " === \"number\" && Number.isInteger(" + valueExpr + ") && " + valueExpr + " >= 0 && " + valueExpr + " <= 65535";
            case "int32" -> "typeof " + valueExpr + " === \"number\" && Number.isInteger(" + valueExpr + ") && " + valueExpr + " >= -2147483648 && " + valueExpr + " <= 2147483647";
            case "uint32" -> "typeof " + valueExpr + " === \"number\" && Number.isInteger(" + valueExpr + ") && " + valueExpr + " >= 0 && " + valueExpr + " <= 4294967295";
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
        
        sb.append(indent).append("if (!(").append(check).append(")) {\n");
        sb.append(indent).append("    errors.push({instancePath: ").append(pathExpr)
          .append(", schemaPath: ").append(schemaPathExpr).append(" + \"/type\"});\n");
        sb.append(indent).append("}\n");
    }

    private static boolean isLeafNode(JtdNode node) {
        return node instanceof TypeNode || node instanceof EnumNode || node instanceof EmptyNode || node instanceof RefNode;
    }

    private static String getInlineFunctionName(JtdNode node, RenderContext ctx) {
        // Check if this node already has a function name assigned
        for (var entry : ctx.generatedInlineFunctions.entrySet()) {
            if (entry.getValue() == node) {
                return entry.getKey();
            }
        }
        // Create new unique function name using counter (not hashCode - avoids collisions)
        final String name = "validate_inline_" + (ctx.inlineCounter++);
        ctx.generatedInlineFunctions.put(name, node);
        return name;
    }

    private static void generateInlineFunctions(StringBuilder sb, RenderContext ctx) {
        // Keep generating until no new inline functions are added
        // (inline functions can reference other inline functions)
        var processed = new HashSet<String>();
        boolean changed;
        do {
            changed = false;
            var entries = new ArrayList<>(ctx.generatedInlineFunctions.entrySet());
            for (var entry : entries) {
                final String fnName = entry.getKey();
                if (processed.contains(fnName)) {
                    continue;
                }
                processed.add(fnName);
                changed = true;
                
                final JtdNode node = entry.getValue();
                sb.append("function ").append(fnName).append("(v, errors, p, sp) {\n");
                generateNodeValidation(sb, node, ctx, "v", "p", "sp", "    ", null);
                sb.append("}\n\n");
            }
        } while (changed);
    }

    private static String getDiscriminatorKey(PropertiesNode pn) {
        // We need to track which discriminator this properties node belongs to
        // For now, this is a placeholder - we'd need to track this during generation
        return null;
    }

    private static String buildKeyCheck(String varName, Set<String> allowedKeys) {
        if (allowedKeys.isEmpty()) {
            return "true"; // No keys allowed, everything is extra
        }
        
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String key : allowedKeys) {
            if (!first) sb.append(" && ");
            sb.append(varName).append(" !== \"").append(key).append("\"");
            first = false;
        }
        return sb.toString();
    }

    private static String findEnumConst(Map<String, List<String>> enumConsts, List<String> values) {
        for (var e : enumConsts.entrySet()) {
            if (e.getValue().equals(values)) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("Enum values not found: " + values);
    }

    private static String toSafeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String jsonPointerEscape(String s) {
        return s.replace("~", "~0").replace("/", "~1");
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static String jsStringArray(List<String> values) {
        final var sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(jsString(values.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static class RenderContext {
        String sha256Hex;
        String shaPrefix8;
        String schemaId;
        int enumCounter = 1;
        int inlineCounter = 0;
        final Map<String, List<String>> enumConstants = new LinkedHashMap<>();
        final Map<String, JtdNode> generatedInlineFunctions = new LinkedHashMap<>();
    }
}
