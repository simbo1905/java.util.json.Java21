package json.java21.jtd;

import jdk.sandbox.java.util.json.JsonValue;

/// Stack frame for iterative validation with path and offset tracking
record Frame(JtdSchema schema, JsonValue instance, String ptr, Crumbs crumbs, String discriminatorKey) {
  /// Constructor for normal validation without discriminator context
  Frame(JtdSchema schema, JsonValue instance, String ptr, Crumbs crumbs) {
    this(schema, instance, ptr, crumbs, null);
  }

  @Override
  public String toString() {
    final var kind = schema.getClass().getSimpleName();
    final var tag = (schema instanceof JtdSchema.RefSchema r) ? "(ref=" + r.ref() + ")" : "";
    return "Frame[schema=" + kind + tag + ", instance=" + instance + ", ptr=" + ptr +
        ", crumbs=" + crumbs + ", discriminatorKey=" + discriminatorKey + "]";
  }
}
