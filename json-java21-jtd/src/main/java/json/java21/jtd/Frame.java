package json.java21.jtd;

import jdk.sandbox.java.util.json.JsonValue;

/// Stack frame for iterative validation with path and offset tracking.
///
/// - `ptr` is the JSON Pointer into the **instance** (RFC 6901).
/// - `schemaPath` is the JSON Pointer into the **schema** (RFC 8927 §3.3).
/// - `crumbs` is a human-readable breadcrumb trail for the enriched error format.
/// - `discriminatorKey` carries the tag field name from a Discriminator to its
///   variant Properties step (see JTD_STACK_MACHINE_SPEC.md §4.8).
record Frame(JtdSchema schema, JsonValue instance, String ptr, Crumbs crumbs,
             String schemaPath, String discriminatorKey) {

  /// Constructor for normal validation without discriminator context or schema path
  Frame(JtdSchema schema, JsonValue instance, String ptr, Crumbs crumbs) {
    this(schema, instance, ptr, crumbs, "", null);
  }

  /// Constructor with schema path but no discriminator
  Frame(JtdSchema schema, JsonValue instance, String ptr, Crumbs crumbs, String schemaPath) {
    this(schema, instance, ptr, crumbs, schemaPath, null);
  }

  @Override
  public String toString() {
    final var kind = schema.getClass().getSimpleName();
    final var tag = (schema instanceof JtdSchema.RefSchema r) ? "(ref=" + r.ref() + ")" : "";
    return "Frame[schema=" + kind + tag + ", instance=" + instance + ", ptr=" + ptr +
        ", schemaPath=" + schemaPath + ", crumbs=" + crumbs + ", discriminatorKey=" + discriminatorKey + "]";
  }
}
