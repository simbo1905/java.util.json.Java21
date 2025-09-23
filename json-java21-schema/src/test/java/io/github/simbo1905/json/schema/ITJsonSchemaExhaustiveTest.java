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
import net.jqwik.api.UseArbitraryProviders;
import net.jqwik.api.providers.ArbitraryProvider;
import net.jqwik.api.providers.SubtypeProvider;
import net.jqwik.api.providers.TypeUsage;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@UseArbitraryProviders(ITJsonSchemaExhaustiveTest.SchemaArbitraryProvider.class)
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
    void exhaustiveRoundTrip(@ForAll ITJsonSchemaExhaustiveTest.JsonSchema schema) {
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
            case ObjectSchema(var properties) -> JsonObject.of(properties.stream()
                .collect(Collectors.toMap(
                    JsonSchema.Property::name,
                    property -> buildCompliantDocument(property.schema()),
                    (left, right) -> left,
                    LinkedHashMap::new
                )));
            case ArraySchema(var items) -> JsonArray.of(items.stream()
                .map(ITJsonSchemaExhaustiveTest::buildCompliantDocument)
                .toList());
            case StringSchema ignored -> JsonString.of("valid");
            case NumberSchema ignored -> JsonNumber.of(BigDecimal.ONE);
            case BooleanSchema ignored -> JsonBoolean.of(true);
            case NullSchema ignored -> JsonNull.of();
        };
    }

    private static List<JsonValue> failingDocuments(JsonSchema schema, JsonValue compliant) {
        return switch (schema) {
            case ObjectSchema(var properties) -> properties.isEmpty()
                ? List.<JsonValue>of(JsonNull.of())
                : properties.stream()
                    .map(JsonSchema.Property::name)
                    .map(name -> removeProperty((JsonObject) compliant, name))
                    .map(json -> (JsonValue) json)
                    .toList();
            case ArraySchema(var items) -> {
                final var values = ((JsonArray) compliant).values();
                if (values.isEmpty()) {
                    yield List.<JsonValue>of(JsonNull.of());
                }
                final var truncated = JsonArray.of(values.stream().limit(values.size() - 1L).toList());
                yield List.<JsonValue>of(truncated);
            }
            case StringSchema ignored -> List.<JsonValue>of(JsonNumber.of(BigDecimal.TWO));
            case NumberSchema ignored -> List.<JsonValue>of(JsonString.of("not-a-number"));
            case BooleanSchema ignored -> List.<JsonValue>of(JsonNull.of());
            case NullSchema ignored -> List.<JsonValue>of(JsonBoolean.of(true));
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
            case ObjectSchema(var properties) -> {
                final var schemaMap = new LinkedHashMap<String, JsonValue>();
                schemaMap.put("type", JsonString.of("object"));
                final var propertyMap = properties.isEmpty()
                    ? JsonObject.of(Map.<String, JsonValue>of())
                    : JsonObject.of(properties.stream()
                        .collect(Collectors.toMap(
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
            case ArraySchema(var items) -> {
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
            case StringSchema ignored -> primitiveSchema("string");
            case NumberSchema ignored -> primitiveSchema("number");
            case BooleanSchema ignored -> primitiveSchema("boolean");
            case NullSchema ignored -> primitiveSchema("null");
        };
    }

    private static JsonObject primitiveSchema(String type) {
        final var schemaMap = new LinkedHashMap<String, JsonValue>();
        schemaMap.put("type", JsonString.of(type));
        return JsonObject.of(schemaMap);
    }

    private static String describeSchema(JsonSchema schema) {
        return switch (schema) {
            case ObjectSchema(var properties) -> properties.stream()
                .map(property -> property.name() + ":" + describeSchema(property.schema()))
                .collect(Collectors.joining(",", "object{", "}"));
            case ArraySchema(var items) -> items.stream()
                .map(ITJsonSchemaExhaustiveTest::describeSchema)
                .collect(Collectors.joining(",", "array[", "]"));
            case StringSchema ignored -> "string";
            case NumberSchema ignored -> "number";
            case BooleanSchema ignored -> "boolean";
            case NullSchema ignored -> "null";
        };
    }

    static final class SchemaArbitraryProvider implements ArbitraryProvider {
        @Override
        public boolean canProvideFor(TypeUsage targetType) {
            return targetType.isOfType(ITJsonSchemaExhaustiveTest.JsonSchema.class);
        }

        @Override
        public Set<Arbitrary<?>> provideFor(TypeUsage targetType, SubtypeProvider subtypeProvider) {
            return Set.of(schemaArbitrary(MAX_DEPTH));
        }
    }

    private static Arbitrary<JsonSchema> schemaArbitrary(int depth) {
        final var primitives = Arbitraries.of(
            new StringSchema(),
            new NumberSchema(),
            new BooleanSchema(),
            new NullSchema()
        );
        if (depth == 0) {
            return primitives;
        }
        return Arbitraries.oneOf(
            primitives,
            objectSchemaArbitrary(depth),
            arraySchemaArbitrary(depth)
        );
    }

    private static Arbitrary<JsonSchema> objectSchemaArbitrary(int depth) {
        if (depth == 1) {
            return Arbitraries.of(new ObjectSchema(List.of()));
        }
        final var childDepth = depth - 1;
        final var empty = Arbitraries.of(new ObjectSchema(List.of()));
        final var single = Combinators.combine(
            Arbitraries.of(PROPERTY_NAMES),
            schemaArbitrary(childDepth)
        ).as((name, child) -> new ObjectSchema(List.of(new Property(name, child))));
        final var pair = Combinators.combine(
            Arbitraries.of(PROPERTY_PAIRS),
            schemaArbitrary(childDepth),
            schemaArbitrary(childDepth)
        ).as((names, first, second) -> new ObjectSchema(List.of(
            new Property(names.getFirst(), first),
            new Property(names.getLast(), second)
        )));
        return Arbitraries.oneOf(empty, single, pair);
    }

    private static Arbitrary<JsonSchema> arraySchemaArbitrary(int depth) {
        if (depth == 1) {
            return Arbitraries.of(new ArraySchema(List.of()));
        }
        final var childDepth = depth - 1;
        final var empty = Arbitraries.of(new ArraySchema(List.of()));
        final var single = schemaArbitrary(childDepth)
            .map(child -> new ArraySchema(List.of(child)));
        final var pair = Combinators.combine(
            schemaArbitrary(childDepth),
            schemaArbitrary(childDepth)
        ).as((first, second) -> new ArraySchema(List.of(first, second)));
        return Arbitraries.oneOf(empty, single, pair);
    }

    sealed interface JsonSchema permits ObjectSchema, ArraySchema, StringSchema, NumberSchema, BooleanSchema, NullSchema {
        record ObjectSchema(List<Property> properties) implements JsonSchema {
            ObjectSchema {
                properties = List.copyOf(properties);
            }
        }

        record Property(String name, JsonSchema schema) {
            Property {
                name = Objects.requireNonNull(name);
                schema = Objects.requireNonNull(schema);
            }
        }

        record ArraySchema(List<JsonSchema> items) implements JsonSchema {
            ArraySchema {
                items = List.copyOf(items);
            }
        }

        record StringSchema() implements JsonSchema {}

        record NumberSchema() implements JsonSchema {}

        record BooleanSchema() implements JsonSchema {}

        record NullSchema() implements JsonSchema {}
    }
}
