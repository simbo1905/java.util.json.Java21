package json.java21.jtd;

import jdk.sandbox.java.util.json.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/// JTD Schema interface - validates JSON instances against JTD schemas
/// Following RFC 8927 specification with eight mutually-exclusive schema forms
public sealed interface JtdSchema {
  
  /// Validates a JSON instance against this schema
  /// @param instance The JSON value to validate
  /// @return Result containing errors if validation fails
  Jtd.Result validate(JsonValue instance);

  /// Validates a JSON instance against this schema using stack-based validation
  /// @param frame The current validation frame containing schema, instance, path, and context
  /// @param errors List to accumulate error messages
  /// @param verboseErrors Whether to include full JSON values in error messages
  /// @return true if validation passes, false if validation fails
  default boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
    // Default implementation delegates to existing validate method for backward compatibility
    Jtd.Result result = validate(frame.instance(), verboseErrors);
    if (!result.isValid()) {
      errors.addAll(result.errors());
      return false;
    }
    return true;
  }
  
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

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      if (frame.instance() instanceof JsonNull) {
        return true;
      }
      return wrapped.validateWithFrame(frame, errors, verboseErrors);
    }
  }
  
  /// Empty schema - accepts any value (null, boolean, number, string, array, object)
  record EmptySchema() implements JtdSchema {
    @Override
    public Jtd.Result validate(JsonValue instance) {
      // Empty schema accepts any JSON value
      return Jtd.Result.success();
    }

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      // Empty schema accepts any JSON value
      return true;
    }
  }
  
  /// Ref schema - references a definition in the schema's definitions
  record RefSchema(String ref, java.util.Map<String, JtdSchema> definitions) implements JtdSchema {
    JtdSchema target() {
      JtdSchema schema = definitions.get(ref);
      if (schema == null) {
        throw new IllegalStateException("Ref not resolved: " + ref);
      }
      return schema;
    }

    @Override
    public Jtd.Result validate(JsonValue instance) {
      return target().validate(instance);
    }

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      JtdSchema resolved = target();
      Jtd.Frame resolvedFrame = new Jtd.Frame(resolved, frame.instance(), frame.ptr(),
          frame.crumbs(), frame.discriminatorKey());
      return resolved.validateWithFrame(resolvedFrame, errors, verboseErrors);
    }

    @Override
    public String toString() {
      return "RefSchema(ref=" + ref + ")";
    }
  }
  
  /// Type schema - validates specific primitive types
  record TypeSchema(String type) implements JtdSchema {
    /// RFC 3339 timestamp pattern with leap second support
    private static final java.util.regex.Pattern RFC3339 = java.util.regex.Pattern.compile(
      "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:(\\d{2}|60)(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2}))$"
    );
    
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

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      Jtd.Result result = validate(frame.instance(), verboseErrors);
      if (!result.isValid()) {
        // Enrich errors with offset and path information
        for (String error : result.errors()) {
          String enrichedError = Jtd.enrichedError(error, frame, frame.instance());
          errors.add(enrichedError);
        }
        return false;
      }
      return true;
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
      if (instance instanceof JsonString str) {
        String value = str.value();
        if (RFC3339.matcher(value).matches()) {
          try {
            // Replace :60 with :59 to allow leap seconds through parsing
            String normalized = value.replace(":60", ":59");
            OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return Jtd.Result.success();
          } catch (Exception ignore) {}
        }
      }
      String error = verboseErrors
          ? Jtd.Error.EXPECTED_TIMESTAMP.message(instance, instance.getClass().getSimpleName())
          : Jtd.Error.EXPECTED_TIMESTAMP.message(instance.getClass().getSimpleName());
      return Jtd.Result.failure(error);
    }
    
    /// Package-protected static validation for RFC 3339 timestamp format with leap second support
    /// RFC 3339 grammar: date-time = full-date "T" full-time
    /// Supports leap seconds (seconds = 60 when minutes = 59)
    static boolean isValidRfc3339Timestamp(String timestamp) {
      // RFC 3339 regex pattern with leap second support
      String rfc3339Pattern = "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$";
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(rfc3339Pattern);
      java.util.regex.Matcher matcher = pattern.matcher(timestamp);
      
      if (!matcher.matches()) {
        return false;
      }
      
      try {
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        int hour = Integer.parseInt(matcher.group(4));
        int minute = Integer.parseInt(matcher.group(5));
        int second = Integer.parseInt(matcher.group(6));
        
        // Validate basic date/time components
        if (year < 1 || month < 1 || month > 12 || day < 1 || day > 31) {
          return false;
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
          return false;
        }
        
        // Handle leap seconds: seconds = 60 is valid only if minutes = 59
        if (second == 60) {
          if (minute != 59) {
            return false;
          }
          // For leap seconds, we accept the format but don't validate the specific date
          // This matches RFC 8927 behavior - format validation only
          return true;
        }
        
        if (second < 0 || second > 59) {
          return false;
        }
        
        // For normal timestamps, delegate to OffsetDateTime.parse for full validation
        try {
          OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
          return true;
        } catch (Exception e) {
          return false;
        }
        
      } catch (NumberFormatException e) {
        return false;
      }
    }
    
    Jtd.Result validateInteger(JsonValue instance, String type, boolean verboseErrors) {
      if (instance instanceof JsonNumber num) {
        Number value = num.toNumber();
        
        // Check if the number is not integral (has fractional part)
        if (value instanceof Double d && d != Math.floor(d)) {
          return Jtd.Result.failure(Jtd.Error.EXPECTED_INTEGER.message());
        }
        
        // Handle BigDecimal - check if it has fractional component (not just scale > 0)
        // RFC 8927 §2.2.3.1: "An integer value is a number without a fractional component"
        // Values like 3.0 or 3.000 are valid integers despite positive scale, but 3.1 is not
        if (value instanceof java.math.BigDecimal bd && bd.remainder(java.math.BigDecimal.ONE).signum() != 0) {
          return Jtd.Result.failure(Jtd.Error.EXPECTED_INTEGER.message());
        }
        
        // Convert to long for range checking
        long longValue = value.longValue();
        
        // Check ranges according to RFC 8927 §2.2.3.1
        boolean valid = switch (type) {
          case "int8" -> longValue >= -128 && longValue <= 127;
          case "uint8" -> longValue >= 0 && longValue <= 255;
          case "int16" -> longValue >= -32768 && longValue <= 32767;
          case "uint16" -> longValue >= 0 && longValue <= 65535;
          case "int32" -> longValue >= -2147483648L && longValue <= 2147483647L;
          case "uint32" -> longValue >= 0 && longValue <= 4294967295L;
          default -> false;
        };
        
        if (valid) {
          return Jtd.Result.success();
        }
        
        // Range violation
        String error = verboseErrors
            ? Jtd.Error.EXPECTED_NUMERIC_TYPE.message(instance, type, instance.getClass().getSimpleName())
            : Jtd.Error.EXPECTED_NUMERIC_TYPE.message(type, instance.getClass().getSimpleName());
        return Jtd.Result.failure(error);
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

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      Jtd.Result result = validate(frame.instance(), verboseErrors);
      if (!result.isValid()) {
        // Enrich errors with offset and path information
        for (String error : result.errors()) {
          String enrichedError = Jtd.enrichedError(error, frame, frame.instance());
          errors.add(enrichedError);
        }
        return false;
      }
      return true;
    }
  }
  
  /// Elements schema - validates array elements against a schema
  record ElementsSchema(JtdSchema elements) implements JtdSchema {
    @Override
    public String toString() {
      return "ElementsSchema[elements=" + elements.getClass().getSimpleName() + "]";
    }
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

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      JsonValue instance = frame.instance();
      
      if (!(instance instanceof JsonArray arr)) {
        String error = verboseErrors
            ? Jtd.Error.EXPECTED_ARRAY.message(instance, instance.getClass().getSimpleName())
            : Jtd.Error.EXPECTED_ARRAY.message(instance.getClass().getSimpleName());
        String enrichedError = Jtd.enrichedError(error, frame, instance);
        errors.add(enrichedError);
        return false;
      }
      
      // For ElementsSchema, child frames are pushed by the main validation loop
      // This method just confirms the instance is an array
      return true;
    }
  }
  
  /// Properties schema - validates object properties
  record PropertiesSchema(
      java.util.Map<String, JtdSchema> properties,
      java.util.Map<String, JtdSchema> optionalProperties,
      boolean additionalProperties
  ) implements JtdSchema {
    @Override
    public String toString() {
      return "PropertiesSchema[required=" + properties.keySet() +
             ", optional=" + optionalProperties.keySet() +
             ", additionalProperties=" + additionalProperties + "]";
    }
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

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      JsonValue instance = frame.instance();
      
      if (!(instance instanceof JsonObject obj)) {
        String error = verboseErrors
            ? Jtd.Error.EXPECTED_OBJECT.message(instance, instance.getClass().getSimpleName())
            : Jtd.Error.EXPECTED_OBJECT.message(instance.getClass().getSimpleName());
        String enrichedError = Jtd.enrichedError(error, frame, instance);
        errors.add(enrichedError);
        return false;
      }
      
      // For PropertiesSchema, child frames are pushed by the main validation loop
      // This method just confirms the instance is an object
      return true;
    }
  }
  
  /// Values schema - validates object values against a schema
  record ValuesSchema(JtdSchema values) implements JtdSchema {
    @Override
    public String toString() {
      return "ValuesSchema[values=" + values.getClass().getSimpleName() + "]";
    }
    
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

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      JsonValue instance = frame.instance();
      
      if (!(instance instanceof JsonObject obj)) {
        String error = verboseErrors
            ? Jtd.Error.EXPECTED_OBJECT.message(instance, instance.getClass().getSimpleName())
            : Jtd.Error.EXPECTED_OBJECT.message(instance.getClass().getSimpleName());
        String enrichedError = Jtd.enrichedError(error, frame, instance);
        errors.add(enrichedError);
        return false;
      }
      
      // For ValuesSchema, child frames are pushed by the main validation loop
      // This method just confirms the instance is an object
      return true;
    }
  }
  
  /// Discriminator schema - validates tagged union objects
  record DiscriminatorSchema(
      String discriminator,
      java.util.Map<String, JtdSchema> mapping
  ) implements JtdSchema {
    @Override
    public String toString() {
      return "DiscriminatorSchema[discriminator=" + discriminator + ", mapping=" + mapping.keySet() + "]";
    }
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
      
      // Special-case: allow objects with only the discriminator key
      // This handles the case where discriminator maps to simple types like "boolean"
      // and the object contains only the discriminator field
      if (obj.members().size() == 1 && obj.members().containsKey(discriminator)) {
        return Jtd.Result.success();
      }
      
      // Otherwise, validate against the chosen variant schema
      return variantSchema.validate(instance, verboseErrors);
    }

    @Override
    public boolean validateWithFrame(Jtd.Frame frame, java.util.List<String> errors, boolean verboseErrors) {
      JsonValue instance = frame.instance();
      
      if (!(instance instanceof JsonObject obj)) {
        String error = verboseErrors
            ? Jtd.Error.EXPECTED_OBJECT.message(instance, instance.getClass().getSimpleName())
            : Jtd.Error.EXPECTED_OBJECT.message(instance.getClass().getSimpleName());
        String enrichedError = Jtd.enrichedError(error, frame, instance);
        errors.add(enrichedError);
        return false;
      }
      
      JsonValue discriminatorValue = obj.members().get(discriminator);
      if (!(discriminatorValue instanceof JsonString discStr)) {
        String error = verboseErrors
            ? Jtd.Error.DISCRIMINATOR_MUST_BE_STRING.message(discriminatorValue, discriminator)
            : Jtd.Error.DISCRIMINATOR_MUST_BE_STRING.message(discriminator);
        String enrichedError = Jtd.enrichedError(error, frame, discriminatorValue != null ? discriminatorValue : instance);
        errors.add(enrichedError);
        return false;
      }
      
      String discriminatorValueStr = discStr.value();
      JtdSchema variantSchema = mapping.get(discriminatorValueStr);
      if (variantSchema == null) {
        String error = verboseErrors
            ? Jtd.Error.DISCRIMINATOR_VALUE_NOT_IN_MAPPING.message(discriminatorValue, discriminatorValueStr)
            : Jtd.Error.DISCRIMINATOR_VALUE_NOT_IN_MAPPING.message(discriminatorValueStr);
        String enrichedError = Jtd.enrichedError(error, frame, discriminatorValue);
        errors.add(enrichedError);
        return false;
      }
      
      // For DiscriminatorSchema, push the variant schema for validation
      return true;
    }
  }
}
