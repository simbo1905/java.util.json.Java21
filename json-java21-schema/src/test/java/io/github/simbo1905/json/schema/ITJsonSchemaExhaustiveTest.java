package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonBoolean;
import jdk.sandbox.java.util.json.JsonNull;
import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ITJsonSchemaExhaustiveTest extends JsonSchemaLoggingConfig {

    private static final Logger LOGGER = Logger.getLogger(io.github.simbo1905.json.schema.JsonSchema.class.getName());
    private static final int MAX_DEPTH = 3;
    private static final List<String> PROPERTY_NAMES = List.of("alpha", "beta", "gamma", "delta");
    private static final List<List<String>> PROPERTY_PAIRS = List.of(
        List.of("alpha", "beta"),
        List.of("alpha", "gamma"),
        List.of("alpha", "delta"),
        List.of("beta", "gamma"),
        List.of("beta", "delta"),
        List.of("gamma", "delta")
    );

    @Property(generation = GenerationMode.EXHAUSTIVE)
    void exhaustiveRoundTrip(@ForAll("schemas") JsonSchema schema) {
        LOGGER.info(() -> "Executing exhaustiveRoundTrip property test");

        final var schemaDescription = describeSchema(schema);
        LOGGER.fine(() -> "Schema descriptor: " + schemaDescription);

        final var schemaJson = schemaToJsonObject(schema);
        LOGGER.finer(() -> "Schema JSON: " + schemaJson);

        final var compiled = io.github.simbo1905.json.schema.JsonSchema.compile(schemaJson);

        final var compliantDocument = buildCompliantDocument(schema);
        LOGGER.finer(() -> "Compliant document: " + compliantDocument);

        final var validation = compiled.validate(compliantDocument);
        assertThat(validation.valid())
            .as("Compliant document should validate for schema %s", schemaDescription)
            .isTrue();
        assertThat(validation.errors())
            .as("No validation errors expected for compliant document")
            .isEmpty();

        final var failingDocuments = failingDocuments(schema, compliantDocument);
        assertThat(failingDocuments)
            .as("Negative cases should be generated for schema %s", schemaDescription)
            .isNotEmpty();

        final var failingDocumentStrings = failingDocuments.stream()
            .map(Object::toString)
            .toList();
        LOGGER.finest(() -> "Failing documents: " + failingDocumentStrings);

        failingDocuments.forEach(failing -> {
            final var failingResult = compiled.validate(failing);
            assertThat(failingResult.valid())
                .as("Expected validation failure for %s against schema %s", failing, schemaDescription)
                .isFalse();
            assertThat(failingResult.errors())
                .as("Expected validation errors for %s against schema %s", failing, schemaDescription)
                .isNotEmpty();
        });
    }

    private static JsonValue buildCompliantDocument(JsonSchema schema) {
        return switch (schema) {
            case JsonSchema.ObjectSchema(var properties) -> JsonObject.of(properties.stream()
                .collect(Collectors.<JsonSchema.Property, String, JsonValue, LinkedHashMap<String, JsonValue>>toMap(
                    JsonSchema.Property::name,
                    property -> buildCompliantDocument(property.schema()),
                    (left, right) -> left,
                    LinkedHashMap::new
                )));
            case JsonSchema.ArraySchema(var items) -> JsonArray.of(items.stream()
                .map(ITJsonSchemaExhaustiveTest::buildCompliantDocument)
                .toList());
            case JsonSchema.StringSchema ignored -> JsonString.of("valid");
            case JsonSchema.NumberSchema ignored -> JsonNumber.of(BigDecimal.ONE);
            case JsonSchema.BooleanSchema ignored -> JsonBoolean.of(true);
            case JsonSchema.NullSchema ignored -> JsonNull.of();
        };
    }

    private static List<JsonValue> failingDocuments(JsonSchema schema, JsonValue compliant) {
        return switch (schema) {
            case JsonSchema.ObjectSchema(var properties) -> properties.isEmpty()
                ? List.<JsonValue>of(JsonNull.of())
                : properties.stream()
                    .map(JsonSchema.Property::name)
                    .map(name -> removeProperty((JsonObject) compliant, name))
                    .map(json -> (JsonValue) json)
                    .toList();
            case JsonSchema.ArraySchema(var items) -> {
                final var values = ((JsonArray) compliant).values();
                if (values.isEmpty()) {
                    yield List.<JsonValue>of(JsonNull.of());
                }
                final var truncated = JsonArray.of(values.stream().limit(values.size() - 1L).toList());
                yield List.<JsonValue>of(truncated);
            }
            case JsonSchema.StringSchema ignored -> List.<JsonValue>of(JsonNumber.of(BigDecimal.TWO));
            case JsonSchema.NumberSchema ignored -> List.<JsonValue>of(JsonString.of("not-a-number"));
            case JsonSchema.BooleanSchema ignored -> List.<JsonValue>of(JsonNull.of());
            case JsonSchema.NullSchema ignored -> List.<JsonValue>of(JsonBoolean.of(true));
        };
    }

    private static JsonObject removeProperty(JsonObject original, String missingProperty) {
        final var filtered = original.members().entrySet().stream()
            .filter(entry -> !Objects.equals(entry.getKey(), missingProperty))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        return JsonObject.of(filtered);
    }

    private static JsonObject schemaToJsonObject(JsonSchema schema) {
        return switch (schema) {
            case JsonSchema.ObjectSchema(var properties) -> {
                final var schemaMap = new LinkedHashMap<String, JsonValue>();
                schemaMap.put("type", JsonString.of("object"));
                final var propertyMap = properties.isEmpty()
                    ? JsonObject.of(Map.<String, JsonValue>of())
                    : JsonObject.of(properties.stream()
                        .collect(Collectors.<JsonSchema.Property, String, JsonValue, LinkedHashMap<String, JsonValue>>toMap(
                            JsonSchema.Property::name,
                            property -> (JsonValue) schemaToJsonObject(property.schema()),
                            (left, right) -> left,
                            LinkedHashMap::new
                        )));
                schemaMap.put("properties", propertyMap);
                final var requiredValues = properties.stream()
                    .map(JsonSchema.Property::name)
                    .map(JsonString::of)
                    .map(value -> (JsonValue) value)
                    .toList();
                schemaMap.put("required", JsonArray.of(requiredValues));
                schemaMap.put("additionalProperties", JsonBoolean.of(false));
                yield JsonObject.of(schemaMap);
            }
            case JsonSchema.ArraySchema(var items) -> {
                final var schemaMap = new LinkedHashMap<String, JsonValue>();
                schemaMap.put("type", JsonString.of("array"));
                final var prefixItems = items.stream()
                    .map(ITJsonSchemaExhaustiveTest::schemaToJsonObject)
                    .map(value -> (JsonValue) value)
                    .toList();
                schemaMap.put("prefixItems", JsonArray.of(prefixItems));
                schemaMap.put("items", JsonBoolean.of(false));
                schemaMap.put("minItems", JsonNumber.of((long) items.size()));
                schemaMap.put("maxItems", JsonNumber.of((long) items.size()));
                yield JsonObject.of(schemaMap);
            }
            case JsonSchema.StringSchema ignored -> primitiveSchema("string");
            case JsonSchema.NumberSchema ignored -> primitiveSchema("number");
            case JsonSchema.BooleanSchema ignored -> primitiveSchema("boolean");
            case JsonSchema.NullSchema ignored -> primitiveSchema("null");
        };
    }

    private static JsonObject primitiveSchema(String type) {
        final var schemaMap = new LinkedHashMap<String, JsonValue>();
        schemaMap.put("type", JsonString.of(type));
        return JsonObject.of(schemaMap);
    }

    private static String describeSchema(JsonSchema schema) {
        return switch (schema) {
            case JsonSchema.ObjectSchema(var properties) -> properties.stream()
                .map(property -> property.name() + ":" + describeSchema(property.schema()))
                .collect(Collectors.joining(",", "object{", "}"));
            case JsonSchema.ArraySchema(var items) -> items.stream()
                .map(ITJsonSchemaExhaustiveTest::describeSchema)
                .collect(Collectors.joining(",", "array[", "]"));
            case JsonSchema.StringSchema ignored -> "string";
            case JsonSchema.NumberSchema ignored -> "number";
            case JsonSchema.BooleanSchema ignored -> "boolean";
            case JsonSchema.NullSchema ignored -> "null";
        };
    }

    @Provide
    Arbitrary<JsonSchema> schemas() {
        return schemaArbitrary(MAX_DEPTH);
    }

    private static Arbitrary<JsonSchema> schemaArbitrary(int depth) {
        final var primitives = Arbitraries.of(
            new JsonSchema.StringSchema(),
            new JsonSchema.NumberSchema(),
            new JsonSchema.BooleanSchema(),
            new JsonSchema.NullSchema()
        );
        if (depth == 0) {
            return primitives.map(schema -> (JsonSchema) schema);
        }
        return Arbitraries.<JsonSchema>oneOf(
            primitives,
            objectSchemaArbitrary(depth),
            arraySchemaArbitrary(depth)
        );
    }

    private static Arbitrary<JsonSchema> objectSchemaArbitrary(int depth) {
        if (depth == 1) {
            return Arbitraries.of(new JsonSchema.ObjectSchema(List.of()));
        }
        final var childDepth = depth - 1;
        final var empty = Arbitraries.of(new JsonSchema.ObjectSchema(List.of()));
        final var single = Combinators.combine(
            Arbitraries.of(PROPERTY_NAMES),
            schemaArbitrary(childDepth)
        ).as((name, child) -> new JsonSchema.ObjectSchema(List.of(new JsonSchema.Property(name, child))));
        final var pair = Combinators.combine(
            Arbitraries.of(PROPERTY_PAIRS),
            schemaArbitrary(childDepth),
            schemaArbitrary(childDepth)
        ).as((names, first, second) -> new JsonSchema.ObjectSchema(List.of(
            new JsonSchema.Property(names.getFirst(), first),
            new JsonSchema.Property(names.getLast(), second)
        )));
        return Arbitraries.oneOf(empty, single, pair);
    }

    private static Arbitrary<JsonSchema> arraySchemaArbitrary(int depth) {
        if (depth == 1) {
            return Arbitraries.of(new JsonSchema.ArraySchema(List.of()));
        }
        final var childDepth = depth - 1;
        final var empty = Arbitraries.of(new JsonSchema.ArraySchema(List.of()));
        final var single = schemaArbitrary(childDepth)
            .map(child -> new JsonSchema.ArraySchema(List.of(child)));
        final var pair = Combinators.combine(
            schemaArbitrary(childDepth),
            schemaArbitrary(childDepth)
        ).as((first, second) -> new JsonSchema.ArraySchema(List.of(first, second)));
        return Arbitraries.oneOf(empty, single, pair);
    }

    sealed interface JsonSchema permits JsonSchema.ObjectSchema, JsonSchema.ArraySchema, JsonSchema.StringSchema, JsonSchema.NumberSchema, JsonSchema.BooleanSchema, JsonSchema.NullSchema {
        record ObjectSchema(List<Property> properties) implements JsonSchema {
            public ObjectSchema {
                properties = List.copyOf(properties);
            }
        }

        record Property(String name, JsonSchema schema) {
            public Property {
                name = Objects.requireNonNull(name);
                schema = Objects.requireNonNull(schema);
            }
        }

        record ArraySchema(List<JsonSchema> items) implements JsonSchema {
            public ArraySchema {
                items = List.copyOf(items);
            }
        }

        record StringSchema() implements JsonSchema {}

        record NumberSchema() implements JsonSchema {}

        record BooleanSchema() implements JsonSchema {}

        record NullSchema() implements JsonSchema {}
    }
}
