package json.java21.jtd;

import jdk.sandbox.java.util.json.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/// Stack-machine interpreter wrapped as a [JtdValidator].
///
/// Produces RFC 8927 `(instancePath, schemaPath)` error pairs by walking
/// the compiled [JtdSchema] AST with an explicit work stack.
final class InterpreterValidator implements JtdValidator {

  private static final Logger LOG = Logger.getLogger(InterpreterValidator.class.getName());

  private final JtdSchema schema;
  private final String schemaJson;

  InterpreterValidator(JtdSchema schema, Jtd jtd, String schemaJson) {
    this.schema = schema;
    this.schemaJson = schemaJson;
  }

  @Override
  public JtdValidationResult validate(JsonValue instance) {
    LOG.fine(() -> "InterpreterValidator validating instance");
    final var errors = new ArrayList<JtdValidationError>();
    final var stack = new java.util.ArrayDeque<Frame>();

    stack.push(new Frame(schema, instance, "", Crumbs.root(), "", null));

    while (!stack.isEmpty()) {
      final var frame = stack.pop();
      stepRfc8927(frame, stack, errors);
    }

    return errors.isEmpty()
        ? JtdValidationResult.success()
        : JtdValidationResult.failure(errors);
  }

  @Override
  public String toString() {
    return schemaJson;
  }

  // ------------------------------------------------------------------
  // RFC 8927 step function producing (instancePath, schemaPath) pairs
  // ------------------------------------------------------------------

  private void stepRfc8927(Frame frame, java.util.Deque<Frame> stack, List<JtdValidationError> errors) {
    final var node = frame.schema();

    if (node instanceof JtdSchema.NullableSchema nullable) {
      if (frame.instance() instanceof jdk.sandbox.java.util.json.JsonNull) return;
      stepRfc8927(new Frame(nullable.wrapped(), frame.instance(), frame.ptr(),
          frame.crumbs(), frame.schemaPath(), frame.discriminatorKey()), stack, errors);
      return;
    }

    switch (node) {
      case JtdSchema.EmptySchema empty -> { /* accepts anything */ }
      case JtdSchema.RefSchema ref -> {
        final var resolved = ref.target();
        stepRfc8927(new Frame(resolved, frame.instance(), frame.ptr(),
            frame.crumbs(), "/definitions/" + ref.ref(), frame.discriminatorKey()), stack, errors);
      }
      case JtdSchema.TypeSchema type -> stepType(frame, type, errors);
      case JtdSchema.EnumSchema enumS -> stepEnum(frame, enumS, errors);
      case JtdSchema.ElementsSchema elems -> stepElements(frame, elems, stack, errors);
      case JtdSchema.PropertiesSchema props -> stepProperties(frame, props, stack, errors);
      case JtdSchema.ValuesSchema vals -> stepValues(frame, vals, stack, errors);
      case JtdSchema.DiscriminatorSchema disc -> stepDiscriminator(frame, disc, stack, errors);
      case JtdSchema.NullableSchema n -> throw new AssertionError("unreachable: handled above");
    }
  }

  private void stepType(Frame frame, JtdSchema.TypeSchema type, List<JtdValidationError> errors) {
    final var instance = frame.instance();
    final var ok = switch (type.type()) {
      case "boolean" -> instance instanceof jdk.sandbox.java.util.json.JsonBoolean;
      case "string" -> instance instanceof jdk.sandbox.java.util.json.JsonString;
      case "timestamp" -> isTimestamp(instance);
      case "float32", "float64" -> instance instanceof jdk.sandbox.java.util.json.JsonNumber;
      case "int8" -> isIntInRange(instance, -128, 127);
      case "uint8" -> isIntInRange(instance, 0, 255);
      case "int16" -> isIntInRange(instance, -32768, 32767);
      case "uint16" -> isIntInRange(instance, 0, 65535);
      case "int32" -> isIntInRange(instance, Integer.MIN_VALUE, Integer.MAX_VALUE);
      case "uint32" -> isIntInRange(instance, 0, 4294967295L);
      default -> false;
    };
    if (!ok) {
      errors.add(new JtdValidationError(frame.ptr(), frame.schemaPath() + "/type"));
    }
  }

  private void stepEnum(Frame frame, JtdSchema.EnumSchema enumS, List<JtdValidationError> errors) {
    if (!(frame.instance() instanceof jdk.sandbox.java.util.json.JsonString str)
        || !enumS.values().contains(str.string())) {
      errors.add(new JtdValidationError(frame.ptr(), frame.schemaPath() + "/enum"));
    }
  }

  private void stepElements(Frame frame, JtdSchema.ElementsSchema elems,
                            java.util.Deque<Frame> stack, List<JtdValidationError> errors) {
    if (!(frame.instance() instanceof jdk.sandbox.java.util.json.JsonArray arr)) {
      errors.add(new JtdValidationError(frame.ptr(), frame.schemaPath() + "/elements"));
      return;
    }
    final var childSchemaPath = frame.schemaPath() + "/elements";
    int i = 0;
    for (final var element : arr.elements()) {
      stack.push(new Frame(elems.elements(), element,
          frame.ptr() + "/" + i,
          frame.crumbs().withArrayIndex(i),
          childSchemaPath, null));
      i++;
    }
  }

  private void stepProperties(Frame frame, JtdSchema.PropertiesSchema props,
                              java.util.Deque<Frame> stack, List<JtdValidationError> errors) {
    if (!(frame.instance() instanceof jdk.sandbox.java.util.json.JsonObject obj)) {
      final var guardPath = props.properties().isEmpty() ? "/optionalProperties" : "/properties";
      errors.add(new JtdValidationError(frame.ptr(), frame.schemaPath() + guardPath));
      return;
    }

    final var members = obj.members();
    final var discKey = frame.discriminatorKey();
    final var sp = frame.schemaPath();

    for (final var entry : props.properties().entrySet()) {
      final var key = entry.getKey();
      if (!members.containsKey(key)) {
        errors.add(new JtdValidationError(frame.ptr(), sp + "/properties/" + key));
      }
    }

    if (!props.additionalProperties()) {
      for (final var key : members.keySet()) {
        if (!props.properties().containsKey(key)
            && !props.optionalProperties().containsKey(key)
            && !key.equals(discKey)) {
          errors.add(new JtdValidationError(frame.ptr() + "/" + key, sp));
        }
      }
    }

    for (final var entry : props.properties().entrySet()) {
      final var key = entry.getKey();
      if (key.equals(discKey)) continue;
      final var value = members.get(key);
      if (value != null) {
        stack.push(new Frame(entry.getValue(), value,
            frame.ptr() + "/" + key,
            frame.crumbs().withObjectField(key),
            sp + "/properties/" + key, null));
      }
    }

    for (final var entry : props.optionalProperties().entrySet()) {
      final var key = entry.getKey();
      if (key.equals(discKey)) continue;
      final var value = members.get(key);
      if (value != null) {
        stack.push(new Frame(entry.getValue(), value,
            frame.ptr() + "/" + key,
            frame.crumbs().withObjectField(key),
            sp + "/optionalProperties/" + key, null));
      }
    }
  }

  private void stepValues(Frame frame, JtdSchema.ValuesSchema vals,
                          java.util.Deque<Frame> stack, List<JtdValidationError> errors) {
    if (!(frame.instance() instanceof jdk.sandbox.java.util.json.JsonObject obj)) {
      errors.add(new JtdValidationError(frame.ptr(), frame.schemaPath() + "/values"));
      return;
    }
    final var childSchemaPath = frame.schemaPath() + "/values";
    for (final var entry : obj.members().entrySet()) {
      stack.push(new Frame(vals.values(), entry.getValue(),
          frame.ptr() + "/" + entry.getKey(),
          frame.crumbs().withObjectField(entry.getKey()),
          childSchemaPath, null));
    }
  }

  private void stepDiscriminator(Frame frame, JtdSchema.DiscriminatorSchema disc,
                                 java.util.Deque<Frame> stack, List<JtdValidationError> errors) {
    if (!(frame.instance() instanceof jdk.sandbox.java.util.json.JsonObject obj)) {
      errors.add(new JtdValidationError(frame.ptr(), frame.schemaPath() + "/discriminator"));
      return;
    }

    final var members = obj.members();
    final var sp = frame.schemaPath();

    if (!members.containsKey(disc.discriminator())) {
      errors.add(new JtdValidationError(frame.ptr(), sp + "/discriminator"));
      return;
    }

    final var tagValue = members.get(disc.discriminator());
    if (!(tagValue instanceof jdk.sandbox.java.util.json.JsonString tagStr)) {
      errors.add(new JtdValidationError(
          frame.ptr() + "/" + disc.discriminator(),
          sp + "/discriminator"));
      return;
    }

    final var variant = disc.mapping().get(tagStr.string());
    if (variant == null) {
      errors.add(new JtdValidationError(
          frame.ptr() + "/" + disc.discriminator(),
          sp + "/mapping"));
      return;
    }

    stack.push(new Frame(variant, frame.instance(), frame.ptr(),
        frame.crumbs(),
        sp + "/mapping/" + tagStr.string(),
        disc.discriminator()));
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private static boolean isTimestamp(JsonValue instance) {
    if (!(instance instanceof jdk.sandbox.java.util.json.JsonString str)) return false;
    final var value = str.string();
    if (!JtdSchema.TypeSchema.RFC3339.matcher(value).matches()) return false;
    try {
      final var normalized = value.replace(":60", ":59");
      java.time.OffsetDateTime.parse(normalized, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isIntInRange(JsonValue instance, long min, long max) {
    if (!(instance instanceof jdk.sandbox.java.util.json.JsonNumber num)) return false;
    final var d = num.toDouble();
    if (d != Math.floor(d)) return false;
    if (d > Long.MAX_VALUE || d < Long.MIN_VALUE) return false;
    final var l = num.toLong();
    return l >= min && l <= max;
  }
}
