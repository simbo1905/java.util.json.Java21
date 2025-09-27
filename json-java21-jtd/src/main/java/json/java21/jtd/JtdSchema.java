package json.java21.jtd;

import jdk.sandbox.java.util.json.*;

import java.util.List;

/// JTD Schema interface - validates JSON instances against JTD schemas
/// Following RFC 8927 specification with eight mutually-exclusive schema forms
public sealed interface JtdSchema {
  
  /// Validates a JSON instance against this schema
  /// @param instance The JSON value to validate
  /// @return ValidationResult containing errors if validation fails
  ValidationResult validate(JsonValue instance);
  
  /// Nullable schema wrapper - allows null values
  record NullableSchema(JtdSchema wrapped) implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      if (instance instanceof JsonNull) {
        return ValidationResult.success();
      }
      return wrapped.validate(instance);
    }
  }
  
  /// Empty schema - accepts any value (null, boolean, number, string, array, object)
  record EmptySchema() implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      // Empty schema accepts any JSON value
      return ValidationResult.success();
    }
  }
  
  /// Ref schema - references a definition in the schema's definitions
  record RefSchema(String ref) implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      // TODO: Implement ref resolution when definitions are supported
      return ValidationResult.success();
    }
  }
  
  /// Type schema - validates specific primitive types
  record TypeSchema(String type) implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      return switch (type) {
        case "boolean" -> validateBoolean(instance);
        case "string" -> validateString(instance);
        case "timestamp" -> validateTimestamp(instance);
        case "int8", "uint8", "int16", "uint16", "int32", "uint32" -> validateInteger(instance, type);
        case "float32", "float64" -> validateFloat(instance, type);
        default -> ValidationResult.failure(List.of(
            new ValidationError("unknown type: " + type)
        ));
      };
    }
    
    private ValidationResult validateBoolean(JsonValue instance) {
      if (instance instanceof JsonBoolean) {
        return ValidationResult.success();
      }
      return ValidationResult.failure(List.of(
          new ValidationError("expected boolean, got " + instance.getClass().getSimpleName())
      ));
    }
    
    private ValidationResult validateString(JsonValue instance) {
      if (instance instanceof JsonString) {
        return ValidationResult.success();
      }
      return ValidationResult.failure(List.of(
          new ValidationError("expected string, got " + instance.getClass().getSimpleName())
      ));
    }
    
    private ValidationResult validateTimestamp(JsonValue instance) {
      if (instance instanceof JsonString ignored) {
        // Basic RFC 3339 timestamp validation - must be a string
        // TODO: Add actual timestamp format validation
        return ValidationResult.success();
      }
      return ValidationResult.failure(List.of(
          new ValidationError("expected timestamp (string), got " + instance.getClass().getSimpleName())
      ));
    }
    
    private ValidationResult validateInteger(JsonValue instance, String type) {
      if (instance instanceof JsonNumber num) {
        Number value = num.toNumber();
        if (value instanceof Double d && d != Math.floor(d)) {
          return ValidationResult.failure(List.of(
              new ValidationError("expected integer, got float")
          ));
        }
        // TODO: Add range validation for different integer types
        return ValidationResult.success();
      }
      return ValidationResult.failure(List.of(
          new ValidationError("expected " + type + ", got " + instance.getClass().getSimpleName())
      ));
    }
    
    private ValidationResult validateFloat(JsonValue instance, String type) {
      if (instance instanceof JsonNumber) {
        return ValidationResult.success();
      }
      return ValidationResult.failure(List.of(
          new ValidationError("expected " + type + ", got " + instance.getClass().getSimpleName())
      ));
    }
  }
  
  /// Enum schema - validates against a set of string values
  record EnumSchema(List<String> values) implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      if (instance instanceof JsonString str) {
        if (values.contains(str.value())) {
          return ValidationResult.success();
        }
        return ValidationResult.failure(List.of(
            new ValidationError("value '" + str.value() + "' not in enum: " + values)
        ));
      }
      return ValidationResult.failure(List.of(
          new ValidationError("expected string for enum, got " + instance.getClass().getSimpleName())
      ));
    }
  }
  
  /// Elements schema - validates array elements against a schema
  record ElementsSchema(JtdSchema elements) implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      if (instance instanceof JsonArray arr) {
        for (JsonValue element : arr.values()) {
          ValidationResult result = elements.validate(element);
          if (!result.isValid()) {
            return result;
          }
        }
        return ValidationResult.success();
      }
      return ValidationResult.failure(List.of(
          new ValidationError("expected array, got " + instance.getClass().getSimpleName())
      ));
    }
  }
  
  /// Properties schema - validates object properties
  record PropertiesSchema(
      java.util.Map<String, JtdSchema> properties,
      java.util.Map<String, JtdSchema> optionalProperties,
      boolean additionalProperties
  ) implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      if (!(instance instanceof JsonObject obj)) {
        return ValidationResult.failure(List.of(
            new ValidationError("expected object, got " + instance.getClass().getSimpleName())
        ));
      }
      
      // Validate required properties
      for (var entry : properties.entrySet()) {
        String key = entry.getKey();
        JtdSchema schema = entry.getValue();
        
        JsonValue value = obj.members().get(key);
        if (value == null) {
          return ValidationResult.failure(List.of(
              new ValidationError("missing required property: " + key)
          ));
        }
        
        ValidationResult result = schema.validate(value);
        if (!result.isValid()) {
          return result;
        }
      }
      
      // Validate optional properties if present
      for (var entry : optionalProperties.entrySet()) {
        String key = entry.getKey();
        JtdSchema schema = entry.getValue();
        
        JsonValue value = obj.members().get(key);
        if (value != null) {
          ValidationResult result = schema.validate(value);
          if (!result.isValid()) {
            return result;
          }
        }
      }
      
      // Check for additional properties if not allowed
      if (!additionalProperties) {
        for (String key : obj.members().keySet()) {
          if (!properties.containsKey(key) && !optionalProperties.containsKey(key)) {
            return ValidationResult.failure(List.of(
                new ValidationError("additional property not allowed: " + key)
            ));
          }
        }
      }
      
      return ValidationResult.success();
    }
  }
  
  /// Values schema - validates object values against a schema
  record ValuesSchema(JtdSchema values) implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      if (!(instance instanceof JsonObject obj)) {
        return ValidationResult.failure(List.of(
            new ValidationError("expected object, got " + instance.getClass().getSimpleName())
        ));
      }
      
      for (JsonValue value : obj.members().values()) {
        ValidationResult result = values.validate(value);
        if (!result.isValid()) {
          return result;
        }
      }
      
      return ValidationResult.success();
    }
  }
  
  /// Discriminator schema - validates tagged union objects
  record DiscriminatorSchema(
      String discriminator,
      java.util.Map<String, JtdSchema> mapping
  ) implements JtdSchema {
    @Override
    public ValidationResult validate(JsonValue instance) {
      if (!(instance instanceof JsonObject obj)) {
        return ValidationResult.failure(List.of(
            new ValidationError("expected object, got " + instance.getClass().getSimpleName())
        ));
      }
      
      JsonValue discriminatorValue = obj.members().get(discriminator);
      if (!(discriminatorValue instanceof JsonString discStr)) {
        return ValidationResult.failure(List.of(
            new ValidationError("discriminator '" + discriminator + "' must be a string")
        ));
      }
      
      String discriminatorValueStr = discStr.value();
      JtdSchema variantSchema = mapping.get(discriminatorValueStr);
      if (variantSchema == null) {
        return ValidationResult.failure(List.of(
            new ValidationError("discriminator value '" + discriminatorValueStr + "' not in mapping")
        ));
      }
      
      return variantSchema.validate(instance);
    }
  }
}
