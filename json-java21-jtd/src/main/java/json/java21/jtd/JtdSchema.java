package json.java21.jtd;

import jdk.sandbox.java.util.json.*;

import java.util.List;

/// JTD Schema interface - validates JSON instances against JTD schemas
/// Following RFC 8927 specification with eight mutually-exclusive schema forms
public sealed interface JtdSchema {
  
  /// Validates a JSON instance against this schema
  /// @param instance The JSON value to validate
  /// @return Result containing errors if validation fails
  Jtd.Result validate(JsonValue instance);
  
  /// Validates a JSON instance against this schema with optional verbose errors
  /// @param instance The JSON value to validate
  /// @param verboseErrors Whether to include full JSON values in error messages
  /// @return Result containing errors if validation fails
  default Jtd.Result validate(JsonValue instance, boolean verboseErrors) {
    // Default implementation delegates to existing validate method
    // Individual schema implementations can override for verbose error support
    return validate(instance);
  }
  
  /// Nullable schema wrapper - allows null values
  record NullableSchema(JtdSchema wrapped) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      if (instance instanceof JsonNull) {
        return Jtd.Result.success();
      }
      return wrapped.validate(instance);
    }
  }
  
  /// Empty schema - accepts any value (null, boolean, number, string, array, object)
  record EmptySchema() implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      // Empty schema accepts any JSON value
      return Jtd.Result.success();
    }
  }
  
  /// Ref schema - references a definition in the schema's definitions
  record RefSchema(String ref) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      throw new AssertionError("not implemented");
    }
  }
  
  /// Type schema - validates specific primitive types
  record TypeSchema(String type) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      return validate(instance, false);
    }
    
    @Override
    public Jtd.Result validate(JsonValue instance, boolean verboseErrors) {
      return switch (type) {
        case "boolean" -> validateBoolean(instance, verboseErrors);
        case "string" -> validateString(instance, verboseErrors);
        case "timestamp" -> validateTimestamp(instance, verboseErrors);
        case "int8", "uint8", "int16", "uint16", "int32", "uint32" -> validateInteger(instance, type, verboseErrors);
        case "float32", "float64" -> validateFloat(instance, type, verboseErrors);
        default -> Jtd.Result.failure(Jtd.Error.UNKNOWN_TYPE.message(type));
      };
    }
    
    Jtd.Result validateBoolean(JsonValue instance, boolean verboseErrors) {
      if (instance instanceof JsonBoolean) {
        return Jtd.Result.success();
      }
      String error = verboseErrors 
          ? Jtd.Error.EXPECTED_BOOLEAN.message(instance, instance.getClass().getSimpleName())
          : Jtd.Error.EXPECTED_BOOLEAN.message(instance.getClass().getSimpleName());
      return Jtd.Result.failure(error);
    }
    
    Jtd.Result validateString(JsonValue instance, boolean verboseErrors) {
      if (instance instanceof JsonString) {
        return Jtd.Result.success();
      }
      String error = verboseErrors
          ? Jtd.Error.EXPECTED_STRING.message(instance, instance.getClass().getSimpleName())
          : Jtd.Error.EXPECTED_STRING.message(instance.getClass().getSimpleName());
      return Jtd.Result.failure(error);
    }
    
    Jtd.Result validateTimestamp(JsonValue instance, boolean verboseErrors) {
      if (instance instanceof JsonString ignored) {
        throw new AssertionError("not implemented");
      }
      String error = verboseErrors
          ? Jtd.Error.EXPECTED_TIMESTAMP.message(instance, instance.getClass().getSimpleName())
          : Jtd.Error.EXPECTED_TIMESTAMP.message(instance.getClass().getSimpleName());
      return Jtd.Result.failure(error);
    }
    
    Jtd.Result validateInteger(JsonValue instance, String type, boolean verboseErrors) {
      if (instance instanceof JsonNumber num) {
        Number value = num.toNumber();
        if (value instanceof Double d && d != Math.floor(d)) {
          return Jtd.Result.failure(Jtd.Error.EXPECTED_INTEGER.message());
        }
        throw new AssertionError("not implemented");
      }
      String error = verboseErrors
          ? Jtd.Error.EXPECTED_NUMERIC_TYPE.message(instance, type, instance.getClass().getSimpleName())
          : Jtd.Error.EXPECTED_NUMERIC_TYPE.message(type, instance.getClass().getSimpleName());
      return Jtd.Result.failure(error);
    }
    
    Jtd.Result validateFloat(JsonValue instance, String type, boolean verboseErrors) {
      if (instance instanceof JsonNumber) {
        return Jtd.Result.success();
      }
      String error = verboseErrors
          ? Jtd.Error.EXPECTED_NUMERIC_TYPE.message(instance, type, instance.getClass().getSimpleName())
          : Jtd.Error.EXPECTED_NUMERIC_TYPE.message(type, instance.getClass().getSimpleName());
      return Jtd.Result.failure(error);
    }
  }
  
  /// Enum schema - validates against a set of string values
  record EnumSchema(List<String> values) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      return validate(instance, false);
    }
    
    @Override
    public Jtd.Result validate(JsonValue instance, boolean verboseErrors) {
      if (instance instanceof JsonString str) {
        if (values.contains(str.value())) {
          return Jtd.Result.success();
        }
        String error = verboseErrors
            ? Jtd.Error.VALUE_NOT_IN_ENUM.message(instance, str.value(), values)
            : Jtd.Error.VALUE_NOT_IN_ENUM.message(str.value(), values);
        return Jtd.Result.failure(error);
      }
      String error = verboseErrors
          ? Jtd.Error.EXPECTED_STRING_FOR_ENUM.message(instance, instance.getClass().getSimpleName())
          : Jtd.Error.EXPECTED_STRING_FOR_ENUM.message(instance.getClass().getSimpleName());
      return Jtd.Result.failure(error);
    }
  }
  
  /// Elements schema - validates array elements against a schema
  record ElementsSchema(JtdSchema elements) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      return validate(instance, false);
    }
    
    @Override
    public Jtd.Result validate(JsonValue instance, boolean verboseErrors) {
      if (instance instanceof JsonArray arr) {
        for (JsonValue element : arr.values()) {
          Jtd.Result result = elements.validate(element, verboseErrors);
          if (!result.isValid()) {
            return result;
          }
        }
        return Jtd.Result.success();
      }
      String error = verboseErrors
          ? Jtd.Error.EXPECTED_ARRAY.message(instance, instance.getClass().getSimpleName())
          : Jtd.Error.EXPECTED_ARRAY.message(instance.getClass().getSimpleName());
      return Jtd.Result.failure(error);
    }
  }
  
  /// Properties schema - validates object properties
  record PropertiesSchema(
      java.util.Map<String, JtdSchema> properties,
      java.util.Map<String, JtdSchema> optionalProperties,
      boolean additionalProperties
  ) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      return validate(instance, false);
    }
    
    @Override
    public Jtd.Result validate(JsonValue instance, boolean verboseErrors) {
      if (!(instance instanceof JsonObject obj)) {
        String error = verboseErrors
            ? Jtd.Error.EXPECTED_OBJECT.message(instance, instance.getClass().getSimpleName())
            : Jtd.Error.EXPECTED_OBJECT.message(instance.getClass().getSimpleName());
        return Jtd.Result.failure(error);
      }
      
      // Validate required properties
      for (var entry : properties.entrySet()) {
        String key = entry.getKey();
        JtdSchema schema = entry.getValue();
        
        JsonValue value = obj.members().get(key);
        if (value == null) {
          return Jtd.Result.failure(Jtd.Error.MISSING_REQUIRED_PROPERTY.message(key));
        }
        
        Jtd.Result result = schema.validate(value, verboseErrors);
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
          Jtd.Result result = schema.validate(value, verboseErrors);
          if (!result.isValid()) {
            return result;
          }
        }
      }
      
      // Check for additional properties if not allowed
      if (!additionalProperties) {
        for (String key : obj.members().keySet()) {
          if (!properties.containsKey(key) && !optionalProperties.containsKey(key)) {
            return Jtd.Result.failure(Jtd.Error.ADDITIONAL_PROPERTY_NOT_ALLOWED.message(key));
          }
        }
      }
      
      return Jtd.Result.success();
    }
  }
  
  /// Values schema - validates object values against a schema
  record ValuesSchema(JtdSchema values) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      return validate(instance, false);
    }
    
    @Override
    public Jtd.Result validate(JsonValue instance, boolean verboseErrors) {
      if (!(instance instanceof JsonObject obj)) {
        String error = verboseErrors
            ? Jtd.Error.EXPECTED_OBJECT.message(instance, instance.getClass().getSimpleName())
            : Jtd.Error.EXPECTED_OBJECT.message(instance.getClass().getSimpleName());
        return Jtd.Result.failure(error);
      }
      
      for (JsonValue value : obj.members().values()) {
        Jtd.Result result = values.validate(value, verboseErrors);
        if (!result.isValid()) {
          return result;
        }
      }
      
      return Jtd.Result.success();
    }
  }
  
  /// Discriminator schema - validates tagged union objects
  record DiscriminatorSchema(
      String discriminator,
      java.util.Map<String, JtdSchema> mapping
  ) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      return validate(instance, false);
    }
    
    @Override
    public Jtd.Result validate(JsonValue instance, boolean verboseErrors) {
      if (!(instance instanceof JsonObject obj)) {
        String error = verboseErrors
            ? Jtd.Error.EXPECTED_OBJECT.message(instance, instance.getClass().getSimpleName())
            : Jtd.Error.EXPECTED_OBJECT.message(instance.getClass().getSimpleName());
        return Jtd.Result.failure(error);
      }
      
      JsonValue discriminatorValue = obj.members().get(discriminator);
      if (!(discriminatorValue instanceof JsonString discStr)) {
        String error = verboseErrors
            ? Jtd.Error.DISCRIMINATOR_MUST_BE_STRING.message(discriminatorValue, discriminator)
            : Jtd.Error.DISCRIMINATOR_MUST_BE_STRING.message(discriminator);
        return Jtd.Result.failure(error);
      }
      
      String discriminatorValueStr = discStr.value();
      JtdSchema variantSchema = mapping.get(discriminatorValueStr);
      if (variantSchema == null) {
        String error = verboseErrors
            ? Jtd.Error.DISCRIMINATOR_VALUE_NOT_IN_MAPPING.message(discriminatorValue, discriminatorValueStr)
            : Jtd.Error.DISCRIMINATOR_VALUE_NOT_IN_MAPPING.message(discriminatorValueStr);
        return Jtd.Result.failure(error);
      }
      
      return variantSchema.validate(instance, verboseErrors);
    }
  }
}
