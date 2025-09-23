package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.*;
import java.util.regex.Pattern;

/// Object schema with properties, required fields, and constraints
public record ObjectSchema(
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
