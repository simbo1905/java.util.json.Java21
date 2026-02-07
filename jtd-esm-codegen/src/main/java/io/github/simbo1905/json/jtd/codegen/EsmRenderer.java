package io.github.simbo1905.json.jtd.codegen;

import java.util.*;

import static io.github.simbo1905.json.jtd.codegen.JtdAst.*;

/// Generates optimal ES2020 ESM validators using explicit stack-based validation.
/// 
/// Key features:
/// - Generates only the code needed (no unused helper functions)
/// - Uses explicit stack to avoid recursion and stack overflow
/// - Supports all RFC 8927 forms: elements, values, discriminator, nullable
/// - Inlines primitive checks, uses loops for arrays
/// - Generates separate functions for complex types
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

        // Analyze schema to determine what helpers we actually need
        analyzeSchema(schema.rootSchema(), ctx);
        for (var def : schema.definitions().values()) {
            analyzeSchema(def, ctx);
        }

        final var sb = new StringBuilder(8 * 1024);

        // Header
        sb.append("// ").append(schema.id()).append("-").append(shaPrefix8).append(".js\n");
        sb.append("// Generated from JTD schema: ").append(schema.id()).append("\n");
        sb.append("// SHA-256: ").append(sha256Hex).append(" (prefix: ").append(shaPrefix8).append(")\n");
        sb.append("\n");

        sb.append("const SCHEMA_ID = ").append(jsString(schema.id())).append(";\n\n");

        // Generate enum constants
        generateEnumConstants(sb, ctx);

        // Generate only the helper functions we need
        generateHelpers(sb, ctx);

        // Generate validation functions for definitions
        for (var entry : schema.definitions().entrySet()) {
            final String defName = entry.getKey();
            final JtdNode defNode = entry.getValue();
            generateDefinitionValidator(sb, defName, defNode, ctx);
        }

        // Generate inline validation functions for complex nested types
        ctx.inlineValidators.clear();
        ctx.inlineValidatorCounter = 0;
        collectInlineValidators(schema.rootSchema(), "root", ctx);
        // Remove root from inline validators - we'll generate it separately
        ctx.inlineValidators.remove("root");
        
        // Track discriminator mappings
        trackDiscriminatorMappings(schema.rootSchema(), ctx);
        
        for (var entry : ctx.inlineValidators.entrySet()) {
            final String key = entry.getKey();
            final JtdNode node = entry.getValue();
            final String discriminatorKey = ctx.discriminatorMappings.get(key);
            generateInlineValidator(sb, key, node, ctx, discriminatorKey);
        }

        // Generate the root validator (referenced by validate() but defined before it)
        generateInlineValidator(sb, "root", schema.rootSchema(), ctx, null);

        // Generate main validate function with stack-based approach
        sb.append("export function validate(instance) {\n");
        sb.append("    const errors = [];\n");
        sb.append("    const stack = [{ fn: validate_root, value: instance, path: '' }];\n\n");
        sb.append("    while (stack.length > 0) {\n");
        sb.append("        const frame = stack.pop();\n");
        sb.append("        frame.fn(frame.value, errors, frame.path, stack);\n");
        sb.append("    }\n\n");
        sb.append("    return errors;\n");
        sb.append("}\n\n");

        sb.append("export { SCHEMA_ID };\n");

        return sb.toString();
    }

    /// Analyzes schema to determine which helpers are needed
    private static void analyzeSchema(JtdNode node, RenderContext ctx) {
        switch (node) {
            case TypeNode tn -> {
                switch (tn.type()) {
                    case "timestamp" -> ctx.needsTimestampCheck = true;
                    case "int8", "uint8", "int16", "uint16", "int32", "uint32" -> ctx.needsIntRangeCheck = true;
                    case "float32", "float64" -> ctx.needsFloatCheck = true;
                }
            }
            case EnumNode en -> {
                final String constName = "ENUM_" + (ctx.enumConstants.size() + 1);
                ctx.enumConstants.put(constName, en.values());
            }
            case ElementsNode el -> analyzeSchema(el.schema(), ctx);
            case ValuesNode vn -> analyzeSchema(vn.schema(), ctx);
            case PropertiesNode pn -> {
                pn.properties().values().forEach(n -> analyzeSchema(n, ctx));
                pn.optionalProperties().values().forEach(n -> analyzeSchema(n, ctx));
            }
            case DiscriminatorNode dn -> {
                dn.mapping().values().forEach(n -> analyzeSchema(n, ctx));
            }
            case NullableNode nn -> analyzeSchema(nn.wrapped(), ctx);
            case RefNode ignored -> {
                // No analysis needed
            }
            case EmptyNode ignored -> {
                // No analysis needed
            }
        }
    }

    private static void collectInlineValidators(JtdNode node, String prefix, RenderContext ctx) {
        // Don't collect for simple types that can be validated inline
        if (isSimpleType(node)) {
            return;
        }

        // Already collected?
        final String key = prefix;
        if (ctx.inlineValidators.containsKey(key)) {
            return;
        }

        ctx.inlineValidators.put(key, node);
        ctx.inlineValidatorCounter++;

        // Recurse into children
        switch (node) {
            case ElementsNode el -> collectInlineValidators(el.schema(), key + "_elem" + ctx.inlineValidatorCounter, ctx);
            case ValuesNode vn -> collectInlineValidators(vn.schema(), key + "_val" + ctx.inlineValidatorCounter, ctx);
            case PropertiesNode pn -> {
                pn.properties().forEach((k, v) -> {
                    if (!isSimpleType(v)) {
                        collectInlineValidators(v, key + "_" + k + ctx.inlineValidatorCounter, ctx);
                    }
                });
                pn.optionalProperties().forEach((k, v) -> {
                    if (!isSimpleType(v)) {
                        collectInlineValidators(v, key + "_" + k + ctx.inlineValidatorCounter, ctx);
                    }
                });
            }
            case DiscriminatorNode dn -> {
                dn.mapping().forEach((k, v) -> {
                    if (!isSimpleType(v)) {
                        collectInlineValidators(v, key + "_" + k + ctx.inlineValidatorCounter, ctx);
                    }
                });
            }
            case NullableNode nn -> collectInlineValidators(nn.wrapped(), key + ctx.inlineValidatorCounter, ctx);
            default -> {
                // No children
            }
        }
    }

    /// Track which inline validators are discriminator mappings
    private static void trackDiscriminatorMappings(JtdNode node, RenderContext ctx) {
        trackDiscriminatorMappings(node, "root", null, ctx);
    }

    private static void trackDiscriminatorMappings(JtdNode node, String prefix, String parentDiscriminatorKey, RenderContext ctx) {
        switch (node) {
            case DiscriminatorNode dn -> {
                // Track mappings for this discriminator
                dn.mapping().forEach((tagValue, variantSchema) -> {
                    // Try to find the inline validator key for this variant
                    for (var entry : ctx.inlineValidators.entrySet()) {
                        if (entry.getValue().equals(variantSchema)) {
                            ctx.discriminatorMappings.put(entry.getKey(), dn.discriminator());
                        }
                    }
                });
                // Also recurse into variant schemas
                dn.mapping().forEach((tagValue, variantSchema) -> 
                    trackDiscriminatorMappings(variantSchema, prefix, dn.discriminator(), ctx));
            }
            case ElementsNode el -> trackDiscriminatorMappings(el.schema(), prefix + "_elem", parentDiscriminatorKey, ctx);
            case ValuesNode vn -> trackDiscriminatorMappings(vn.schema(), prefix + "_val", parentDiscriminatorKey, ctx);
            case PropertiesNode pn -> {
                pn.properties().forEach((k, v) -> trackDiscriminatorMappings(v, prefix + "_" + k, parentDiscriminatorKey, ctx));
                pn.optionalProperties().forEach((k, v) -> trackDiscriminatorMappings(v, prefix + "_" + k, parentDiscriminatorKey, ctx));
            }
            case NullableNode nn -> trackDiscriminatorMappings(nn.wrapped(), prefix, parentDiscriminatorKey, ctx);
            default -> {
                // No discriminator here
            }
        }
    }

    private static boolean isSimpleType(JtdNode node) {
        return node instanceof TypeNode || node instanceof EnumNode || node instanceof EmptyNode || node instanceof RefNode;
    }

    private static void generateEnumConstants(StringBuilder sb, RenderContext ctx) {
        int i = 1;
        for (var entry : ctx.enumConstants.entrySet()) {
            sb.append("const ").append(entry.getKey()).append(" = ")
              .append(jsStringArray(entry.getValue())).append(";\n");
            i++;
        }
        if (!ctx.enumConstants.isEmpty()) {
            sb.append("\n");
        }
    }

    private static void generateHelpers(StringBuilder sb, RenderContext ctx) {
        if (ctx.needsTimestampCheck) {
            sb.append("function isTimestamp(v) {\n");
            sb.append("    return typeof v === \"string\" && !Number.isNaN(Date.parse(v));\n");
            sb.append("}\n\n");
        }

        if (ctx.needsIntRangeCheck) {
            sb.append("function isIntInRange(v, min, max) {\n");
            sb.append("    return Number.isInteger(v) && v >= min && v <= max;\n");
            sb.append("}\n\n");
        }

        if (ctx.needsFloatCheck) {
            sb.append("function isFloat(v) {\n");
            sb.append("    return typeof v === \"number\" && Number.isFinite(v);\n");
            sb.append("}\n\n");
        }
    }

    private static void generateDefinitionValidator(StringBuilder sb, String defName, 
            JtdNode node, RenderContext ctx) {
        final String safeName = toSafeName(defName);
        
        sb.append("function validate_").append(safeName).append("(value, errors, path, stack) {\n");
        generateNodeValidation(sb, node, ctx, "value", "path", "    ", null);
        sb.append("}\n\n");
    }

    private static void generateInlineValidator(StringBuilder sb, String name, 
            JtdNode node, RenderContext ctx, String discriminatorKey) {
        sb.append("function validate_").append(name).append("(value, errors, path, stack) {\n");
        generateNodeValidation(sb, node, ctx, "value", "path", "    ", discriminatorKey);
        sb.append("}\n\n");
    }

    /// Generates validation logic for a node
    private static void generateNodeValidation(StringBuilder sb, JtdNode node, 
            RenderContext ctx, String valueExpr, String pathExpr, String indent, String discriminatorKey) {
        
        switch (node) {
            case EmptyNode en -> {
                // Accepts anything - no validation needed
            }
            
            case TypeNode tn -> {
                final String check = generateTypeCheck(tn.type(), valueExpr);
                sb.append(indent).append("if (!(").append(check).append(")) {\n");
                sb.append(indent).append("    errors.push({ instancePath: ").append(pathExpr).append(", schemaPath: '/type' });\n");
                sb.append(indent).append("}\n");
            }
            
            case EnumNode en -> {
                final String constName = findEnumConst(ctx.enumConstants, en.values());
                sb.append(indent).append("if (!").append(constName).append(".includes(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({ instancePath: ").append(pathExpr).append(", schemaPath: '/enum' });\n");
                sb.append(indent).append("}\n");
            }
            
            case ElementsNode el -> {
                // Check it's an array
                sb.append(indent).append("if (!Array.isArray(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({ instancePath: ").append(pathExpr).append(", schemaPath: '/elements' });\n");
                sb.append(indent).append("} else {\n");
                
                // Generate element validation inline or push to stack
                final JtdNode elemSchema = el.schema();
                if (isSimpleType(elemSchema)) {
                    // Inline simple element validation with loop
                    sb.append(indent).append("    for (let i = 0; i < ").append(valueExpr).append(".length; i++) {\n");
                    final String elemPath = pathExpr + " + '[' + i + ']'";
                    final String elemExpr = valueExpr + "[i]";
                    generateNodeValidation(sb, elemSchema, ctx, elemExpr, elemPath, indent + "        ", discriminatorKey);
                    sb.append(indent).append("    }\n");
                } else {
                    // Push elements onto stack for deferred validation
                    final String validatorKey = findInlineValidator(ctx, elemSchema);
                    sb.append(indent).append("    for (let i = ").append(valueExpr).append(".length - 1; i >= 0; i--) {\n");
                    sb.append(indent).append("        stack.push({\n");
                    sb.append(indent).append("            fn: validate_").append(validatorKey).append(",\n");
                    sb.append(indent).append("            value: ").append(valueExpr).append("[i],\n");
                    sb.append(indent).append("            path: ").append(pathExpr).append(" + '[' + i + ']'\n");
                    sb.append(indent).append("        });\n");
                    sb.append(indent).append("    }\n");
                }
                
                sb.append(indent).append("}\n");
            }
            
            case PropertiesNode pn -> {
                // Check it's an object
                sb.append(indent).append("if (").append(valueExpr).append(" === null || typeof ").append(valueExpr).append(" !== \"object\" || Array.isArray(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({ instancePath: ").append(pathExpr).append(", schemaPath: '' });\n");
                sb.append(indent).append("} else {\n");
                
                // Check required properties
                for (var entry : pn.properties().entrySet()) {
                    final String key = entry.getKey();
                    final JtdNode subNode = entry.getValue();
                    final String childPath = pathExpr + " + '/" + pointerEscape(key) + "'";
                    final String childExpr = valueExpr + "['" + key + "']";
                    
                    sb.append(indent).append("    if (!('").append(key).append("' in ").append(valueExpr).append(")) {\n");
                    sb.append(indent).append("        errors.push({ instancePath: ").append(pathExpr).append(", schemaPath: '/properties/").append(pointerEscape(key)).append("' });\n");
                    sb.append(indent).append("    } else {\n");
                    
                    if (isSimpleType(subNode)) {
                        generateNodeValidation(sb, subNode, ctx, childExpr, childPath, indent + "        ", discriminatorKey);
                    } else {
                        final String validatorKey = findInlineValidator(ctx, subNode);
                        sb.append(indent).append("        stack.push({\n");
                        sb.append(indent).append("            fn: validate_").append(validatorKey).append(",\n");
                        sb.append(indent).append("            value: ").append(childExpr).append(",\n");
                        sb.append(indent).append("            path: ").append(childPath).append("\n");
                        sb.append(indent).append("        });\n");
                    }
                    
                    sb.append(indent).append("    }\n");
                }
                
                // Check optional properties
                for (var entry : pn.optionalProperties().entrySet()) {
                    final String key = entry.getKey();
                    final JtdNode subNode = entry.getValue();
                    final String childPath = pathExpr + " + '/" + pointerEscape(key) + "'";
                    final String childExpr = valueExpr + "['" + key + "']";
                    
                    sb.append(indent).append("    if ('").append(key).append("' in ").append(valueExpr).append(") {\n");
                    
                    if (isSimpleType(subNode)) {
                        generateNodeValidation(sb, subNode, ctx, childExpr, childPath, indent + "        ", discriminatorKey);
                    } else {
                        final String validatorKey = findInlineValidator(ctx, subNode);
                        sb.append(indent).append("        stack.push({\n");
                        sb.append(indent).append("            fn: validate_").append(validatorKey).append(",\n");
                        sb.append(indent).append("            value: ").append(childExpr).append(",\n");
                        sb.append(indent).append("            path: ").append(childPath).append("\n");
                        sb.append(indent).append("        });\n");
                    }
                    
                    sb.append(indent).append("    }\n");
                }
                
                // Check additional properties if not allowed
                if (!pn.additionalProperties()) {
                    sb.append(indent).append("    const allowed = new Set([");
                    boolean first = true;
                    for (var key : pn.properties().keySet()) {
                        if (!first) sb.append(", ");
                        sb.append("'").append(key).append("'");
                        first = false;
                    }
                    for (var key : pn.optionalProperties().keySet()) {
                        if (!first) sb.append(", ");
                        sb.append("'").append(key).append("'");
                        first = false;
                    }
                    // Add discriminator key if present (for discriminator mappings)
                    if (discriminatorKey != null) {
                        if (!first) sb.append(", ");
                        sb.append("'").append(discriminatorKey).append("'");
                    }
                    sb.append("]);\n");
                    sb.append(indent).append("    for (const key of Object.keys(").append(valueExpr).append(")) {\n");
                    sb.append(indent).append("        if (!allowed.has(key)) {\n");
                    sb.append(indent).append("            errors.push({ instancePath: ").append(pathExpr).append(" + '/' + key, schemaPath: '/additionalProperties' });\n");
                    sb.append(indent).append("        }\n");
                    sb.append(indent).append("    }\n");
                }
                
                sb.append(indent).append("}\n");
            }
            
            case ValuesNode vn -> {
                // Check it's an object
                sb.append(indent).append("if (").append(valueExpr).append(" === null || typeof ").append(valueExpr).append(" !== \"object\" || Array.isArray(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({ instancePath: ").append(pathExpr).append(", schemaPath: '/values' });\n");
                sb.append(indent).append("} else {\n");
                
                // Iterate over values
                final JtdNode valSchema = vn.schema();
                if (isSimpleType(valSchema)) {
                    // Inline simple value validation with loop
                    sb.append(indent).append("    for (const key of Object.keys(").append(valueExpr).append(")) {\n");
                    final String valPath = pathExpr + " + '/' + key";
                    final String valExpr = valueExpr + "[key]";
                    generateNodeValidation(sb, valSchema, ctx, valExpr, valPath, indent + "        ", discriminatorKey);
                    sb.append(indent).append("    }\n");
                } else {
                    // Push values onto stack for deferred validation
                    final String validatorKey = findInlineValidator(ctx, valSchema);
                    sb.append(indent).append("    const keys = Object.keys(").append(valueExpr).append(");\n");
                    sb.append(indent).append("    for (let i = keys.length - 1; i >= 0; i--) {\n");
                    sb.append(indent).append("        const key = keys[i];\n");
                    sb.append(indent).append("        stack.push({\n");
                    sb.append(indent).append("            fn: validate_").append(validatorKey).append(",\n");
                    sb.append(indent).append("            value: ").append(valueExpr).append("[key],\n");
                    sb.append(indent).append("            path: ").append(pathExpr).append(" + '/' + key\n");
                    sb.append(indent).append("        });\n");
                    sb.append(indent).append("    }\n");
                }
                
                sb.append(indent).append("}\n");
            }
            
            case DiscriminatorNode dn -> {
                // Check it's an object and has discriminator
                sb.append(indent).append("if (").append(valueExpr).append(" === null || typeof ").append(valueExpr).append(" !== \"object\" || Array.isArray(").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({ instancePath: ").append(pathExpr).append(", schemaPath: '' });\n");
                sb.append(indent).append("} else if (!('").append(dn.discriminator()).append("' in ").append(valueExpr).append(")) {\n");
                sb.append(indent).append("    errors.push({ instancePath: ").append(pathExpr).append(", schemaPath: '/discriminator' });\n");
                sb.append(indent).append("} else {\n");
                sb.append(indent).append("    const tag = ").append(valueExpr).append("['").append(dn.discriminator()).append("'];\n");
                
                // Switch on tag value
                boolean first = true;
                for (var entry : dn.mapping().entrySet()) {
                    final String tagValue = entry.getKey();
                    final JtdNode subNode = entry.getValue();
                    
                    if (first) {
                        sb.append(indent).append("    if (tag === '").append(tagValue).append("') {\n");
                        first = false;
                    } else {
                        sb.append(indent).append("    } else if (tag === '").append(tagValue).append("') {\n");
                    }
                    
                    if (isSimpleType(subNode)) {
                        generateNodeValidation(sb, subNode, ctx, valueExpr, pathExpr, indent + "        ", dn.discriminator());
                    } else {
                        final String validatorKey = findInlineValidator(ctx, subNode);
                        sb.append(indent).append("        stack.push({\n");
                        sb.append(indent).append("            fn: validate_").append(validatorKey).append(",\n");
                        sb.append(indent).append("            value: ").append(valueExpr).append(",\n");
                        sb.append(indent).append("            path: ").append(pathExpr).append("\n");
                        sb.append(indent).append("        });\n");
                    }
                }
                
                sb.append(indent).append("    } else {\n");
                sb.append(indent).append("        errors.push({ instancePath: ").append(pathExpr).append(" + '/").append(dn.discriminator()).append("', schemaPath: '/discriminator' });\n");
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
            
            case RefNode rn -> {
                sb.append(indent).append("validate_").append(toSafeName(rn.ref()))
                  .append("(").append(valueExpr).append(", errors, ").append(pathExpr).append(", stack);\n");
            }
            
            case NullableNode nn -> {
                sb.append(indent).append("if (").append(valueExpr).append(" !== null) {\n");
                generateNodeValidation(sb, nn.wrapped(), ctx, valueExpr, pathExpr, indent + "    ", discriminatorKey);
                sb.append(indent).append("}\n");
            }
        }
    }

    private static String generateTypeCheck(String type, String valueExpr) {
        return switch (type) {
            case "string" -> "typeof " + valueExpr + " === \"string\"";
            case "boolean" -> "typeof " + valueExpr + " === \"boolean\"";
            case "timestamp" -> "isTimestamp(" + valueExpr + ")";
            case "int8" -> "isIntInRange(" + valueExpr + ", -128, 127)";
            case "uint8" -> "isIntInRange(" + valueExpr + ", 0, 255)";
            case "int16" -> "isIntInRange(" + valueExpr + ", -32768, 32767)";
            case "uint16" -> "isIntInRange(" + valueExpr + ", 0, 65535)";
            case "int32" -> "isIntInRange(" + valueExpr + ", -2147483648, 2147483647)";
            case "uint32" -> "isIntInRange(" + valueExpr + ", 0, 4294967295)";
            case "float32", "float64" -> "isFloat(" + valueExpr + ")";
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    private static String findInlineValidator(RenderContext ctx, JtdNode node) {
        for (var entry : ctx.inlineValidators.entrySet()) {
            if (entry.getValue().equals(node)) {
                return entry.getKey();
            }
        }
        // If not found, it's a simple type - shouldn't happen
        throw new IllegalStateException("No inline validator found for: " + node.getClass().getSimpleName());
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

    private static String pointerEscape(String s) {
        return s.replace("~", "~0").replace("/", "~1");
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
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

    /// Context for tracking what's needed during rendering
    private static class RenderContext {
        String sha256Hex;
        String shaPrefix8;
        String schemaId;
        boolean needsTimestampCheck = false;
        boolean needsIntRangeCheck = false;
        boolean needsFloatCheck = false;
        final Map<String, List<String>> enumConstants = new LinkedHashMap<>();
        final Map<String, JtdNode> inlineValidators = new LinkedHashMap<>();
        final Map<String, String> discriminatorMappings = new LinkedHashMap<>();
        int inlineValidatorCounter = 0;
    }
}
