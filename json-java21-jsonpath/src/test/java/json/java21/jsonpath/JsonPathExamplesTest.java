package json.java21.jsonpath;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void recursive_descent_author_selects_all_authors() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$..author").select(doc);
        assertThat(asStrings(matches)).containsExactly(
                "Nigel Rees",
                "Evelyn Waugh",
                "Herman Melville",
                "J. R. R. Tolkien"
        );
    }

    @Test
    void book_wildcard_author_selects_all_authors() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$.store.book[*].author").select(doc);
        assertThat(asStrings(matches)).containsExactly(
                "Nigel Rees",
                "Evelyn Waugh",
                "Herman Melville",
                "J. R. R. Tolkien"
        );
    }

    @Test
    void bracket_notation_can_select_authors_by_index() {
        JsonValue doc = Json.parse(STORE_DOC);

        assertThat(singleString(doc, "$['store']['book'][0]['author']")).isEqualTo("Nigel Rees");
        assertThat(singleString(doc, "$['store']['book'][1]['author']")).isEqualTo("Evelyn Waugh");
        assertThat(singleString(doc, "$['store']['book'][2]['author']")).isEqualTo("Herman Melville");
        assertThat(singleString(doc, "$['store']['book'][3]['author']")).isEqualTo("J. R. R. Tolkien");
    }

    @Test
    void store_recursive_descent_price_selects_all_prices() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$.store..price").select(doc);
        assertThat(matches).hasSize(5);
        assertThat(matches.stream().map(JsonValue::toDouble).toList()).containsExactly(
                8.95, 12.99, 8.99, 22.99, 19.95
        );
    }

    @Test
    void recursive_descent_book_index_selects_third_book() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$..book[2]").select(doc);
        assertThat(matches).hasSize(1);
        assertThat(((JsonObject) matches.getFirst()).get("title").string()).isEqualTo("Moby Dick");
    }

    @Test
    void script_index_can_select_last_book() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$..book[(@.length-1)]").select(doc);
        assertThat(matches).hasSize(1);
        assertThat(((JsonObject) matches.getFirst()).get("title").string()).isEqualTo("The Lord of the Rings");
    }

    @Test
    void slice_can_select_last_book() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$..book[-1:]").select(doc);
        assertThat(matches).hasSize(1);
        assertThat(((JsonObject) matches.getFirst()).get("title").string()).isEqualTo("The Lord of the Rings");
    }

    @Test
    void union_and_slice_can_select_first_two_books() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var union = JsonPath.parse("$..book[0,1]").select(doc);
        assertThat(union).hasSize(2);
        assertThat(union.stream().map(v -> ((JsonObject) v).get("title").string()).toList()).containsExactly(
                "Sayings of the Century",
                "Sword of Honour"
        );

        final var slice = JsonPath.parse("$..book[:2]").select(doc);
        assertThat(slice).hasSize(2);
        assertThat(slice.stream().map(v -> ((JsonObject) v).get("title").string()).toList()).containsExactly(
                "Sayings of the Century",
                "Sword of Honour"
        );
    }

    @Test
    void filter_can_select_books_by_presence_of_isbn() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$..book[?(@.isbn)]").select(doc);
        assertThat(matches).hasSize(2);
        assertThat(matches.stream().map(v -> ((JsonObject) v).get("title").string()).toList()).containsExactly(
                "Moby Dick",
                "The Lord of the Rings"
        );
    }

    @Test
    void filter_can_select_books_by_price_less_than_10() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matchesTight = JsonPath.parse("$..book[?(@.price<10)]").select(doc);
        assertThat(matchesTight).hasSize(2);
        assertThat(matchesTight.stream().map(v -> ((JsonObject) v).get("title").string()).toList()).containsExactly(
                "Sayings of the Century",
                "Moby Dick"
        );

        final var matchesSpaced = JsonPath.parse("$.store.book[?(@.price < 10)]").select(doc);
        assertThat(matchesSpaced.stream().map(v -> ((JsonObject) v).get("title").string()).toList()).containsExactly(
                "Sayings of the Century",
                "Moby Dick"
        );
    }

    @Test
    void dot_notation_examples_select_titles() {
        JsonValue doc = Json.parse(STORE_DOC);

        assertThat(singleString(doc, "$.store.book[0].title")).isEqualTo("Sayings of the Century");
        assertThat(singleString(doc, "$.store.book[(@.length-1)].title")).isEqualTo("The Lord of the Rings");
    }

    @Test
    void recursive_descent_wildcard_selects_all_children_in_tree() {
        JsonValue doc = Json.parse(STORE_DOC);

        final var matches = JsonPath.parse("$..*").select(doc);
        assertThat(matches).hasSize(27);
        assertThat(matches.stream().map(JsonValue::toString).toList()).contains(
                "\"Nigel Rees\"",
                "\"red\"",
                "19.95"
        );
    }

    private static List<String> asStrings(List<JsonValue> values) {
        return values.stream().map(v -> ((JsonString) v).string()).toList();
    }

    private static String singleString(JsonValue doc, String path) {
        final var matches = JsonPath.parse(path).select(doc);
        assertThat(matches).hasSize(1);
        return ((JsonString) matches.getFirst()).string();
    }
}

