
package jdk.sandbox.internal.util.json;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonRecordMappingTests {

    // 1. Define the Domain Model using a Sealed Interface and Records
    public sealed interface Ecommerce permits Customer, Product, LineItem, Order {
    }

    public record Customer(String name, String email) implements Ecommerce {
    }

    public record Product(String sku, String name, double price) implements Ecommerce {
    }

    public record LineItem(Product product, int quantity) implements Ecommerce {
    }

    public record Order(String orderId, Customer customer, List<LineItem> items) implements Ecommerce {
    }

    // 2. Implement Record-to-JSON Mapping
    private JsonValue toJon(Ecommerce domainObject) {
        return switch (domainObject) {
            case Order o -> {
                Map<String, JsonValue> map = new LinkedHashMap<>();
                map.put("type", JsonString.of("order"));
                map.put("orderId", JsonString.of(o.orderId()));
                map.put("customer", toJon(o.customer()));
                map.put("items", JsonArray.of(o.items().stream().map(this::toJon).collect(Collectors.toList())));
                yield JsonObject.of(map);
            }
            case Customer c -> {
                Map<String, JsonValue> map = new LinkedHashMap<>();
                map.put("type", JsonString.of("customer"));
                map.put("name", JsonString.of(c.name()));
                map.put("email", JsonString.of(c.email()));
                yield JsonObject.of(map);
            }
            case LineItem li -> {
                Map<String, JsonValue> map = new LinkedHashMap<>();
                map.put("type", JsonString.of("lineItem"));
                map.put("product", toJon(li.product()));
                map.put("quantity", JsonNumber.of(li.quantity()));
                yield JsonObject.of(map);
            }
            case Product p -> {
                Map<String, JsonValue> map = new LinkedHashMap<>();
                map.put("type", JsonString.of("product"));
                map.put("sku", JsonString.of(p.sku()));
                map.put("name", JsonString.of(p.name()));
                map.put("price", JsonNumber.of(p.price()));
                yield JsonObject.of(map);
            }
        };
    }

    // 3. Implement JSON-to-Record Mapping
    private Ecommerce toDomain(JsonValue jsonValue) {
        if (!(jsonValue instanceof JsonObject jsonObject)) {
            throw new IllegalArgumentException("Expected a JsonObject");
        }

        Map<String, JsonValue> members = jsonObject.members();
        String type = ((JsonString) members.get("type")).value();

        return switch (type) {
            case "order" -> {
                String orderId = ((JsonString) members.get("orderId")).value();
                Customer customer = (Customer) toDomain(members.get("customer"));
                List<LineItem> items = ((JsonArray) members.get("items")).values().stream()
                        .map(item -> (LineItem) toDomain(item))
                        .collect(Collectors.toList());
                yield new Order(orderId, customer, items);
            }
            case "customer" -> {
                String name = ((JsonString) members.get("name")).value();
                String email = ((JsonString) members.get("email")).value();
                yield new Customer(name, email);
            }
            case "lineItem" -> {
                Product product = (Product) toDomain(members.get("product"));
                int quantity = ((JsonNumber) members.get("quantity")).toNumber().intValue();
                yield new LineItem(product, quantity);
            }
            case "product" -> {
                String sku = ((JsonString) members.get("sku")).value();
                String name = ((JsonString) members.get("name")).value();
                double price = ((JsonNumber) members.get("price")).toNumber().doubleValue();
                yield new Product(sku, name, price);
            }
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }


    @Test
    void testEcommerceDagMapping() {
        // Step 1: Create the object graph
        var customer = new Customer("John Doe", "john.doe@example.com");
        var product1 = new Product("SKU-001", "Laptop", 1200.00);
        var product2 = new Product("SKU-002", "Mouse", 25.00);
        var order = new Order("ORD-12345", customer, Arrays.asList(
                new LineItem(product1, 1),
                new LineItem(product2, 2)
        ));

        // Step 2: Convert to JSON
        JsonObject jsonOrder = (JsonObject) toJon(order);
        String jsonString = jsonOrder.toString();

        // For demonstration, let's parse it back with the library's parser
        JsonParser parser = new JsonParser(jsonString.toCharArray());
        JsonObject parsedJsonOrder = (JsonObject) parser.parseRoot();

        // Step 3: Convert back to a record
        Order reconstructedOrder = (Order) toDomain(parsedJsonOrder);

        // Step 4: Assert equality
        assertThat(reconstructedOrder).isEqualTo(order);

        // You can also assert individual fields to be sure
        assertThat(reconstructedOrder.orderId()).isEqualTo("ORD-12345");
        assertThat(reconstructedOrder.customer().name()).isEqualTo("John Doe");
        assertThat(reconstructedOrder.items()).hasSize(2);
        assertThat(reconstructedOrder.items().get(0).product().name()).isEqualTo("Laptop");
        assertThat(reconstructedOrder.items().get(1).quantity()).isEqualTo(2);
    }
}
