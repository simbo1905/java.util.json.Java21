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
            JsonSchema.OneOfSchema,
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

    /// Options for schema compilation
    ///
    /// @param assertFormats whether to enable format assertion validation
    record Options(boolean assertFormats) {
        /// Default options with format assertion disabled
        static final Options DEFAULT = new Options(false);
    }

    /// Factory method to create schema from JSON Schema document
    ///
    /// @param schemaJson JSON Schema document as JsonValue
    /// @return Immutable JsonSchema instance
    /// @throws IllegalArgumentException if schema is invalid
    static JsonSchema compile(JsonValue schemaJson) {
        Objects.requireNonNull(schemaJson, "schemaJson");
        return SchemaCompiler.compile(schemaJson, Options.DEFAULT);
    }

    /// Factory method to create schema from JSON Schema document with options
    ///
    /// @param schemaJson JSON Schema document as JsonValue
    /// @param options compilation options
    /// @return Immutable JsonSchema instance
    /// @throws IllegalArgumentException if schema is invalid
    static JsonSchema compile(JsonValue schemaJson, Options options) {
        Objects.requireNonNull(schemaJson, "schemaJson");
        Objects.requireNonNull(options, "options");
        return SchemaCompiler.compile(schemaJson, options);
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
        Integer maxProperties,
        Map<Pattern, JsonSchema> patternProperties,
        JsonSchema propertyNames,
        Map<String, Set<String>> dependentRequired,
        Map<String, JsonSchema> dependentSchemas
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

            // Handle dependentRequired
            if (dependentRequired != null) {
                for (var entry : dependentRequired.entrySet()) {
                    String triggerProp = entry.getKey();
                    Set<String> requiredDeps = entry.getValue();
                    
                    // If trigger property is present, check all dependent properties
                    if (obj.members().containsKey(triggerProp)) {
                        for (String depProp : requiredDeps) {
                            if (!obj.members().containsKey(depProp)) {
                                errors.add(new ValidationError(path, "Property '" + triggerProp + "' requires property '" + depProp + "' (dependentRequired)"));
                            }
                        }
                    }
                }
            }

            // Handle dependentSchemas
            if (dependentSchemas != null) {
                for (var entry : dependentSchemas.entrySet()) {
                    String triggerProp = entry.getKey();
                    JsonSchema depSchema = entry.getValue();
                    
                    // If trigger property is present, apply the dependent schema
                    if (obj.members().containsKey(triggerProp)) {
                        if (depSchema == BooleanSchema.FALSE) {
                            errors.add(new ValidationError(path, "Property '" + triggerProp + "' forbids object unless its dependent schema is satisfied (dependentSchemas=false)"));
                        } else if (depSchema != BooleanSchema.TRUE) {
                            // Apply the dependent schema to the entire object
                            stack.push(new ValidationFrame(path, depSchema, json));
                        }
                    }
                }
            }

            // Validate property names if specified
            if (propertyNames != null) {
                for (String propName : obj.members().keySet()) {
                    String namePath = path.isEmpty() ? propName : path + "." + propName;
                    JsonValue nameValue = Json.parse("\"" + propName + "\"");
                    ValidationResult nameResult = propertyNames.validateAt(namePath + "(name)", nameValue, stack);
                    if (!nameResult.valid()) {
                        errors.add(new ValidationError(namePath, "Property name violates propertyNames"));
                    }
                }
            }

            // Validate each property with correct precedence
            for (var entry : obj.members().entrySet()) {
                String propName = entry.getKey();
                JsonValue propValue = entry.getValue();
                String propPath = path.isEmpty() ? propName : path + "." + propName;

                // Track if property was handled by properties or patternProperties
                boolean handledByProperties = false;
                boolean handledByPattern = false;

                // 1. Check if property is in properties (highest precedence)
                JsonSchema propSchema = properties.get(propName);
                if (propSchema != null) {
                    stack.push(new ValidationFrame(propPath, propSchema, propValue));
                    handledByProperties = true;
                }

                // 2. Check all patternProperties that match this property name
                if (patternProperties != null) {
                    for (var patternEntry : patternProperties.entrySet()) {
                        Pattern pattern = patternEntry.getKey();
                        JsonSchema patternSchema = patternEntry.getValue();
                        if (pattern.matcher(propName).find()) { // unanchored find semantics
                            stack.push(new ValidationFrame(propPath, patternSchema, propValue));
                            handledByPattern = true;
                        }
                    }
                }

                // 3. If property wasn't handled by properties or patternProperties, apply additionalProperties
                if (!handledByProperties && !handledByPattern) {
                    if (additionalProperties != null) {
                        if (additionalProperties == BooleanSchema.FALSE) {
                            // Handle additionalProperties: false - reject unmatched properties
                            errors.add(new ValidationError(propPath, "Additional properties not allowed"));
                        } else if (additionalProperties != BooleanSchema.TRUE) {
                            // Apply the additionalProperties schema (not true/false boolean schemas)
                            stack.push(new ValidationFrame(propPath, additionalProperties, propValue));
                        }
                    }
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
        Pattern pattern,
        FormatValidator formatValidator,
        boolean assertFormats
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

            // Check format validation (only when format assertion is enabled)
            if (formatValidator != null && assertFormats) {
                if (!formatValidator.test(value)) {
                    String formatName = formatValidator instanceof Format format ? format.name().toLowerCase().replace("_", "-") : "unknown";
                    errors.add(new ValidationError(path, "Invalid format '" + formatName + "'"));
                }
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

    /// Boolean schema - validates boolean values
    record BooleanSchema() implements JsonSchema {
        /// Singleton instances for boolean sub-schema handling
        static final BooleanSchema TRUE = new BooleanSchema();
        static final BooleanSchema FALSE = new BooleanSchema();
        
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            // For boolean subschemas, FALSE always fails, TRUE always passes
            if (this == FALSE) {
                return ValidationResult.failure(List.of(
                    new ValidationError(path, "Schema should not match")
                ));
            }
            if (this == TRUE) {
                return ValidationResult.success();
            }
            // Regular boolean validation for normal boolean schemas
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
    record RefSchema(String ref, java.util.function.Supplier<JsonSchema> targetSupplier) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            JsonSchema target = targetSupplier.get();
            if (target == null) {
                return ValidationResult.failure(List.of(new ValidationError(path, "Unresolved $ref: " + ref)));
            }
            return target.validateAt(path, json, stack);
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

    /// OneOf composition - must satisfy exactly one schema
    record OneOfSchema(List<JsonSchema> schemas) implements JsonSchema {
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            List<ValidationError> collected = new ArrayList<>();
            int validCount = 0;
            List<ValidationError> minimalErrors = null;

            for (JsonSchema schema : schemas) {
                // Create a separate validation stack for this branch
                Deque<ValidationFrame> branchStack = new ArrayDeque<>();
                List<ValidationError> branchErrors = new ArrayList<>();

                LOG.finest(() -> "ONEOF BRANCH START: " + schema.getClass().getSimpleName());
                branchStack.push(new ValidationFrame(path, schema, json));

                while (!branchStack.isEmpty()) {
                    ValidationFrame frame = branchStack.pop();
                    ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), branchStack);
                    if (!result.valid()) {
                        branchErrors.addAll(result.errors());
                    }
                }

                if (branchErrors.isEmpty()) {
                    validCount++;
                } else {
                    // Track minimal error set for zero-valid case
                    // Prefer errors that don't start with "Expected" (type mismatches) if possible
                    // In case of ties, prefer later branches (they tend to be more specific)
                    if (minimalErrors == null || 
                        (branchErrors.size() < minimalErrors.size()) ||
                        (branchErrors.size() == minimalErrors.size() && 
                         hasBetterErrorType(branchErrors, minimalErrors))) {
                        minimalErrors = branchErrors;
                    }
                }
                LOG.finest(() -> "ONEOF BRANCH END: " + branchErrors.size() + " errors, valid=" + branchErrors.isEmpty());
            }

            // Exactly one must be valid
            if (validCount == 1) {
                return ValidationResult.success();
            } else if (validCount == 0) {
                // Zero valid - return minimal error set
                return ValidationResult.failure(minimalErrors != null ? minimalErrors : List.of());
            } else {
                // Multiple valid - single error
                return ValidationResult.failure(List.of(
                    new ValidationError(path, "oneOf: multiple schemas matched (" + validCount + ")")
                ));
            }
        }
        
        private boolean hasBetterErrorType(List<ValidationError> newErrors, List<ValidationError> currentErrors) {
            // Prefer errors that don't start with "Expected" (type mismatches)
            boolean newHasTypeMismatch = newErrors.stream().anyMatch(e -> e.message().startsWith("Expected"));
            boolean currentHasTypeMismatch = currentErrors.stream().anyMatch(e -> e.message().startsWith("Expected"));
            
            // If new has type mismatch and current doesn't, current is better (keep current)
            if (newHasTypeMismatch && !currentHasTypeMismatch) {
                return false;
            }
            
            // If current has type mismatch and new doesn't, new is better (replace current)
            if (currentHasTypeMismatch && !newHasTypeMismatch) {
                return true;
            }
            
            // If both have type mismatches or both don't, prefer later branches
            // This is a simple heuristic
            return true;
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
        private static Options currentOptions;
        private static final Map<String, JsonSchema> compiledByPointer = new HashMap<>();
        private static final Map<String, JsonValue> rawByPointer = new HashMap<>();
        private static final Deque<String> resolutionStack = new ArrayDeque<>();

        private static void trace(String stage, JsonValue fragment) {
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer(() ->
                    String.format("[%s] %s", stage, fragment.toString()));
            }
        }

        /// JSON Pointer utility for RFC-6901 fragment navigation
        static Optional<JsonValue> navigatePointer(JsonValue root, String pointer) {
            if (pointer.isEmpty() || pointer.equals("#")) {
                return Optional.of(root);
            }
            
            // Remove leading # if present
            String path = pointer.startsWith("#") ? pointer.substring(1) : pointer;
            if (path.isEmpty()) {
                return Optional.of(root);
            }
            
            // Must start with /
            if (!path.startsWith("/")) {
                return Optional.empty();
            }
            
            JsonValue current = root;
            String[] tokens = path.substring(1).split("/");
            
            for (String token : tokens) {
                // Unescape ~1 -> / and ~0 -> ~
                String unescaped = token.replace("~1", "/").replace("~0", "~");
                
                if (current instanceof JsonObject obj) {
                    current = obj.members().get(unescaped);
                    if (current == null) {
                        return Optional.empty();
                    }
                } else if (current instanceof JsonArray arr) {
                    try {
                        int index = Integer.parseInt(unescaped);
                        if (index < 0 || index >= arr.values().size()) {
                            return Optional.empty();
                        }
                        current = arr.values().get(index);
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                } else {
                    return Optional.empty();
                }
            }
            
            return Optional.of(current);
        }

        /// Resolve $ref with cycle detection and memoization
        static JsonSchema resolveRef(String ref) {
            // Check for cycles
            if (resolutionStack.contains(ref)) {
                throw new IllegalArgumentException("Cyclic $ref: " + String.join(" -> ", resolutionStack) + " -> " + ref);
            }
            
            // Check memoized results
            JsonSchema cached = compiledByPointer.get(ref);
            if (cached != null) {
                return cached;
            }
            
            if (ref.equals("#")) {
                // Root reference - return RootRef instead of RefSchema to avoid cycles
                return new RootRef(() -> currentRootSchema);
            }
            
            // Resolve via JSON Pointer
            Optional<JsonValue> target = navigatePointer(rawByPointer.get(""), ref);
            if (target.isEmpty()) {
                throw new IllegalArgumentException("Unresolved $ref: " + ref);
            }
            
            // Check if it's a boolean schema
            JsonValue targetValue = target.get();
            if (targetValue instanceof JsonBoolean bool) {
                JsonSchema schema = bool.value() ? AnySchema.INSTANCE : new NotSchema(AnySchema.INSTANCE);
                compiledByPointer.put(ref, schema);
                return new RefSchema(ref, () -> schema);
            }
            
            // Push to resolution stack for cycle detection
            resolutionStack.push(ref);
            try {
                JsonSchema compiled = compileInternal(targetValue);
                compiledByPointer.put(ref, compiled);
                final JsonSchema finalCompiled = compiled;
                return new RefSchema(ref, () -> finalCompiled);
            } finally {
                resolutionStack.pop();
            }
        }

        /// Index schema fragments by JSON Pointer for efficient lookup
        static void indexSchemaByPointer(String pointer, JsonValue value) {
            rawByPointer.put(pointer, value);
            
            if (value instanceof JsonObject obj) {
                for (var entry : obj.members().entrySet()) {
                    String key = entry.getKey();
                    // Escape special characters in key
                    String escapedKey = key.replace("~", "~0").replace("/", "~1");
                    indexSchemaByPointer(pointer + "/" + escapedKey, entry.getValue());
                }
            } else if (value instanceof JsonArray arr) {
                for (int i = 0; i < arr.values().size(); i++) {
                    indexSchemaByPointer(pointer + "/" + i, arr.values().get(i));
                }
            }
        }

        static JsonSchema compile(JsonValue schemaJson) {
            return compile(schemaJson, Options.DEFAULT);
        }

        static JsonSchema compile(JsonValue schemaJson, Options options) {
            definitions.clear(); // Clear any previous definitions
            compiledByPointer.clear();
            rawByPointer.clear();
            resolutionStack.clear();
            currentRootSchema = null;
            currentOptions = options;
            
            // Handle format assertion controls
            boolean assertFormats = options.assertFormats();
            
            // Check system property first (read once during compile)
            String systemProp = System.getProperty("jsonschema.format.assertion");
            if (systemProp != null) {
                assertFormats = Boolean.parseBoolean(systemProp);
            }
            
            // Check root schema flag (highest precedence)
            if (schemaJson instanceof JsonObject obj) {
                JsonValue formatAssertionValue = obj.members().get("formatAssertion");
                if (formatAssertionValue instanceof JsonBoolean formatAssertionBool) {
                    assertFormats = formatAssertionBool.value();
                }
            }
            
            // Update options with final assertion setting
            currentOptions = new Options(assertFormats);
            
            // Index the raw schema by JSON Pointer
            indexSchemaByPointer("", schemaJson);
            
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
                    String pointer = "#/$defs/" + entry.getKey();
                    JsonSchema compiled = compileInternal(entry.getValue());
                    definitions.put(pointer, compiled);
                    compiledByPointer.put(pointer, compiled);
                }
            }

            // Handle $ref first
            JsonValue refValue = obj.members().get("$ref");
            if (refValue instanceof JsonString refStr) {
                String ref = refStr.value();
                trace("compile-ref", refValue);
                return resolveRef(ref);
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

            JsonValue oneOfValue = obj.members().get("oneOf");
            if (oneOfValue instanceof JsonArray oneOfArr) {
                trace("compile-oneof", oneOfValue);
                List<JsonSchema> schemas = new ArrayList<>();
                for (JsonValue item : oneOfArr.values()) {
                    schemas.add(compileInternal(item));
                }
                return new OneOfSchema(schemas);
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
                    || obj.members().containsKey("maxProperties")
                    || obj.members().containsKey("patternProperties")
                    || obj.members().containsKey("propertyNames")
                    || obj.members().containsKey("dependentRequired")
                    || obj.members().containsKey("dependentSchemas");

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
                    || obj.members().containsKey("maxLength")
                    || obj.members().containsKey("format");

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
                additionalProperties = addPropsBool.value() ? AnySchema.INSTANCE : BooleanSchema.FALSE;
            } else if (addPropsValue instanceof JsonObject addPropsObj) {
                additionalProperties = compileInternal(addPropsObj);
            }

            // Handle patternProperties
            Map<Pattern, JsonSchema> patternProperties = null;
            JsonValue patternPropsValue = obj.members().get("patternProperties");
            if (patternPropsValue instanceof JsonObject patternPropsObj) {
                patternProperties = new LinkedHashMap<>();
                for (var entry : patternPropsObj.members().entrySet()) {
                    String patternStr = entry.getKey();
                    Pattern pattern = Pattern.compile(patternStr);
                    JsonSchema schema = compileInternal(entry.getValue());
                    patternProperties.put(pattern, schema);
                }
            }

            // Handle propertyNames
            JsonSchema propertyNames = null;
            JsonValue propNamesValue = obj.members().get("propertyNames");
            if (propNamesValue != null) {
                propertyNames = compileInternal(propNamesValue);
            }

            Integer minProperties = getInteger(obj, "minProperties");
            Integer maxProperties = getInteger(obj, "maxProperties");

            // Handle dependentRequired
            Map<String, Set<String>> dependentRequired = null;
            JsonValue depReqValue = obj.members().get("dependentRequired");
            if (depReqValue instanceof JsonObject depReqObj) {
                dependentRequired = new LinkedHashMap<>();
                for (var entry : depReqObj.members().entrySet()) {
                    String triggerProp = entry.getKey();
                    JsonValue depsValue = entry.getValue();
                    if (depsValue instanceof JsonArray depsArray) {
                        Set<String> requiredProps = new LinkedHashSet<>();
                        for (JsonValue depItem : depsArray.values()) {
                            if (depItem instanceof JsonString depStr) {
                                requiredProps.add(depStr.value());
                            } else {
                                throw new IllegalArgumentException("dependentRequired values must be arrays of strings");
                            }
                        }
                        dependentRequired.put(triggerProp, requiredProps);
                    } else {
                        throw new IllegalArgumentException("dependentRequired values must be arrays");
                    }
                }
            }

            // Handle dependentSchemas
            Map<String, JsonSchema> dependentSchemas = null;
            JsonValue depSchValue = obj.members().get("dependentSchemas");
            if (depSchValue instanceof JsonObject depSchObj) {
                dependentSchemas = new LinkedHashMap<>();
                for (var entry : depSchObj.members().entrySet()) {
                    String triggerProp = entry.getKey();
                    JsonValue schemaValue = entry.getValue();
                    JsonSchema schema;
                    if (schemaValue instanceof JsonBoolean boolValue) {
                        schema = boolValue.value() ? AnySchema.INSTANCE : BooleanSchema.FALSE;
                    } else {
                        schema = compileInternal(schemaValue);
                    }
                    dependentSchemas.put(triggerProp, schema);
                }
            }

            return new ObjectSchema(properties, required, additionalProperties, minProperties, maxProperties, patternProperties, propertyNames, dependentRequired, dependentSchemas);
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

            // Handle format keyword
            FormatValidator formatValidator = null;
            boolean assertFormats = currentOptions != null && currentOptions.assertFormats();
            
            if (assertFormats) {
                JsonValue formatValue = obj.members().get("format");
                if (formatValue instanceof JsonString formatStr) {
                    String formatName = formatStr.value();
                    formatValidator = Format.byName(formatName);
                    if (formatValidator == null) {
                        LOG.fine("Unknown format: " + formatName);
                    }
                }
            }

            return new StringSchema(minLength, maxLength, pattern, formatValidator, assertFormats);
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
        // Track recursion depth per thread to avoid infinite loops
        private static final ThreadLocal<Integer> recursionDepth = ThreadLocal.withInitial(() -> 0);
        private static final int MAX_RECURSION_DEPTH = 50;
        
        @Override
        public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
            JsonSchema root = rootSupplier.get();
            if (root == null) {
                // No root yet (should not happen during validation), accept for now
                return ValidationResult.success();
            }
            
            // Check recursion depth to prevent infinite loops
            int depth = recursionDepth.get();
            if (depth >= MAX_RECURSION_DEPTH) {
                return ValidationResult.success(); // Break the cycle
            }
            
            try {
                recursionDepth.set(depth + 1);
                return root.validate(json);
            } finally {
                recursionDepth.set(depth);
            }
        }
    }

    /// Format validator interface for string format validation
    sealed interface FormatValidator {
        /// Test if the string value matches the format
        /// @param s the string to test
        /// @return true if the string matches the format, false otherwise
        boolean test(String s);
    }

    /// Built-in format validators
    enum Format implements FormatValidator {
        UUID {
            @Override
            public boolean test(String s) {
                try {
                    java.util.UUID.fromString(s);
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
        },
        
        EMAIL {
            @Override
            public boolean test(String s) {
                // Pragmatic RFC-5322-lite regex: reject whitespace, require TLD, no consecutive dots
                return s.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") && !s.contains("..");
            }
        },
        
        IPV4 {
            @Override
            public boolean test(String s) {
                String[] parts = s.split("\\.");
                if (parts.length != 4) return false;
                
                for (String part : parts) {
                    try {
                        int num = Integer.parseInt(part);
                        if (num < 0 || num > 255) return false;
                        // Check for leading zeros (except for 0 itself)
                        if (part.length() > 1 && part.startsWith("0")) return false;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                return true;
            }
        },
        
        IPV6 {
            @Override
            public boolean test(String s) {
                try {
                    // Use InetAddress to validate, but also check it contains ':' to distinguish from IPv4
                    java.net.InetAddress addr = java.net.InetAddress.getByName(s);
                    return s.contains(":");
                } catch (Exception e) {
                    return false;
                }
            }
        },
        
        URI {
            @Override
            public boolean test(String s) {
                try {
                    java.net.URI uri = new java.net.URI(s);
                    return uri.isAbsolute() && uri.getScheme() != null;
                } catch (Exception e) {
                    return false;
                }
            }
        },
        
        URI_REFERENCE {
            @Override
            public boolean test(String s) {
                try {
                    new java.net.URI(s);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        },
        
        HOSTNAME {
            @Override
            public boolean test(String s) {
                // Basic hostname validation: labels a-zA-Z0-9-, no leading/trailing -, label 1-63, total ≤255
                if (s.isEmpty() || s.length() > 255) return false;
                if (!s.contains(".")) return false; // Must have at least one dot
                
                String[] labels = s.split("\\.");
                for (String label : labels) {
                    if (label.isEmpty() || label.length() > 63) return false;
                    if (label.startsWith("-") || label.endsWith("-")) return false;
                    if (!label.matches("^[a-zA-Z0-9-]+$")) return false;
                }
                return true;
            }
        },
        
        DATE {
            @Override
            public boolean test(String s) {
                try {
                    java.time.LocalDate.parse(s);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        },
        
        TIME {
            @Override
            public boolean test(String s) {
                try {
                    // Try OffsetTime first (with timezone)
                    java.time.OffsetTime.parse(s);
                    return true;
                } catch (Exception e) {
                    try {
                        // Try LocalTime (without timezone)
                        java.time.LocalTime.parse(s);
                        return true;
                    } catch (Exception e2) {
                        return false;
                    }
                }
            }
        },
        
        DATE_TIME {
            @Override
            public boolean test(String s) {
                try {
                    // Try OffsetDateTime first (with timezone)
                    java.time.OffsetDateTime.parse(s);
                    return true;
                } catch (Exception e) {
                    try {
                        // Try LocalDateTime (without timezone)
                        java.time.LocalDateTime.parse(s);
                        return true;
                    } catch (Exception e2) {
                        return false;
                    }
                }
            }
        },
        
        REGEX {
            @Override
            public boolean test(String s) {
                try {
                    java.util.regex.Pattern.compile(s);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        
        /// Get format validator by name (case-insensitive)
        static FormatValidator byName(String name) {
            try {
                return Format.valueOf(name.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                return null; // Unknown format
            }
        }
    }
}
