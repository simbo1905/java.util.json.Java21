package json.java21.jtd;

import jdk.sandbox.java.util.json.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/// JTD Validator - validates JSON instances against JTD schemas (RFC 8927)
/// Implements the eight mutually-exclusive schema forms defined in RFC 8927
public class Jtd {
  
  private static final Logger LOG = Logger.getLogger(Jtd.class.getName());
  
  /// Validates a JSON instance against a JTD schema
  /// @param schema The JTD schema as a JsonValue
  /// @param instance The JSON instance to validate
  /// @return Result containing validation status and any errors
  public Result validate(JsonValue schema, JsonValue instance) {
    LOG.fine(() -> "JTD validation - schema: " + schema + ", instance: " + instance);
    
    try {
      JtdSchema jtdSchema = compileSchema(schema);
      Result result = jtdSchema.validate(instance);
      
      LOG.fine(() -> "JTD validation result: " + (result.isValid() ? "VALID" : "INVALID") + 
                     ", errors: " + result.errors().size());
      
      return result;
    } catch (Exception e) {
      LOG.warning(() -> "JTD validation failed: " + e.getMessage());
      return Result.failure("Schema parsing failed: " + e.getMessage());
    }
  }
  
  /// Compiles a JsonValue into a JtdSchema based on RFC 8927 rules
  JtdSchema compileSchema(JsonValue schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }
    
    if (schema instanceof JsonObject obj) {
      return compileObjectSchema(obj);
    }
    
    throw new IllegalArgumentException("Schema must be an object, got: " + schema.getClass().getSimpleName());
  }
  
  /// Compiles an object schema according to RFC 8927
  JtdSchema compileObjectSchema(JsonObject obj) {
    // Check for mutually-exclusive schema forms
    List<String> forms = new ArrayList<>();
    Map<String, JsonValue> members = obj.members();
    
    if (members.containsKey("ref")) forms.add("ref");
    if (members.containsKey("type")) forms.add("type");
    if (members.containsKey("enum")) forms.add("enum");
    if (members.containsKey("elements")) forms.add("elements");
    if (members.containsKey("values")) forms.add("values");
    if (members.containsKey("discriminator")) forms.add("discriminator");
    
    // Properties and optionalProperties are special - they can coexist
    boolean hasProperties = members.containsKey("properties");
    boolean hasOptionalProperties = members.containsKey("optionalProperties");
    if (hasProperties || hasOptionalProperties) {
      forms.add("properties"); // Treat as single form
    }
    
    // RFC 8927: schemas must have exactly one of these forms
    if (forms.size() > 1) {
      throw new IllegalArgumentException("Schema has multiple forms: " + forms);
    }
    
    // Handle nullable flag (can be combined with any form)
    boolean nullable = false;
    if (members.containsKey("nullable")) {
      JsonValue nullableValue = members.get("nullable");
      if (!(nullableValue instanceof JsonBoolean bool)) {
        throw new IllegalArgumentException("nullable must be a boolean");
      }
      nullable = bool.value();
    }
    
    // Parse the specific schema form
    JtdSchema schema;
    
    if (forms.isEmpty()) {
      // Empty schema - accepts any value
      schema = new JtdSchema.EmptySchema();
    } else {
      String form = forms.getFirst();
      schema = switch (form) {
        case "ref" -> compileRefSchema(obj);
        case "type" -> compileTypeSchema(obj);
        case "enum" -> compileEnumSchema(obj);
        case "elements" -> compileElementsSchema(obj);
        case "properties" -> compilePropertiesSchema(obj);
        case "optionalProperties" -> compilePropertiesSchema(obj); // handled together
        case "values" -> compileValuesSchema(obj);
        case "discriminator" -> compileDiscriminatorSchema(obj);
        default -> throw new IllegalArgumentException("Unknown schema form: " + form);
      };
    }
    
    // Wrap with nullable if needed
    if (nullable) {
      return new JtdSchema.NullableSchema(schema);
    }
    
    return schema;
  }
  
  JtdSchema compileRefSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue refValue = members.get("ref");
    if (!(refValue instanceof JsonString str)) {
      throw new IllegalArgumentException("ref must be a string");
    }
    return new JtdSchema.RefSchema(str.value());
  }
  
  JtdSchema compileTypeSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue typeValue = members.get("type");
    if (!(typeValue instanceof JsonString str)) {
      throw new IllegalArgumentException("type must be a string");
    }
    return new JtdSchema.TypeSchema(str.value());
  }
  
  JtdSchema compileEnumSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue enumValue = members.get("enum");
    if (!(enumValue instanceof JsonArray arr)) {
      throw new IllegalArgumentException("enum must be an array");
    }
    
    List<String> values = new ArrayList<>();
    for (JsonValue value : arr.values()) {
      if (!(value instanceof JsonString str)) {
        throw new IllegalArgumentException("enum values must be strings");
      }
      values.add(str.value());
    }
    
    if (values.isEmpty()) {
      throw new IllegalArgumentException("enum cannot be empty");
    }
    
    return new JtdSchema.EnumSchema(values);
  }
  
  JtdSchema compileElementsSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue elementsValue = members.get("elements");
    JtdSchema elementsSchema = compileSchema(elementsValue);
    return new JtdSchema.ElementsSchema(elementsSchema);
  }
  
  JtdSchema compilePropertiesSchema(JsonObject obj) {
    Map<String, JtdSchema> properties = Map.of();
    Map<String, JtdSchema> optionalProperties = Map.of();
    boolean additionalProperties = true;
    
    Map<String, JsonValue> members = obj.members();
    
    // Parse required properties
    if (members.containsKey("properties")) {
      JsonValue propsValue = members.get("properties");
      if (!(propsValue instanceof JsonObject propsObj)) {
        throw new IllegalArgumentException("properties must be an object");
      }
      properties = parsePropertySchemas(propsObj);
    }
    
    // Parse optional properties
    if (members.containsKey("optionalProperties")) {
      JsonValue optPropsValue = members.get("optionalProperties");
      if (!(optPropsValue instanceof JsonObject optPropsObj)) {
        throw new IllegalArgumentException("optionalProperties must be an object");
      }
      optionalProperties = parsePropertySchemas(optPropsObj);
    }
    
    // Check additionalProperties
    if (members.containsKey("additionalProperties")) {
      JsonValue addPropsValue = members.get("additionalProperties");
      if (!(addPropsValue instanceof JsonBoolean bool)) {
        throw new IllegalArgumentException("additionalProperties must be a boolean");
      }
      additionalProperties = bool.value();
    }
    
    return new JtdSchema.PropertiesSchema(properties, optionalProperties, additionalProperties);
  }
  
  JtdSchema compileValuesSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue valuesValue = members.get("values");
    JtdSchema valuesSchema = compileSchema(valuesValue);
    return new JtdSchema.ValuesSchema(valuesSchema);
  }
  
  JtdSchema compileDiscriminatorSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue discriminatorValue = members.get("discriminator");
    if (!(discriminatorValue instanceof JsonString discStr)) {
      throw new IllegalArgumentException("discriminator must be a string");
    }
    
    JsonValue mappingValue = members.get("mapping");
    if (!(mappingValue instanceof JsonObject mappingObj)) {
      throw new IllegalArgumentException("mapping must be an object");
    }
    
    Map<String, JtdSchema> mapping = new java.util.HashMap<>();
    for (String key : mappingObj.members().keySet()) {
      JsonValue variantValue = mappingObj.members().get(key);
      JtdSchema variantSchema = compileSchema(variantValue);
      mapping.put(key, variantSchema);
    }
    
    return new JtdSchema.DiscriminatorSchema(discStr.value(), mapping);
  }
  
  private Map<String, JtdSchema> parsePropertySchemas(JsonObject propsObj) {
    Map<String, JtdSchema> schemas = new java.util.HashMap<>();
    for (String key : propsObj.members().keySet()) {
      JsonValue schemaValue = propsObj.members().get(key);
      schemas.put(key, compileSchema(schemaValue));
    }
    return schemas;
  }

  /// Result of JTD schema validation
  /// Immutable result containing validation status and any error messages
  public record Result(boolean isValid, List<String> errors) {
    
    /// Singleton success result - no errors
    private static final Result SUCCESS = new Result(true, Collections.emptyList());
    
    /// Creates a successful validation result
    public static Result success() {
      return SUCCESS;
    }
    
    /// Creates a failed validation result with the given error messages
    public static Result failure(List<String> errors) {
      return new Result(false, Collections.unmodifiableList(errors));
    }
    
    /// Creates a failed validation result with a single error message
    public static Result failure(String error) {
      return failure(List.of(error));
    }
  }
}
