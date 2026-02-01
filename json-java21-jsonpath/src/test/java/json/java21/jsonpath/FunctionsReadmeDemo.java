package json.java21.jsonpath;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class FunctionsReadmeDemo extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(FunctionsReadmeDemo.class.getName());

    @Test
    void testSummingNumbersLax() {
        LOG.info(() -> "FunctionsReadmeDemo#testSummingNumbersLax");
        JsonValue doc = Json.parse("""
            {
              "store": {
                "book": [
                  { "category": "reference", "price": 8.95 },
                  { "category": "fiction", "price": 12.99 },
                  { "category": "fiction", "price": "Not For Sale" },
                  { "category": "fiction", "price": 22.99 }
                ]
              }
            }
            """);

        JsonPath path = JsonPath.parse("$.store.book[*].price");

        double total = path.query(doc).stream()
            .map(JsonPathStreams::asDoubleOrNull)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .sum();

        assertThat(total).isCloseTo(8.95 + 12.99 + 22.99, within(0.001));
    }

    @Test
    void testAverageStrict() {
        LOG.info(() -> "FunctionsReadmeDemo#testAverageStrict");
        JsonValue doc = Json.parse("""
            {
              "temperatures": [ 98.6, 99.1, 98.4 ]
            }
            """);

        JsonPath path = JsonPath.parse("$.temperatures[*]");

        OptionalDouble avg = path.query(doc).stream()
            .map(JsonPathStreams::asDouble)
            .mapToDouble(Double::doubleValue)
            .average();

        assertThat(avg).isPresent();
        assertThat(avg.getAsDouble()).isBetween(98.6, 98.8); // 98.7 approx
    }

    @Test
    void testFilteringByType() {
        LOG.info(() -> "FunctionsReadmeDemo#testFilteringByType");
        JsonValue doc = Json.parse("""
            [ "apple", 100, "banana", true, "cherry", null ]
            """);

        JsonPath path = JsonPath.parse("$[*]");

        // Get all strings
        List<String> strings = path.query(doc).stream()
            .filter(JsonPathStreams::isString)
            .map(JsonPathStreams::asString)
            .toList();

        assertThat(strings).containsExactly("apple", "banana", "cherry");
    }
}
