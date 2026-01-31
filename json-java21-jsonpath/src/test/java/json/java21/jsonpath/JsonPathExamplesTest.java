package json.java21.jsonpath;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonPathExamplesTest extends JsonPathTestBase {

    static final String STORE_DOC = """
        { "store": {
            "book": [
              { "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
              },
              { "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
              },
              { "category": "fiction",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
              },
              { "category": "fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "isbn": "0-395-19395-8",
                "price": 22.99
              }
            ],
            "bicycle": {
              "color": "red",
              "price": 19.95
            }
          }
        }
        """;

    @Test
    void store_book_selects_book_array() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$.store.book").select(doc);
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst()).isInstanceOf(JsonArray.class);
    }

    @Test
    void store_wildcard_selects_all_store_children() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$.store.*").select(doc);
        assertThat(matches).hasSize(2);
        assertThat(matches.getFirst()).isInstanceOf(JsonArray.class);
        assertThat(matches.get(1)).isInstanceOf(JsonObject.class);
    }
}

