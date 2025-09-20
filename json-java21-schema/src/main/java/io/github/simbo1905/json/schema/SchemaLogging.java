package io.github.simbo1905.json.schema;

import java.util.logging.Logger;

/// Centralized logger for the JSON Schema subsystem.
/// All classes must use this logger via:
///   import static io.github.simbo1905.json.schema.SchemaLogging.LOG;
final class SchemaLogging {
  public static final Logger LOG = Logger.getLogger("io.github.simbo1905.json.schema");
  private SchemaLogging() {}
}
