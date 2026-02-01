package json.java21.jsonpath.test;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonValue;
import json.java21.jsonpath.JsonPath;

import java.util.List;

public class TestPublicAPI {
    /// Sample JSON from Goessner article
    private static final String STORE_JSON = """
        {
          "store": {
            "book": [
              {
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
              },
              {
                "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
              },
              {
                "category": "fiction",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "ISBN": "0-553-21311-3",
                "price": 8.99
              },
              {
                "category": "fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "ISBN": "0-395-19395-8",
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

    public static void main(String[] args) {
        final JsonValue doc = Json.parse(STORE_JSON);
        final JsonPath path = JsonPath.parse("$.store.book[*].title");

        final List<String> matches = path.query(doc).stream().map(Object::toString).toList();
        if( matches.size()!=4){
            throw new AssertionError("Expected 4 books, got " + matches.size());
        }

        final var raw = JsonPath.parse("$.store.book").query(doc);
        if( raw instanceof JsonArray array && array.elements().size() != 4) {
            throw new AssertionError("Expected 4 books, got " + raw.size());
        }
    }

}
