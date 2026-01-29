package json.java21.jtd;

import jdk.sandbox.java.util.json.*;
import jdk.sandbox.internal.util.json.*;

import java.util.ArrayList;
import java.util.Collections;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/// JTD Validator - validates JSON instances against JTD schemas (RFC 8927)
/// Implements the eight mutually-exclusive schema forms defined in RFC 8927
public class Jtd {
  
  private static final Logger LOG = Logger.getLogger(Jtd.class.getName());
  
  /// RFC 8927 §2.2.3: Valid primitive types for type schema validation
  private static final Set<String> VALID_TYPES = Set.of(
    "boolean", "string", "timestamp",
    "int8", "uint8", "int16", "uint16", "int32", "uint32",
    "float32", "float64"
  );
  
  /// Top-level definitions map for ref resolution
  private final Map<String, JtdSchema> definitions = new java.util.HashMap<>();

  /// Extracts offset from JsonValue implementation classes
  static int offsetOf(JsonValue v) {
    return switch (v) {
      case JsonObjectImpl j -> j.offset();
      case JsonArrayImpl j -> j.offset();
      case JsonStringImpl j -> j.offset();
      case JsonNumberImpl j -> j.offset();
      case JsonBooleanImpl j -> j.offset();
      case JsonNullImpl j -> j.offset();
      default -> -1; // unknown/foreign implementation
    };
  }
  
  /// Creates an enriched error message with offset and path information
  static String enrichedError(String baseMessage, Frame frame, JsonValue contextValue) {
    int off = offsetOf(contextValue);
    String ptr = frame.ptr();
    String via = frame.crumbs().value();
    return "[off=" + off + " ptr=" + ptr + " via=" + via + "] " + baseMessage;
  }
  
  /// Compiles a JTD schema and throws exceptions for invalid schemas
  ///
  /// @param schema The JTD schema as a JsonValue
  /// @throws IllegalArgumentException if the schema is invalid
  public void compile(JsonValue schema) {
    definitions.clear();
    compileSchema(schema);
  }

  /// Validates a JSON instance against a JTD schema
  /// @param schema The JTD schema as a JsonValue
  /// @param instance The JSON instance to validate
  /// @return Result containing validation status and any errors
  public Result validate(JsonValue schema, JsonValue instance) {
    LOG.fine(() -> "JTD validation - schema: " + schema + ", instance: " + instance);
    
    try {
      // Clear previous definitions
      definitions.clear();
      
      JtdSchema jtdSchema = compileSchema(schema);
      Result result = validateWithStack(jtdSchema, instance);
      
      LOG.fine(() -> "JTD validation result: " + (result.isValid() ? "VALID" : "INVALID") + 
                     ", errors: " + result.errors().size());
      
      return result;
    } catch (Exception e) {
      LOG.warning(() -> "JTD validation failed: " + e.getMessage());
      String error = enrichedError("Schema parsing failed: " + e.getMessage(), 
                                   new Frame(null, schema, "#", Crumbs.root()), schema);
      return Result.failure(error);
    }
  }
  
  /// Validates using iterative stack-based approach with offset and path tracking
  Result validateWithStack(JtdSchema schema, JsonValue instance) {
    List<String> errors = new ArrayList<>();
    java.util.Deque<Frame> stack = new java.util.ArrayDeque<>();
    
    // Push initial frame
    Frame rootFrame = new Frame(schema, instance, "#", Crumbs.root());
    stack.push(rootFrame);
    
    LOG.fine(() -> "Starting stack validation - schema=" +
        rootFrame.schema().getClass().getSimpleName() +
        (rootFrame.schema() instanceof JtdSchema.RefSchema r ? "(ref=" + r.ref() + ")" : "") +
        ", ptr=#");
    
    // Process frames iteratively
    while (!stack.isEmpty()) {
      Frame frame = stack.pop();
      LOG.fine(() -> "Processing frame - schema: " + frame.schema().getClass().getSimpleName() +
                     (frame.schema() instanceof JtdSchema.RefSchema r ? "(ref=" + r.ref() + ")" : "") +
                     ", ptr: " + frame.ptr() + ", off: " + offsetOf(frame.instance()));
      
      // Validate current frame
      if (!frame.schema().validateWithFrame(frame, errors, false)) {
        LOG.fine(() -> "Validation failed for frame at " + frame.ptr() + " with " + errors.size() + " errors");
        continue; // Continue processing other frames even if this one failed
      }
      
      // Handle special validations for PropertiesSchema
      if (frame.schema() instanceof JtdSchema.PropertiesSchema propsSchema) {
        validatePropertiesSchema(frame, propsSchema, errors);
      }
      
      // Push child frames based on schema type
      pushChildFrames(frame, stack);
    }
    
    return errors.isEmpty() ? Result.success() : Result.failure(errors);
  }
  
  /// Validates PropertiesSchema-specific rules (missing required, additional properties)
  void validatePropertiesSchema(Frame frame, JtdSchema.PropertiesSchema propsSchema, List<String> errors) {
    JsonValue instance = frame.instance();
    if (!(instance instanceof JsonObject obj)) {
      return; // Type validation should have already caught this
    }
    
    // Check for missing required properties
    for (var entry : propsSchema.properties().entrySet()) {
      String key = entry.getKey();
      JsonValue value = obj.members().get(key);
      
      if (value == null) {
        // Missing required property - create error with containing object offset
        String error = Jtd.Error.MISSING_REQUIRED_PROPERTY.message(key);
        String enrichedError = Jtd.enrichedError(error, frame, instance);
        errors.add(enrichedError);
        LOG.fine(() -> "Missing required property: " + enrichedError);
      }
    }
    
    // Check for additional properties if not allowed
    // RFC 8927 §2.2.8: Only the discriminator field is exempt from additionalProperties enforcement
    if (!propsSchema.additionalProperties()) {
      String discriminatorKey = frame.discriminatorKey();
      for (String key : obj.members().keySet()) {
        if (!propsSchema.properties().containsKey(key) && !propsSchema.optionalProperties().containsKey(key)) {
          // Only exempt the discriminator field itself, not all additional properties
          if (key.equals(discriminatorKey)) {
            continue; // Skip the discriminator field - it's exempt
          }
          JsonValue value = obj.members().get(key);
          // Additional property not allowed - create error with the value's offset
          String error = Jtd.Error.ADDITIONAL_PROPERTY_NOT_ALLOWED.message(key);
          String enrichedError = Jtd.enrichedError(error, frame, value);
          errors.add(enrichedError);
          LOG.fine(() -> "Additional property not allowed: " + enrichedError);
        }
      }
    }
  }
  
  /// Pushes child frames for complex schema types
  void pushChildFrames(Frame frame, java.util.Deque<Frame> stack) {
    JtdSchema schema = frame.schema();
    JsonValue instance = frame.instance();
    
    LOG.finer(() -> "Pushing child frames for schema type: " + schema.getClass().getSimpleName());
    
    switch (schema) {
      case JtdSchema.ElementsSchema elementsSchema -> {
        if (instance instanceof JsonArray arr) {
          int index = 0;
          for (JsonValue element : arr.elements()) {
            String childPtr = frame.ptr() + "/" + index;
            Crumbs childCrumbs = frame.crumbs().withArrayIndex(index);
            Frame childFrame = new Frame(elementsSchema.elements(), element, childPtr, childCrumbs);
            stack.push(childFrame);
            LOG.finer(() -> "Pushed array element frame at " + childPtr);
            index++;
          }
        }
      }
      case JtdSchema.PropertiesSchema propsSchema -> {
        if (instance instanceof JsonObject obj) {
          String discriminatorKey = frame.discriminatorKey();

          for (var entry : propsSchema.properties().entrySet()) {
            String key = entry.getKey();

            // Skip the discriminator field - it was already validated by discriminator logic
            if (key.equals(discriminatorKey)) {
              LOG.finer(() -> "Skipping discriminator field validation for: " + key);
              continue;
            }

            JsonValue value = obj.members().get(key);

            if (value != null) {
              String childPtr = frame.ptr() + "/" + key;
              Crumbs childCrumbs = frame.crumbs().withObjectField(key);
              Frame childFrame = new Frame(entry.getValue(), value, childPtr, childCrumbs);
              stack.push(childFrame);
              LOG.finer(() -> "Pushed required property frame at " + childPtr);
            }
          }

          for (var entry : propsSchema.optionalProperties().entrySet()) {
            String key = entry.getKey();

            // Skip the discriminator field - it was already validated by discriminator logic
            if (key.equals(discriminatorKey)) {
              LOG.finer(() -> "Skipping discriminator field validation for optional: " + key);
              continue;
            }

            JtdSchema childSchema = entry.getValue();
            JsonValue value = obj.members().get(key);

            if (value != null) {
              String childPtr = frame.ptr() + "/" + key;
              Crumbs childCrumbs = frame.crumbs().withObjectField(key);
              Frame childFrame = new Frame(childSchema, value, childPtr, childCrumbs);
              stack.push(childFrame);
              LOG.finer(() -> "Pushed optional property frame at " + childPtr);
            }
          }

        }
      }
      case JtdSchema.ValuesSchema valuesSchema -> {
        if (instance instanceof JsonObject obj) {
          for (var entry : obj.members().entrySet()) {
            String key = entry.getKey();
            JsonValue value = entry.getValue();
            String childPtr = frame.ptr() + "/" + key;
            Crumbs childCrumbs = frame.crumbs().withObjectField(key);
            Frame childFrame = new Frame(valuesSchema.values(), value, childPtr, childCrumbs);
            stack.push(childFrame);
            LOG.finer(() -> "Pushed values schema frame at " + childPtr);
          }
        }
      }
      case JtdSchema.DiscriminatorSchema discSchema -> {
        if (instance instanceof JsonObject obj) {
          JsonValue discriminatorValue = obj.members().get(discSchema.discriminator());
          if (discriminatorValue instanceof JsonString discStr) {
            String discriminatorValueStr = discStr.string();
            JtdSchema variantSchema = discSchema.mapping().get(discriminatorValueStr);
            if (variantSchema != null) {

              Frame variantFrame = new Frame(variantSchema, instance, frame.ptr(), frame.crumbs(), discSchema.discriminator());
              stack.push(variantFrame);
              LOG.finer(() -> "Pushed discriminator variant frame for " + discriminatorValueStr + " with discriminator key: " + discSchema.discriminator());
            }
          }
        }
      }
      case JtdSchema.RefSchema refSchema -> {
        try {
          JtdSchema resolved = refSchema.target();
          Frame resolvedFrame = new Frame(resolved, instance, frame.ptr(),
              frame.crumbs(), frame.discriminatorKey());
          pushChildFrames(resolvedFrame, stack);
          LOG.finer(() -> "Pushed ref schema resolved to " +
              resolved.getClass().getSimpleName() + " for ref: " + refSchema.ref());
        } catch (IllegalStateException e) {
          LOG.finer(() -> "No child frames for unresolved ref: " + refSchema.ref());
        }
      }
      default -> // Simple schemas (Empty, Type, Enum, Nullable) don't push child frames
          LOG.finer(() -> "No child frames for schema type: " + schema.getClass().getSimpleName());
    }
  }
  
  /// Compiles a JsonValue into a JtdSchema based on RFC 8927 rules
  JtdSchema compileSchema(JsonValue schema) {
    return compileSchema(schema, true); // Root schema by default
  }
  
  /// Compiles a JsonValue into a JtdSchema based on RFC 8927 rules
  /// @param schema The JSON schema to compile
  /// @param isRoot Whether this is a root-level schema (can contain definitions)
  /// @return Compiled JtdSchema
  JtdSchema compileSchema(JsonValue schema, boolean isRoot) {
    if (!(schema instanceof JsonObject obj)) {
      throw new IllegalArgumentException("Schema must be an object");
    }

    // RFC 8927: Only root schemas can contain definitions
    if (!isRoot && obj.members().containsKey("definitions")) {
      throw new IllegalArgumentException("Nested schemas cannot contain definitions, found: " + 
          Json.toDisplayString(obj, 0));
    }

    // First pass: register definition keys as placeholders (only for root schemas)
    if (isRoot && obj.members().containsKey("definitions")) {
      JsonValue definitionsValue = obj.members().get("definitions");
      if (!(definitionsValue instanceof JsonObject defsObj)) {
        throw new IllegalArgumentException("definitions must be an object");
      }
      for (String key : defsObj.members().keySet()) {
        definitions.putIfAbsent(key, null);
      }
    }

    // Second pass: compile each definition if not already compiled (only for root schemas)
    if (isRoot && obj.members().containsKey("definitions")) {
      JsonValue definitionsValue = obj.members().get("definitions");
      if (!(definitionsValue instanceof JsonObject defsObj)) {
        throw new IllegalArgumentException("definitions must be an object");
      }
      for (String key : defsObj.members().keySet()) {
        if (definitions.get(key) == null) {
          JsonValue rawDef = defsObj.members().get(key);
          // Compile definitions normally (RFC 8927 strict)
          JtdSchema compiled = compileSchema(rawDef, false); // Definitions are not root schemas
          definitions.put(key, compiled);
        }
      }
    }

    return compileObjectSchema(obj);
  }

  /// Compiles an object schema according to RFC 8927 with strict semantics
  /// @param obj The JSON object to compile
  /// @return Compiled JtdSchema
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
    
    // RFC 8927: Check for form-specific properties that shouldn't be mixed
    if (forms.size() == 1) {
      String form = forms.getFirst();
      switch (form) {
        case "elements", "values", "enum", "ref", "type" -> {
          // These forms should not have properties-specific attributes
          if (members.containsKey("additionalProperties")) {
            throw new IllegalArgumentException(form + " schema cannot contain additionalProperties");
          }
          if (members.containsKey("properties")) {
            throw new IllegalArgumentException(form + " schema cannot contain properties");
          }
          if (members.containsKey("optionalProperties")) {
            throw new IllegalArgumentException(form + " schema cannot contain optionalProperties");
          }
        }
        case "discriminator" -> {
          // Discriminator should not have properties-specific attributes (except in mapping values)
          if (members.containsKey("additionalProperties")) {
            throw new IllegalArgumentException("discriminator schema cannot contain additionalProperties");
          }
          if (members.containsKey("properties")) {
            throw new IllegalArgumentException("discriminator schema cannot contain properties");
          }
          if (members.containsKey("optionalProperties")) {
            throw new IllegalArgumentException("discriminator schema cannot contain optionalProperties");
          }
        }
      }
    }
    
    // RFC 8927: schemas must have exactly one of these forms
    if (forms.size() > 1) {
      throw new IllegalArgumentException("Schema has multiple forms: " + forms);
    }
    
    // RFC 8927: discriminator schemas must have both discriminator and mapping
    if (members.containsKey("discriminator") && !members.containsKey("mapping")) {
      throw new IllegalArgumentException("discriminator schema must also contain mapping");
    }
    if (members.containsKey("mapping") && !members.containsKey("discriminator")) {
      throw new IllegalArgumentException("mapping can only appear with discriminator in schema: " + 
          Json.toDisplayString(obj, 0));
    }
    
    // Parse the specific schema form
    JtdSchema schema;
    
    // RFC 8927: {} is the empty form and accepts all instances
    if (forms.isEmpty() && obj.members().isEmpty()) {
      LOG.finer(() -> "Empty schema {} encountered. Per RFC 8927 this means 'accept anything'. "
        + "Some non-JTD validators interpret {} with object semantics; this implementation follows RFC 8927.");
      return new JtdSchema.EmptySchema();
    } else if (forms.isEmpty()) {
      // Check if this is effectively an empty schema (ignoring metadata keys)
      // But first validate nullable if present
      if (members.containsKey("nullable")) {
        JsonValue nullableValue = members.get("nullable");
        if (!(nullableValue instanceof JsonBoolean bool)) {
          throw new IllegalArgumentException("nullable must be a boolean, found: " + 
              nullableValue.getClass().getSimpleName() + " in schema: " + Json.toDisplayString(obj, 0));
        }
        // If nullable is valid, this becomes a nullable empty schema
        if (bool.bool()) {
          return new JtdSchema.NullableSchema(new JtdSchema.EmptySchema());
        }
      }
      
      boolean hasNonMetadataKeys = members.keySet().stream()
          .anyMatch(key -> !key.equals("nullable") && !key.equals("metadata") && !key.equals("definitions"));
      
      if (!hasNonMetadataKeys) {
        // This is an empty schema (possibly with metadata)
        LOG.finer(() -> "Empty schema encountered (with metadata: " + members.keySet() + "). "
          + "Per RFC 8927 this means 'accept anything'. "
          + "Some non-JTD validators interpret {} with object semantics; this implementation follows RFC 8927.");
        return new JtdSchema.EmptySchema();
      } else {
        // This should not happen in RFC 8927 - unknown keys present
        throw new IllegalArgumentException("Schema contains unknown keys: " + 
            members.keySet().stream()
                .filter(key -> !key.equals("nullable") && !key.equals("metadata") && !key.equals("definitions"))
                .toList());
      }
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
    
    // Handle nullable flag (can be combined with any form)
    if (members.containsKey("nullable")) {
      JsonValue nullableValue = members.get("nullable");
      if (!(nullableValue instanceof JsonBoolean bool)) {
        throw new IllegalArgumentException("nullable must be a boolean, found: " + 
            nullableValue.getClass().getSimpleName() + " in schema: " + Json.toDisplayString(obj, 0));
      }
      if (bool.bool()) {
        return new JtdSchema.NullableSchema(schema);
      }
    }
    // Default: non-nullable
    return schema;
  }
  
  JtdSchema compileRefSchema(JsonObject obj) {
    JsonValue refValue = obj.members().get("ref");
    if (!(refValue instanceof JsonString str)) {
      throw new IllegalArgumentException("ref must be a string");
    }
    String ref = str.string();
    
    // RFC 8927: Validate that ref points to an existing definition at compile time
    if (!definitions.containsKey(ref)) {
      throw new IllegalArgumentException("ref '" + ref + "' points to non-existent definition in schema: " + 
          Json.toDisplayString(obj, 0));
    }
    
    return new JtdSchema.RefSchema(ref, definitions);
  }
  
  JtdSchema compileTypeSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    
    // Validate that only expected keys are present
    for (String key : members.keySet()) {
      if (!key.equals("type") && !key.equals("nullable") && !key.equals("metadata") && !key.equals("definitions")) {
        throw new IllegalArgumentException("Type schema contains unknown key: '" + key + 
            "' in schema: " + Json.toDisplayString(obj, 0));
      }
    }
    
    JsonValue typeValue = members.get("type");
    if (!(typeValue instanceof JsonString str)) {
      throw new IllegalArgumentException("type must be a string");
    }
    
    String typeStr = str.string();
    
    // RFC 8927 §2.2.3: Validate that type is one of the supported primitive types
    if (!VALID_TYPES.contains(typeStr)) {
      throw new IllegalArgumentException("unknown type: '" + typeStr + 
          "', expected one of: boolean, string, timestamp, int8, uint8, int16, uint16, int32, uint32, float32, float64");
    }
    
    return new JtdSchema.TypeSchema(typeStr);
  }
  
  JtdSchema compileEnumSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue enumValue = members.get("enum");
    if (!(enumValue instanceof JsonArray arr)) {
      throw new IllegalArgumentException("enum must be an array");
    }
    
    List<String> values = new ArrayList<>();
    for (JsonValue value : arr.elements()) {
      if (!(value instanceof JsonString str)) {
        throw new IllegalArgumentException("enum values must be strings");
      }
      values.add(str.string());
    }
    
    if (values.isEmpty()) {
      throw new IllegalArgumentException("enum cannot be empty");
    }
    
    // RFC 8927: Check for duplicates
    Set<String> uniqueValues = new java.util.HashSet<>(values);
    if (uniqueValues.size() != values.size()) {
      throw new IllegalArgumentException("enum contains duplicate values: " + 
          values.stream().collect(java.util.stream.Collectors.joining(", ", "[", "]")));
    }
    
    return new JtdSchema.EnumSchema(values);
  }
  
  JtdSchema compileElementsSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue elementsValue = members.get("elements");
    JtdSchema elementsSchema = compileSchema(elementsValue, false); // Elements are nested schemas
    return new JtdSchema.ElementsSchema(elementsSchema);
  }

  JtdSchema compilePropertiesSchema(JsonObject obj) {
    Map<String, JtdSchema> properties = Map.of();
    Map<String, JtdSchema> optionalProperties = Map.of();
    
    Map<String, JsonValue> members = obj.members();
    
    // Parse required properties
    if (members.containsKey("properties")) {
      JsonValue propsValue = members.get("properties");
      if (!(propsValue instanceof JsonObject propsObj)) {
        throw new IllegalArgumentException("properties must be an object");
      }
      properties = parsePropertySchemas(propsObj); // Property schemas are nested
    }
    
    // Parse optional properties
    if (members.containsKey("optionalProperties")) {
      JsonValue optPropsValue = members.get("optionalProperties");
      if (!(optPropsValue instanceof JsonObject optPropsObj)) {
        throw new IllegalArgumentException("optionalProperties must be an object");
      }
      optionalProperties = parsePropertySchemas(optPropsObj); // Property schemas are nested
    }
    
    // RFC 8927: Check for key overlap between properties and optionalProperties
    for (String key : properties.keySet()) {
      if (optionalProperties.containsKey(key)) {
        throw new IllegalArgumentException("Key '" + key + 
            "' cannot be defined in both properties and optionalProperties in schema: " + 
            Json.toDisplayString(obj, 0));
      }
    }
    
    // RFC 8927: additionalProperties defaults to false when properties or optionalProperties are defined
    boolean additionalProperties = false;
    if (members.containsKey("additionalProperties")) {
      JsonValue addPropsValue = members.get("additionalProperties");
      if (!(addPropsValue instanceof JsonBoolean bool)) {
        throw new IllegalArgumentException("additionalProperties must be a boolean");
      }
      additionalProperties = bool.bool();
    }  // Empty schema with no properties defined rejects additional properties by default

    return new JtdSchema.PropertiesSchema(properties, optionalProperties, additionalProperties);
  }
  
  JtdSchema compileValuesSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue valuesValue = members.get("values");
    JtdSchema valuesSchema = compileSchema(valuesValue, false); // Values are nested schemas
    return new JtdSchema.ValuesSchema(valuesSchema);
  }

  JtdSchema compileDiscriminatorSchema(JsonObject obj) {
    Map<String, JsonValue> members = obj.members();
    JsonValue discriminatorValue = members.get("discriminator");
    if (!(discriminatorValue instanceof JsonString discStr)) {
      throw new IllegalArgumentException("discriminator must be a string");
    }
    String discriminatorKey = discStr.string();
    
    JsonValue mappingValue = members.get("mapping");
    if (!(mappingValue instanceof JsonObject mappingObj)) {
      throw new IllegalArgumentException("mapping must be an object");
    }
    
    Map<String, JtdSchema> mapping = new java.util.HashMap<>();
    for (String key : mappingObj.members().keySet()) {
      JsonValue variantValue = mappingObj.members().get(key);
      
      // Early validation: mapping values must be objects (for PropertiesSchema)
      if (!(variantValue instanceof JsonObject)) {
        throw new IllegalArgumentException("Discriminator mapping '" + key + "' must be an object (properties schema)");
      }
      
      JsonObject variantObj = (JsonObject) variantValue;
      
      // Check for nullable flag before compiling
      if (variantObj.members().containsKey("nullable") && 
          variantObj.members().get("nullable") instanceof JsonBoolean bool &&
          bool.bool()) {
        throw new IllegalArgumentException("Discriminator mapping '" + key + "' cannot be nullable");
      }
      
      JtdSchema variantSchema = compileSchema(variantValue, false); // Mapping values are nested schemas
      
      // RFC 8927 §2.2.8: Validate discriminator mapping constraints
      validateDiscriminatorMapping(key, variantSchema, discriminatorKey);
      
      mapping.put(key, variantSchema);
    }
    
    return new JtdSchema.DiscriminatorSchema(discriminatorKey, mapping);
  }
  
  /// Validates discriminator mapping constraints per RFC 8927 §2.2.8
  void validateDiscriminatorMapping(String mappingKey, JtdSchema variantSchema, String discriminatorKey) {
    // Check if this is a nullable schema and unwrap it
    JtdSchema unwrappedSchema = variantSchema;
    boolean wasNullableWrapper = false;
    
    if (variantSchema instanceof JtdSchema.NullableSchema nullableSchema) {
      wasNullableWrapper = true;
      unwrappedSchema = nullableSchema.wrapped();
    }
    
    // RFC 8927 §2.2.8: Mapping values must be PropertiesSchema
    if (!(unwrappedSchema instanceof JtdSchema.PropertiesSchema)) {
      String schemaType = wasNullableWrapper ? "nullable " + unwrappedSchema.getClass().getSimpleName() : 
                          unwrappedSchema.getClass().getSimpleName();
      throw new IllegalArgumentException(
        "Discriminator mapping '" + mappingKey + "' must be a properties schema, found: " + schemaType);
    }
    
    JtdSchema.PropertiesSchema propsSchema = (JtdSchema.PropertiesSchema) unwrappedSchema;
    
    // RFC 8927 §2.2.8: Mapped schemas cannot have nullable: true
    if (wasNullableWrapper) {
      throw new IllegalArgumentException(
        "Discriminator mapping '" + mappingKey + "' cannot be nullable");
    }
    
    // RFC 8927 §2.2.8: Mapped schemas cannot define the discriminator key in properties or optionalProperties
    if (propsSchema.properties().containsKey(discriminatorKey)) {
      throw new IllegalArgumentException(
        "Discriminator mapping '" + mappingKey + "' cannot define discriminator key '" + discriminatorKey + 
        "' in properties");
    }
    
    if (propsSchema.optionalProperties().containsKey(discriminatorKey)) {
      throw new IllegalArgumentException(
        "Discriminator mapping '" + mappingKey + "' cannot define discriminator key '" + discriminatorKey + 
        "' in optionalProperties");
    }
  }
  
  // Removed: RFC 8927 strict mode - no context-aware ref resolution needed
  
  /// Extracts and stores top-level definitions for ref resolution
  private Map<String, JtdSchema> parsePropertySchemas(JsonObject propsObj) {
    Map<String, JtdSchema> schemas = new java.util.HashMap<>();
    for (String key : propsObj.members().keySet()) {
      JsonValue schemaValue = propsObj.members().get(key);
      schemas.put(key, compileSchema(schemaValue, false));
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

  /// Standardized validation error types for JTD schema validation
  /// Provides consistent error messages following RFC 8927 specification
  public enum Error {
    /// Unknown type specified in schema
    UNKNOWN_TYPE("unknown type: '%s'"),

    /// Expected boolean but got different type
    EXPECTED_BOOLEAN("expected boolean, got %s"),

    /// Expected string but got different type
    EXPECTED_STRING("expected string, got %s"),

    /// Expected timestamp string but got different type
    EXPECTED_TIMESTAMP("expected timestamp (string), got %s"),

    /// Expected integer but got float
    EXPECTED_INTEGER("expected integer, got float"),

    /// Expected specific numeric type but got different type
    EXPECTED_NUMERIC_TYPE("expected %s, got %s"),

    /// Expected array but got different type
    EXPECTED_ARRAY("expected array, got %s"),

    /// Expected object but got different type
    EXPECTED_OBJECT("expected object, got %s"),

    /// String value not in enum
    VALUE_NOT_IN_ENUM("value '%s' not in enum: %s"),

    /// Expected string for enum but got different type
    EXPECTED_STRING_FOR_ENUM("expected string for enum, got %s"),

    /// Missing required property
    MISSING_REQUIRED_PROPERTY("missing required property: '%s'"),

    /// Additional property not allowed
    ADDITIONAL_PROPERTY_NOT_ALLOWED("additional property not allowed: '%s'"),

    /// Discriminator must be a string
    DISCRIMINATOR_MUST_BE_STRING("discriminator '%s' must be a string"),

    /// Discriminator value not in mapping
    DISCRIMINATOR_VALUE_NOT_IN_MAPPING("discriminator value '%s' not in mapping");

    private final String messageTemplate;

    Error(String messageTemplate) {
      this.messageTemplate = messageTemplate;
    }

    /// Creates a concise error message without the actual JSON value
    public String message(Object... args) {
      return String.format(messageTemplate, args);
    }

    /// Creates a verbose error message including the actual JSON value
    public String message(JsonValue invalidValue, Object... args) {
      String baseMessage = String.format(messageTemplate, args);
      String displayValue = Json.toDisplayString(invalidValue, 0); // Use compact format
      return baseMessage + " (was: " + displayValue + ")";
    }
  }
}
