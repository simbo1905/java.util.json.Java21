/// Copyright (c) 2025 Simon Massey
///
/// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
///
/// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
///
/// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Single public sealed interface for JSON Schema validation.
///
/// All schema types are implemented as inner records within this interface,
/// preventing external implementations while providing a clean, immutable API.
///
/// ## Usage
/// ```java
/// // Compile schema once (thread-safe, reusable)
/// JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
///
/// // Validate JSON documents
/// ValidationResult result = schema.validate(Json.parse(jsonDoc));
///
/// if (!result.valid()) {
///     for (var error : result.errors()) {
///         System.out.println(error.path() + ": " + error.message());
///     }
/// }
/// ```
public sealed interface JsonSchema
    permits JsonSchema.Nothing,
            JsonSchema.ObjectSchema,
            JsonSchema.ArraySchema,
            JsonSchema.StringSchema,
            JsonSchema.NumberSchema,
            JsonSchema.BooleanSchema,
            JsonSchema.NullSchema,
            JsonSchema.AnySchema,
            JsonSchema.RefSchema,
            JsonSchema.AllOfSchema,
            JsonSchema.AnyOfSchema,
            JsonSchema.ConditionalSchema,
            JsonSchema.ConstSchema,
            JsonSchema.NotSchema,
            JsonSchema.RootRef,
            JsonSchema.EnumSchema {

    Logger LOG = Logger.getLogger(JsonSchema.class.getName());

    /// Prevents external implementations, ensuring all schema types are inner records
    enum Nothing implements JsonSchema {
        ;  // Empty enum - just used as a sealed interface permit

        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            throw new UnsupportedOperationException("Nothing enum should not be used for validation");
        }
    }

    /// Factory method to create schema from JSON Schema document
    ///
    /// @param schemaJson JSON Schema document as JsonValue
    /// @return Immutable JsonSchema instance
    /// @throws IllegalArgumentException if schema is invalid
    static JsonSchema compile(JsonValue schemaJson) {
        Objects.requireNonNull(schemaJson, "schemaJson");
        return SchemaCompiler.compile(schemaJson);
    }

    /// Validates JSON document against this schema
    ///
    /// @param json JSON value to validate
    /// @return ValidationResult with success/failure information
    default ValidationResult validate(JsonValue json) {
        Objects.requireNonNull(json, "json");
        List<ValidationError> errors = new ArrayList<>();
        Deque<ValidationFrame> stack = new ArrayDeque<>();
        stack.push(new ValidationFrame("", this, json));

        while (!stack.isEmpty()) {
            ValidationFrame frame = stack.pop();
            LOG.finest(() -> "POP " + frame.path() +
                          "   schema=" + frame.schema().getClass().getSimpleName());
            ValidationResult result = frame.schema.validateAt(frame.path, frame.json, stack);
            if (!result.valid()) {
                errors.addAll(result.errors());
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /// Internal validation method used by stack-based traversal
    ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack);

    /// Object schema with properties, required fields, and constraints
    record ObjectSchema(
        Map<String, JsonSchema> properties,
        Set<String> required,
        JsonSchema additionalProperties,
        Integer minProperties,
        Integer maxProperties
    ) implements JsonSchema {

        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            if (!(json instanceof JsonObject obj)) {
                return ValidationResult.failure(List.of(
                    new ValidationError(path, "Expected object")
                ));
            }

            List<ValidationError> errors = new ArrayList<>();

            // Check property count constraints
            int propCount = obj.members().size();
            if (minProperties != null && propCount < minProperties) {
                errors.add(new ValidationError(path, "Too few properties: expected at least " + minProperties));
            }
            if (maxProperties != null && propCount > maxProperties) {
                errors.add(new ValidationError(path, "Too many properties: expected at most " + maxProperties));
            }

            // Check required properties
            for (String reqProp : required) {
                if (!obj.members().containsKey(reqProp)) {
                    errors.add(new ValidationError(path, "Missing required property: " + reqProp));
                }
            }

            // Validate properties
            for (var entry : obj.members().entrySet()) {
                String propName = entry.getKey();
                JsonValue propValue = entry.getValue();
                String propPath = path.isEmpty() ? propName : path + "." + propName;

                JsonSchema propSchema = properties.get(propName);
                if (propSchema != null) {
                    stack.push(new ValidationFrame(propPath, propSchema, propValue));
                } else if (additionalProperties != null && additionalProperties != AnySchema.INSTANCE) {
                    stack.push(new ValidationFrame(propPath, additionalProperties, propValue));
                }
            }

            return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
        }
    }

    /// Array schema with item validation and constraints
    record ArraySchema(
        JsonSchema items,
        Integer minItems,
        Integer maxItems,
        Boolean uniqueItems,
        // NEW: Pack 2 array features
        List<JsonSchema> prefixItems,
        JsonSchema contains,
        Integer minContains,
        Integer maxContains
    ) implements JsonSchema {

        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            if (!(json instanceof JsonArray arr)) {
                return ValidationResult.failure(List.of(
                    new ValidationError(path, "Expected array")
                ));
            }

            List<ValidationError> errors = new ArrayList<>();
            int itemCount = arr.values().size();

            // Check item count constraints
            if (minItems != null && itemCount < minItems) {
                errors.add(new ValidationError(path, "Too few items: expected at least " + minItems));
            }
            if (maxItems != null && itemCount > maxItems) {
                errors.add(new ValidationError(path, "Too many items: expected at most " + maxItems));
            }

            // Check uniqueness if required (structural equality)
            if (uniqueItems != null && uniqueItems) {
                Set<String> seen = new HashSet<>();
                for (JsonValue item : arr.values()) {
                    String canonicalKey = canonicalize(item);
                    if (!seen.add(canonicalKey)) {
                        errors.add(new ValidationError(path, "Array items must be unique"));
                        break;
                    }
                }
            }

            // Validate prefixItems + items (tuple validation)
            if (prefixItems != null && !prefixItems.isEmpty()) {
                // Validate prefix items - fail if not enough items for all prefix positions
                for (int i = 0; i < prefixItems.size(); i++) {
                    if (i >= itemCount) {
                        errors.add(new ValidationError(path, "Array has too few items for prefixItems validation"));
                        break;
                    }
                    String itemPath = path + "[" + i + "]";
                    // Validate prefix items immediately to capture errors
                    ValidationResult prefixResult = prefixItems.get(i).validateAt(itemPath, arr.values().get(i), stack);
                    if (!prefixResult.valid()) {
                        errors.addAll(prefixResult.errors());
                    }
                }
                // Validate remaining items with items schema if present
                if (items != null && items != AnySchema.INSTANCE) {
                    for (int i = prefixItems.size(); i < itemCount; i++) {
                        String itemPath = path + "[" + i + "]";
                        stack.push(new ValidationFrame(itemPath, items, arr.values().get(i)));
                    }
                }
            } else if (items != null && items != AnySchema.INSTANCE) {
                // Original items validation (no prefixItems)
                int index = 0;
                for (JsonValue item : arr.values()) {
                    String itemPath = path + "[" + index + "]";
                    stack.push(new ValidationFrame(itemPath, items, item));
                    index++;
                }
            }

            // Validate contains / minContains / maxContains
            if (contains != null) {
                int matchCount = 0;
                for (JsonValue item : arr.values()) {
                    // Create isolated validation to check if item matches contains schema
                    Deque<ValidationFrame> tempStack = new ArrayDeque<>();
                    List<ValidationError> tempErrors = new ArrayList<>();
                    tempStack.push(new ValidationFrame("", contains, item));
                    
                    while (!tempStack.isEmpty()) {
                        ValidationFrame frame = tempStack.pop();
                        ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), tempStack);
                        if (!result.valid()) {
                            tempErrors.addAll(result.errors());
                        }
                    }
                    
                    if (tempErrors.isEmpty()) {
                        matchCount++;
                    }
                }
                
                int min = (minContains != null ? minContains : 1); // default min=1
                int max = (maxContains != null ? maxContains : Integer.MAX_VALUE); // default max=∞
                
                if (matchCount < min) {
                    errors.add(new ValidationError(path, "Array must contain at least " + min + " matching element(s)"));
                } else if (matchCount > max) {
                    errors.add(new ValidationError(path, "Array must contain at most " + max + " matching element(s)"));
                }
            }

            return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
        }
    }

    /// String schema with length, pattern, and enum constraints
    record StringSchema(
        Integer minLength,
        Integer maxLength,
        Pattern pattern
    ) implements JsonSchema {

        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            if (!(json instanceof JsonString str)) {
                return ValidationResult.failure(List.of(
                    new ValidationError(path, "Expected string")
                ));
            }

            String value = str.value();
            List<ValidationError> errors = new ArrayList<>();

            // Check length constraints
            int length = value.length();
            if (minLength != null && length < minLength) {
                errors.add(new ValidationError(path, "String too short: expected at least " + minLength + " characters"));
            }
            if (maxLength != null && length > maxLength) {
                errors.add(new ValidationError(path, "String too long: expected at most " + maxLength + " characters"));
            }

            // Check pattern (unanchored matching - uses find() instead of matches())
            if (pattern != null && !pattern.matcher(value).find()) {
                errors.add(new ValidationError(path, "Pattern mismatch"));
            }

            return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
        }
    }

    /// Number schema with range and multiple constraints
    record NumberSchema(
        BigDecimal minimum,
        BigDecimal maximum,
        BigDecimal multipleOf,
        Boolean exclusiveMinimum,
        Boolean exclusiveMaximum
    ) implements JsonSchema {

        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            if (!(json instanceof JsonNumber num)) {
                return ValidationResult.failure(List.of(
                    new ValidationError(path, "Expected number")
                ));
            }

            BigDecimal value = num.toNumber() instanceof BigDecimal bd ? bd : BigDecimal.valueOf(num.toNumber().doubleValue());
            List<ValidationError> errors = new ArrayList<>();

            // Check minimum
            if (minimum != null) {
                int comparison = value.compareTo(minimum);
                if (exclusiveMinimum != null && exclusiveMinimum && comparison <= 0) {
                    errors.add(new ValidationError(path, "Below minimum"));
                } else if (comparison < 0) {
                    errors.add(new ValidationError(path, "Below minimum"));
                }
            }

            // Check maximum
            if (maximum != null) {
                int comparison = value.compareTo(maximum);
                if (exclusiveMaximum != null && exclusiveMaximum && comparison >= 0) {
                    errors.add(new ValidationError(path, "Above maximum"));
                } else if (comparison > 0) {
                    errors.add(new ValidationError(path, "Above maximum"));
                }
            }

            // Check multipleOf
            if (multipleOf != null) {
                BigDecimal remainder = value.remainder(multipleOf);
                if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                    errors.add(new ValidationError(path, "Not multiple of " + multipleOf));
                }
            }

            return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
        }
    }

    /// Boolean schema - always valid for boolean values
    record BooleanSchema() implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            if (!(json instanceof JsonBoolean)) {
                return ValidationResult.failure(List.of(
                    new ValidationError(path, "Expected boolean")
                ));
            }
            return ValidationResult.success();
        }
    }

    /// Null schema - always valid for null values
    record NullSchema() implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            if (!(json instanceof JsonNull)) {
                return ValidationResult.failure(List.of(
                    new ValidationError(path, "Expected null")
                ));
            }
            return ValidationResult.success();
        }
    }

    /// Any schema - accepts all values
    record AnySchema() implements JsonSchema {
        static final AnySchema INSTANCE = new AnySchema();

        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            return ValidationResult.success();
        }
    }

    /// Reference schema for JSON Schema $ref
    record RefSchema(String ref) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            throw new UnsupportedOperationException("$ref resolution not implemented");
        }
    }

    /// AllOf composition - must satisfy all schemas
    record AllOfSchema(List<JsonSchema> schemas) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            // Push all subschemas onto the stack for validation
            for (JsonSchema schema : schemas) {
                stack.push(new ValidationFrame(path, schema, json));
            }
            return ValidationResult.success(); // Actual results emerge from stack processing
        }
    }

    /// AnyOf composition - must satisfy at least one schema
    record AnyOfSchema(List<JsonSchema> schemas) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            List<ValidationError> collected = new ArrayList<>();
            boolean anyValid = false;

            for (JsonSchema schema : schemas) {
                // Create a separate validation stack for this branch
                Deque<ValidationFrame> branchStack = new ArrayDeque<>();
                List<ValidationError> branchErrors = new ArrayList<>();

                LOG.finest(() -> "BRANCH START: " + schema.getClass().getSimpleName());
                branchStack.push(new ValidationFrame(path, schema, json));

                while (!branchStack.isEmpty()) {
                    ValidationFrame frame = branchStack.pop();
                    ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), branchStack);
                    if (!result.valid()) {
                        branchErrors.addAll(result.errors());
                    }
                }

                if (branchErrors.isEmpty()) {
                    anyValid = true;
                    break;
                }
                collected.addAll(branchErrors);
                LOG.finest(() -> "BRANCH END: " + branchErrors.size() + " errors");
            }

            return anyValid ? ValidationResult.success() : ValidationResult.failure(collected);
        }
    }

    /// If/Then/Else conditional schema
    record ConditionalSchema(JsonSchema ifSchema, JsonSchema thenSchema, JsonSchema elseSchema) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            // Step 1 - evaluate IF condition (still needs direct validation)
            ValidationResult ifResult = ifSchema.validate(json);

            // Step 2 - choose branch
            JsonSchema branch = ifResult.valid() ? thenSchema : elseSchema;

            LOG.finer(() -> String.format(
                "Conditional path=%s ifValid=%b branch=%s",
                path, ifResult.valid(),
                branch == null ? "none" : (ifResult.valid() ? "then" : "else")));

            // Step 3 - if there's a branch, push it onto the stack for later evaluation
            if (branch == null) {
                return ValidationResult.success();      // no branch → accept
            }

            // NEW: push branch onto SAME stack instead of direct call
            stack.push(new ValidationFrame(path, branch, json));
            return ValidationResult.success();          // real result emerges later
        }
    }

    /// Validation result types
    record ValidationResult(boolean valid, List<ValidationError> errors) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<ValidationError> errors) {
            return new ValidationResult(false, errors);
        }
    }

    record ValidationError(String path, String message) {}

    /// Validation frame for stack-based processing
    record ValidationFrame(String path, JsonSchema schema, JsonValue json) {}

    /// Canonicalization helper for structural equality in uniqueItems
    private static String canonicalize(JsonValue v) {
        if (v instanceof JsonObject o) {
            var keys = new ArrayList<>(o.members().keySet());
            Collections.sort(keys);
            var sb = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                String k = keys.get(i);
                if (i > 0) sb.append(',');
                sb.append('"').append(escapeJsonString(k)).append("\":").append(canonicalize(o.members().get(k)));
            }
            return sb.append('}').toString();
        } else if (v instanceof JsonArray a) {
            var sb = new StringBuilder("[");
            for (int i = 0; i < a.values().size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(canonicalize(a.values().get(i)));
            }
            return sb.append(']').toString();
        } else if (v instanceof JsonString s) {
            return "\"" + escapeJsonString(s.value()) + "\"";
        } else {
            // numbers/booleans/null: rely on stable toString from the Json* impls
            return v.toString();
        }
    }
    
    private static String escapeJsonString(String s) {
        if (s == null) return "null";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (ch < 0x20 || ch > 0x7e) {
                        result.append("\\u").append(String.format("%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
            }
        }
        return result.toString();
    }

    /// Internal schema compiler
    final class SchemaCompiler {
        private static final Map<String, JsonSchema> definitions = new HashMap<>();
        private static JsonSchema currentRootSchema;

        private static void trace(String stage, JsonValue fragment) {
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer(() ->
                    String.format("[%s] %s", stage, fragment.toString()));
            }
        }

        static JsonSchema compile(JsonValue schemaJson) {
            definitions.clear(); // Clear any previous definitions
            currentRootSchema = null;
            trace("compile-start", schemaJson);
            JsonSchema schema = compileInternal(schemaJson);
            currentRootSchema = schema; // Store the root schema for self-references
            return schema;
        }

        private static JsonSchema compileInternal(JsonValue schemaJson) {
            if (schemaJson instanceof JsonBoolean bool) {
                return bool.value() ? AnySchema.INSTANCE : new NotSchema(AnySchema.INSTANCE);
            }

            if (!(schemaJson instanceof JsonObject obj)) {
                throw new IllegalArgumentException("Schema must be an object or boolean");
            }

            // Process definitions first
            JsonValue defsValue = obj.members().get("$defs");
            if (defsValue instanceof JsonObject defsObj) {
                trace("compile-defs", defsValue);
                for (var entry : defsObj.members().entrySet()) {
                    definitions.put("#/$defs/" + entry.getKey(), compileInternal(entry.getValue()));
                }
            }

            // Handle $ref first
            JsonValue refValue = obj.members().get("$ref");
            if (refValue instanceof JsonString refStr) {
                String ref = refStr.value();
                trace("compile-ref", refValue);
                if (ref.equals("#")) {
                    // Lazily resolve to whatever the root schema becomes after compilation
                    return new RootRef(() -> currentRootSchema);
                }
                JsonSchema resolved = definitions.get(ref);
                if (resolved == null) {
                    throw new IllegalArgumentException("Unresolved $ref: " + ref);
                }
                return resolved;
            }

            // Handle composition keywords
            JsonValue allOfValue = obj.members().get("allOf");
            if (allOfValue instanceof JsonArray allOfArr) {
                trace("compile-allof", allOfValue);
                List<JsonSchema> schemas = new ArrayList<>();
                for (JsonValue item : allOfArr.values()) {
                    schemas.add(compileInternal(item));
                }
                return new AllOfSchema(schemas);
            }

            JsonValue anyOfValue = obj.members().get("anyOf");
            if (anyOfValue instanceof JsonArray anyOfArr) {
                trace("compile-anyof", anyOfValue);
                List<JsonSchema> schemas = new ArrayList<>();
                for (JsonValue item : anyOfArr.values()) {
                    schemas.add(compileInternal(item));
                }
                return new AnyOfSchema(schemas);
            }

            // Handle if/then/else
            JsonValue ifValue = obj.members().get("if");
            if (ifValue != null) {
                trace("compile-conditional", obj);
                JsonSchema ifSchema = compileInternal(ifValue);
                JsonSchema thenSchema = null;
                JsonSchema elseSchema = null;

                JsonValue thenValue = obj.members().get("then");
                if (thenValue != null) {
                    thenSchema = compileInternal(thenValue);
                }

                JsonValue elseValue = obj.members().get("else");
                if (elseValue != null) {
                    elseSchema = compileInternal(elseValue);
                }

                return new ConditionalSchema(ifSchema, thenSchema, elseSchema);
            }

            // Handle const
            JsonValue constValue = obj.members().get("const");
            if (constValue != null) {
                return new ConstSchema(constValue);
            }

            // Handle not
            JsonValue notValue = obj.members().get("not");
            if (notValue != null) {
                JsonSchema inner = compileInternal(notValue);
                return new NotSchema(inner);
            }

            // Detect keyword-based schema types for use in enum handling and fallback
            boolean hasObjectKeywords = obj.members().containsKey("properties")
                    || obj.members().containsKey("required")
                    || obj.members().containsKey("additionalProperties")
                    || obj.members().containsKey("minProperties")
                    || obj.members().containsKey("maxProperties");

            boolean hasArrayKeywords = obj.members().containsKey("items")
                    || obj.members().containsKey("minItems")
                    || obj.members().containsKey("maxItems")
                    || obj.members().containsKey("uniqueItems")
                    || obj.members().containsKey("prefixItems")
                    || obj.members().containsKey("contains")
                    || obj.members().containsKey("minContains")
                    || obj.members().containsKey("maxContains");

            boolean hasStringKeywords = obj.members().containsKey("pattern")
                    || obj.members().containsKey("minLength")
                    || obj.members().containsKey("maxLength");

            // Handle enum early (before type-specific compilation)
            JsonValue enumValue = obj.members().get("enum");
            if (enumValue instanceof JsonArray enumArray) {
                // Build base schema from type or heuristics
                JsonSchema baseSchema;
                
                // If type is specified, use it; otherwise infer from keywords
                JsonValue typeValue = obj.members().get("type");
                if (typeValue instanceof JsonString typeStr) {
                    baseSchema = switch (typeStr.value()) {
                        case "object" -> compileObjectSchema(obj);
                        case "array" -> compileArraySchema(obj);
                        case "string" -> compileStringSchema(obj);
                        case "number", "integer" -> compileNumberSchema(obj);
                        case "boolean" -> new BooleanSchema();
                        case "null" -> new NullSchema();
                        default -> AnySchema.INSTANCE;
                    };
                } else if (hasObjectKeywords) {
                    baseSchema = compileObjectSchema(obj);
                } else if (hasArrayKeywords) {
                    baseSchema = compileArraySchema(obj);
                } else if (hasStringKeywords) {
                    baseSchema = compileStringSchema(obj);
                } else {
                    baseSchema = AnySchema.INSTANCE;
                }
                
                // Build enum values set
                Set<JsonValue> allowedValues = new LinkedHashSet<>();
                for (JsonValue item : enumArray.values()) {
                    allowedValues.add(item);
                }
                
                return new EnumSchema(baseSchema, allowedValues);
            }

            // Handle type-based schemas
            JsonValue typeValue = obj.members().get("type");
            if (typeValue instanceof JsonString typeStr) {
                return switch (typeStr.value()) {
                    case "object" -> compileObjectSchema(obj);
                    case "array" -> compileArraySchema(obj);
                    case "string" -> compileStringSchema(obj);
                    case "number" -> compileNumberSchema(obj);
                    case "integer" -> compileNumberSchema(obj); // For now, treat integer as number
                    case "boolean" -> new BooleanSchema();
                    case "null" -> new NullSchema();
                    default -> AnySchema.INSTANCE;
                };
            } else if (typeValue instanceof JsonArray typeArray) {
                // Handle type arrays: ["string", "null", ...] - treat as anyOf
                List<JsonSchema> typeSchemas = new ArrayList<>();
                for (JsonValue item : typeArray.values()) {
                    if (item instanceof JsonString typeStr) {
                        JsonSchema typeSchema = switch (typeStr.value()) {
                            case "object" -> compileObjectSchema(obj);
                            case "array" -> compileArraySchema(obj);
                            case "string" -> compileStringSchema(obj);
                            case "number" -> compileNumberSchema(obj);
                            case "integer" -> compileNumberSchema(obj);
                            case "boolean" -> new BooleanSchema();
                            case "null" -> new NullSchema();
                            default -> AnySchema.INSTANCE;
                        };
                        typeSchemas.add(typeSchema);
                    } else {
                        throw new IllegalArgumentException("Type array must contain only strings");
                    }
                }
                if (typeSchemas.isEmpty()) {
                    return AnySchema.INSTANCE;
                } else if (typeSchemas.size() == 1) {
                    return typeSchemas.get(0);
                } else {
                    return new AnyOfSchema(typeSchemas);
                }
            } else {
                if (hasObjectKeywords) {
                    return compileObjectSchema(obj);
                } else if (hasArrayKeywords) {
                    return compileArraySchema(obj);
                } else if (hasStringKeywords) {
                    return compileStringSchema(obj);
                }
            }

            return AnySchema.INSTANCE;
        }

        private static JsonSchema compileObjectSchema(JsonObject obj) {
            Map<String, JsonSchema> properties = new LinkedHashMap<>();
            JsonValue propsValue = obj.members().get("properties");
            if (propsValue instanceof JsonObject propsObj) {
                for (var entry : propsObj.members().entrySet()) {
                    properties.put(entry.getKey(), compileInternal(entry.getValue()));
                }
            }

            Set<String> required = new LinkedHashSet<>();
            JsonValue reqValue = obj.members().get("required");
            if (reqValue instanceof JsonArray reqArray) {
                for (JsonValue item : reqArray.values()) {
                    if (item instanceof JsonString str) {
                        required.add(str.value());
                    }
                }
            }

            JsonSchema additionalProperties = AnySchema.INSTANCE;
            JsonValue addPropsValue = obj.members().get("additionalProperties");
            if (addPropsValue instanceof JsonBoolean addPropsBool) {
                additionalProperties = addPropsBool.value() ? AnySchema.INSTANCE : new NotSchema(AnySchema.INSTANCE);
            } else if (addPropsValue instanceof JsonObject addPropsObj) {
                additionalProperties = compileInternal(addPropsObj);
            }

            Integer minProperties = getInteger(obj, "minProperties");
            Integer maxProperties = getInteger(obj, "maxProperties");

            return new ObjectSchema(properties, required, additionalProperties, minProperties, maxProperties);
        }

        private static JsonSchema compileArraySchema(JsonObject obj) {
            JsonSchema items = AnySchema.INSTANCE;
            JsonValue itemsValue = obj.members().get("items");
            if (itemsValue != null) {
                items = compileInternal(itemsValue);
            }

            // Parse prefixItems (tuple validation)
            List<JsonSchema> prefixItems = null;
            JsonValue prefixItemsVal = obj.members().get("prefixItems");
            if (prefixItemsVal instanceof JsonArray arr) {
                prefixItems = new ArrayList<>(arr.values().size());
                for (JsonValue v : arr.values()) {
                    prefixItems.add(compileInternal(v));
                }
                prefixItems = List.copyOf(prefixItems);
            }

            // Parse contains schema
            JsonSchema contains = null;
            JsonValue containsVal = obj.members().get("contains");
            if (containsVal != null) {
                contains = compileInternal(containsVal);
            }

            // Parse minContains / maxContains
            Integer minContains = getInteger(obj, "minContains");
            Integer maxContains = getInteger(obj, "maxContains");

            Integer minItems = getInteger(obj, "minItems");
            Integer maxItems = getInteger(obj, "maxItems");
            Boolean uniqueItems = getBoolean(obj, "uniqueItems");

            return new ArraySchema(items, minItems, maxItems, uniqueItems, prefixItems, contains, minContains, maxContains);
        }

        private static JsonSchema compileStringSchema(JsonObject obj) {
            Integer minLength = getInteger(obj, "minLength");
            Integer maxLength = getInteger(obj, "maxLength");

            Pattern pattern = null;
            JsonValue patternValue = obj.members().get("pattern");
            if (patternValue instanceof JsonString patternStr) {
                pattern = Pattern.compile(patternStr.value());
            }

            return new StringSchema(minLength, maxLength, pattern);
        }

        private static JsonSchema compileNumberSchema(JsonObject obj) {
            BigDecimal minimum = getBigDecimal(obj, "minimum");
            BigDecimal maximum = getBigDecimal(obj, "maximum");
            BigDecimal multipleOf = getBigDecimal(obj, "multipleOf");
            Boolean exclusiveMinimum = getBoolean(obj, "exclusiveMinimum");
            Boolean exclusiveMaximum = getBoolean(obj, "exclusiveMaximum");
            
            // Handle numeric exclusiveMinimum/exclusiveMaximum (2020-12 spec)
            BigDecimal exclusiveMinValue = getBigDecimal(obj, "exclusiveMinimum");
            BigDecimal exclusiveMaxValue = getBigDecimal(obj, "exclusiveMaximum");
            
            // Normalize: if numeric exclusives are present, convert to boolean form
            if (exclusiveMinValue != null) {
                minimum = exclusiveMinValue;
                exclusiveMinimum = true;
            }
            if (exclusiveMaxValue != null) {
                maximum = exclusiveMaxValue;
                exclusiveMaximum = true;
            }

            return new NumberSchema(minimum, maximum, multipleOf, exclusiveMinimum, exclusiveMaximum);
        }

        private static Integer getInteger(JsonObject obj, String key) {
            JsonValue value = obj.members().get(key);
            if (value instanceof JsonNumber num) {
                Number n = num.toNumber();
                if (n instanceof Integer i) return i;
                if (n instanceof Long l) return l.intValue();
                if (n instanceof BigDecimal bd) return bd.intValue();
            }
            return null;
        }

        private static Boolean getBoolean(JsonObject obj, String key) {
            JsonValue value = obj.members().get(key);
            if (value instanceof JsonBoolean bool) {
                return bool.value();
            }
            return null;
        }

        private static BigDecimal getBigDecimal(JsonObject obj, String key) {
            JsonValue value = obj.members().get(key);
            if (value instanceof JsonNumber num) {
                Number n = num.toNumber();
                if (n instanceof BigDecimal) return (BigDecimal) n;
                if (n instanceof BigInteger) return new BigDecimal((BigInteger) n);
                return BigDecimal.valueOf(n.doubleValue());
            }
            return null;
        }
    }

    /// Const schema - validates that a value equals a constant
    record ConstSchema(JsonValue constValue) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            return json.equals(constValue) ?
                ValidationResult.success() :
                ValidationResult.failure(List.of(new ValidationError(path, "Value must equal const value")));
        }
    }

    /// Enum schema - validates that a value is in a set of allowed values
    record EnumSchema(JsonSchema baseSchema, Set<JsonValue> allowedValues) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            // First validate against base schema
            ValidationResult baseResult = baseSchema.validateAt(path, json, stack);
            if (!baseResult.valid()) {
                return baseResult;
            }
            
            // Then check if value is in enum
            if (!allowedValues.contains(json)) {
                return ValidationResult.failure(List.of(new ValidationError(path, "Not in enum")));
            }
            
            return ValidationResult.success();
        }
    }

    /// Not composition - inverts the validation result of the inner schema
    record NotSchema(JsonSchema schema) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            ValidationResult result = schema.validate(json);
            return result.valid() ?
                ValidationResult.failure(List.of(new ValidationError(path, "Schema should not match"))) :
                ValidationResult.success();
        }
    }

    /// Root reference schema that refers back to the root schema
    record RootRef(java.util.function.Supplier<JsonSchema> rootSupplier) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            JsonSchema root = rootSupplier.get();
            if (root == null) {
                // No root yet (should not happen during validation), accept for now
                return ValidationResult.success();
            }
            return root.validate(json); // Direct validation against root schema
        }
    }
}
