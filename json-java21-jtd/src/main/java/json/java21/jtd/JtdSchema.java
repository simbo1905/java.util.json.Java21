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
      return switch (type) {
        case "boolean" -> validateBoolean(instance);
        case "string" -> validateString(instance);
        case "timestamp" -> validateTimestamp(instance);
        case "int8", "uint8", "int16", "uint16", "int32", "uint32" -> validateInteger(instance, type);
        case "float32", "float64" -> validateFloat(instance, type);
        default -> Jtd.Result.failure(List.of(
            "unknown type: " + type
        ));
      };
    }
    
    Jtd.Result validateBoolean(JsonValue instance) {
      if (instance instanceof JsonBoolean) {
        return Jtd.Result.success();
      }
      return Jtd.Result.failure(List.of(
          "expected boolean, got " + instance.getClass().getSimpleName()
      ));
    }
    
    Jtd.Result validateString(JsonValue instance) {
      if (instance instanceof JsonString) {
        return Jtd.Result.success();
      }
      return Jtd.Result.failure(List.of(
          "expected string, got " + instance.getClass().getSimpleName()
      ));
    }
    
    Jtd.Result validateTimestamp(JsonValue instance) {
      if (instance instanceof JsonString ignored) {
        throw new AssertionError("not implemented");
      }
      return Jtd.Result.failure(List.of(
          "expected timestamp (string), got " + instance.getClass().getSimpleName()
      ));
    }
    
    Jtd.Result validateInteger(JsonValue instance, String type) {
      if (instance instanceof JsonNumber num) {
        Number value = num.toNumber();
        if (value instanceof Double d && d != Math.floor(d)) {
          return Jtd.Result.failure(List.of(
              "expected integer, got float"
          ));
        }
        throw new AssertionError("not implemented");
      }
      return Jtd.Result.failure(List.of(
          "expected " + type + ", got " + instance.getClass().getSimpleName()
      ));
    }
    
    Jtd.Result validateFloat(JsonValue instance, String type) {
      if (instance instanceof JsonNumber) {
        return Jtd.Result.success();
      }
      return Jtd.Result.failure(List.of(
          "expected " + type + ", got " + instance.getClass().getSimpleName()
      ));
    }
  }
  
  /// Enum schema - validates against a set of string values
  record EnumSchema(List<String> values) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      if (instance instanceof JsonString str) {
        if (values.contains(str.value())) {
          return Jtd.Result.success();
        }
        return Jtd.Result.failure(List.of(
            "value '" + str.value() + "' not in enum: " + values
        ));
      }
      return Jtd.Result.failure(List.of(
          "expected string for enum, got " + instance.getClass().getSimpleName()
      ));
    }
  }
  
  /// Elements schema - validates array elements against a schema
  record ElementsSchema(JtdSchema elements) implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      if (instance instanceof JsonArray arr) {
        for (JsonValue element : arr.values()) {
          Jtd.Result result = elements.validate(element);
          if (!result.isValid()) {
            return result;
          }
        }
        return Jtd.Result.success();
      }
      return Jtd.Result.failure(List.of(
          "expected array, got " + instance.getClass().getSimpleName()
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
    public Jtd.Result validate(JsonValue instance) {
      if (!(instance instanceof JsonObject obj)) {
        return Jtd.Result.failure(List.of(
            "expected object, got " + instance.getClass().getSimpleName()
        ));
      }
      
      // Validate required properties
      for (var entry : properties.entrySet()) {
        String key = entry.getKey();
        JtdSchema schema = entry.getValue();
        
        JsonValue value = obj.members().get(key);
        if (value == null) {
          return Jtd.Result.failure(List.of(
              "missing required property: " + key
          ));
        }
        
        Jtd.Result result = schema.validate(value);
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
          Jtd.Result result = schema.validate(value);
          if (!result.isValid()) {
            return result;
          }
        }
      }
      
      // Check for additional properties if not allowed
      if (!additionalProperties) {
        for (String key : obj.members().keySet()) {
          if (!properties.containsKey(key) && !optionalProperties.containsKey(key)) {
            return Jtd.Result.failure(List.of(
                "additional property not allowed: " + key
            ));
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
      if (!(instance instanceof JsonObject obj)) {
        return Jtd.Result.failure(List.of(
            "expected object, got " + instance.getClass().getSimpleName()
        ));
      }
      
      for (JsonValue value : obj.members().values()) {
        Jtd.Result result = values.validate(value);
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
      if (!(instance instanceof JsonObject obj)) {
        return Jtd.Result.failure(List.of(
            "expected object, got " + instance.getClass().getSimpleName()
        ));
      }
      
      JsonValue discriminatorValue = obj.members().get(discriminator);
      if (!(discriminatorValue instanceof JsonString discStr)) {
        return Jtd.Result.failure(List.of(
            "discriminator '" + discriminator + "' must be a string"
        ));
      }
      
      String discriminatorValueStr = discStr.value();
      JtdSchema variantSchema = mapping.get(discriminatorValueStr);
      if (variantSchema == null) {
        return Jtd.Result.failure(List.of(
            "discriminator value '" + discriminatorValueStr + "' not in mapping"
        ));
      }
      
      return variantSchema.validate(instance);
    }
  }
}
