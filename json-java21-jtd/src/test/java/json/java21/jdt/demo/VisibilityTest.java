package json.java21.jdt.demo;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;
import json.java21.jtd.Jtd;
import json.java21.jtd.JtdTestBase;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;


public class VisibilityTest extends JtdTestBase {

  static final Logger LOG = Logger.getLogger("json.java21.jtd");

  /// Test ref schema resolution with valid definitions
  /// RFC 8927 Section 3.3.2: Ref schemas must resolve against definitions
  @Test
  public void testRefSchemaValid() {
    JsonValue schema = Json.parse("{\"ref\": \"address\", \"definitions\": {\"address\": {\"type\": \"string\"}}}");
    JsonValue validData = Json.parse("\"123 Main St\"");

    Jtd validator = new Jtd();
    Jtd.Result result = validator.validate(schema, validData);

    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isEmpty();
    LOG.fine(() -> "Ref schema valid test - schema: " + schema + ", data: " + validData);
  }
}
