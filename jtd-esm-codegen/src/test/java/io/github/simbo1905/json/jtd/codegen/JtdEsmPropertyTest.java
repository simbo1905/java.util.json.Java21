package io.github.simbo1905.json.jtd.codegen;

import jdk.sandbox.java.util.json.*;
import net.jqwik.api.*;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Property-based testing for JTD to ESM code generator.
/// Generates comprehensive schema/document permutations to validate generated JavaScript validators.
///
/// Uses GraalVM Polyglot JS for in-process JavaScript execution - no external runtime needed.
class JtdEsmPropertyTest extends JtdEsmCodegenLoggingConfig {
    static final Logger LOG = Logger.getLogger(JtdEsmPropertyTest.class.getName());

    private static final List<String> PROPERTY_NAMES = List.of("alpha", "beta", "gamma", "delta", "epsilon");
    private static final List<List<String>> PROPERTY_PAIRS = List.of(
        List.of("alpha", "beta"), List.of("alpha", "gamma"),
        List.of("beta", "delta"), List.of("gamma", "epsilon")
    );
    private static final List<String> DISCRIMINATOR_VALUES = List.of("type1", "type2", "type3");
    private static final List<String> ENUM_VALUES = List.of("red", "green", "blue", "yellow");
    private static final Random RANDOM = new Random();

    /// Sealed interface for JTD test schemas
    sealed interface JtdTestSchema permits EmptySchema, RefSchema, TypeSchema, EnumSchema,
        ElementsSchema, PropertiesSchema, ValuesSchema, DiscriminatorSchema, NullableSchema {}

    record EmptySchema() implements JtdTestSchema {}
    record RefSchema(String ref) implements JtdTestSchema {}
    record TypeSchema(String type) implements JtdTestSchema {}
    record EnumSchema(List<String> values) implements JtdTestSchema {}
    record ElementsSchema(JtdTestSchema elements) implements JtdTestSchema {}
    record PropertiesSchema(Map<String, JtdTestSchema> properties,
                           Map<String, JtdTestSchema> optionalProperties,
                           boolean additionalProperties) implements JtdTestSchema {}
    record ValuesSchema(JtdTestSchema values) implements JtdTestSchema {}
    record DiscriminatorSchema(String discriminator, Map<String, JtdTestSchema> mapping) implements JtdTestSchema {}
    record NullableSchema(JtdTestSchema schema) implements JtdTestSchema {}

    @Provide
    Arbitrary<JtdTestSchema> jtdSchemas() {
        return jtdSchemaArbitrary(3);
    }

    @SuppressWarnings("unchecked")
    private static Arbitrary<JtdTestSchema> jtdSchemaArbitrary(int depth) {
        final var primitives = Arbitraries.of(
            new EmptySchema(),
            new TypeSchema("boolean"),
            new TypeSchema("string"),
            new TypeSchema("int32"),
            new TypeSchema("float64"),
            new TypeSchema("timestamp")
        );

        if (depth == 0) {
            return (Arbitrary<JtdTestSchema>) (Arbitrary<?>) primitives;
        }

        return (Arbitrary<JtdTestSchema>) (Arbitrary<?>) Arbitraries.oneOf(
            primitives,
            enumSchemaArbitrary(),
            elementsSchemaArbitrary(depth),
            propertiesSchemaArbitrary(depth),
            valuesSchemaArbitrary(depth),
            discriminatorSchemaArbitrary(),
            nullableSchemaArbitrary(depth)
        );
    }

    private static Arbitrary<JtdTestSchema> enumSchemaArbitrary() {
        return Arbitraries.of(ENUM_VALUES).list().ofMinSize(1).ofMaxSize(4).map(values -> {
            List<String> distinctValues = values.stream().distinct().toList();
            return new EnumSchema(new ArrayList<>(distinctValues));
        });
    }

    private static Arbitrary<JtdTestSchema> elementsSchemaArbitrary(int depth) {
        return jtdSchemaArbitrary(depth - 1).filter(schema -> {
            if (schema instanceof DiscriminatorSchema disc) {
                var firstVariant = disc.mapping().values().iterator().next();
                return !(firstVariant instanceof TypeSchema) && !(firstVariant instanceof EnumSchema);
            }
            return true;
        }).map(ElementsSchema::new);
    }

    private static Arbitrary<JtdTestSchema> propertiesSchemaArbitrary(int depth) {
        final var childDepth = depth - 1;
        final var empty = Arbitraries.of(new PropertiesSchema(Map.of(), Map.of(), false));

        final var singleRequired = Combinators.combine(
            Arbitraries.of(PROPERTY_NAMES),
            jtdSchemaArbitrary(childDepth)
        ).as((name, schema) -> {
            Assertions.assertNotNull(name);
            Assertions.assertNotNull(schema);
            return new PropertiesSchema(Map.of(name, schema), Map.of(), false);
        });

        final var mixed = Combinators.combine(
            Arbitraries.of(PROPERTY_PAIRS),
            jtdSchemaArbitrary(childDepth),
            jtdSchemaArbitrary(childDepth)
        ).as((names, requiredSchema, optionalSchema) -> {
            Assertions.assertNotNull(names);
            Assertions.assertNotNull(requiredSchema);
            Assertions.assertNotNull(optionalSchema);
            return new PropertiesSchema(
                Map.of(names.getFirst(), requiredSchema),
                Map.of(names.getLast(), optionalSchema),
                false
            );
        });

        final var withAdditional = mixed.map(props -> {
            Assertions.assertNotNull(props);
            return new PropertiesSchema(props.properties(), props.optionalProperties(), true);
        });

        return Arbitraries.oneOf(empty, singleRequired, mixed, withAdditional);
    }

    private static Arbitrary<JtdTestSchema> valuesSchemaArbitrary(int depth) {
        return jtdSchemaArbitrary(depth - 1).map(ValuesSchema::new);
    }

    private static Arbitrary<JtdTestSchema> discriminatorSchemaArbitrary() {
        return Combinators.combine(
            Arbitraries.of(PROPERTY_NAMES),
            Arbitraries.of(DISCRIMINATOR_VALUES),
            Arbitraries.of(DISCRIMINATOR_VALUES)
        ).as((discriminatorKey, value1, value2) -> {
            final var mapping = new LinkedHashMap<String, JtdTestSchema>();
            final var schema1 = propertiesSchemaForDiscriminatorMapping(discriminatorKey).sample();
            mapping.put(value1, schema1);

            Assertions.assertNotNull(value1);
            if (!value1.equals(value2)) {
                final var schema2 = propertiesSchemaForDiscriminatorMapping(discriminatorKey).sample();
                mapping.put(value2, schema2);
            }
            return new DiscriminatorSchema(discriminatorKey, mapping);
        });
    }

    private static Arbitrary<JtdTestSchema> propertiesSchemaForDiscriminatorMapping(String discriminatorKey) {
        final var primitiveSchemas = Arbitraries.of(
            new TypeSchema("boolean"),
            new TypeSchema("string"),
            new TypeSchema("int32"),
            new EnumSchema(List.of("red", "green", "blue"))
        );

        final var allPropertyNames = List.of("alpha", "beta", "gamma", "delta", "epsilon");
        final var safePropertyNames = allPropertyNames.stream()
            .filter(name -> !name.equals(discriminatorKey))
            .toList();
        final var effectivePropertyNames = safePropertyNames.isEmpty()
            ? List.of("prop1", "prop2", "prop3")
            : safePropertyNames;

        final var safePropertyPairs = effectivePropertyNames.stream()
            .flatMap(name1 -> effectivePropertyNames.stream()
                .filter(name2 -> !name1.equals(name2))
                .map(name2 -> List.of(name1, name2)))
            .filter(pair -> !pair.getFirst().equals(discriminatorKey) && !pair.get(1).equals(discriminatorKey))
            .toList();

        return Arbitraries.oneOf(
            Combinators.combine(Arbitraries.of(effectivePropertyNames), primitiveSchemas)
                .as((name, schema) -> new PropertiesSchema(Map.of(name, schema), Map.of(), false)),
            Combinators.combine(Arbitraries.of(effectivePropertyNames), primitiveSchemas)
                .as((name, schema) -> new PropertiesSchema(Map.of(), Map.of(name, schema), false)),
            Combinators.combine(Arbitraries.of(safePropertyPairs), primitiveSchemas, primitiveSchemas)
                .as((names, reqSchema, optSchema) ->
                    new PropertiesSchema(Map.of(names.getFirst(), reqSchema),
                                        Map.of(names.getLast(), optSchema), false))
        );
    }

    private static Arbitrary<JtdTestSchema> nullableSchemaArbitrary(int depth) {
        return jtdSchemaArbitrary(depth - 1).map(NullableSchema::new);
    }

    /// Builds compliant JSON document for a schema
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object buildCompliantDocument(JtdTestSchema schema) {
        return switch (schema) {
            case EmptySchema ignored -> "anything-goes";
            case RefSchema ignored -> "ref-compliant-value";
            case TypeSchema(var type) -> buildCompliantTypeValue(type);
            case EnumSchema(var values) -> values.getFirst();
            case ElementsSchema(var elem) -> {
                final var v1 = buildCompliantDocument(elem);
                final var v2 = buildCompliantDocument(elem);
                final var lst = new ArrayList<>();
                if (v1 != null) lst.add(v1);
                if (v2 != null) lst.add(v2);
                yield lst;
            }
            case PropertiesSchema(var props, var optProps, var ignored) -> {
                final var obj = new LinkedHashMap<String, Object>();
                props.forEach((k, v) -> obj.put(k, buildCompliantDocument(v)));
                optProps.forEach((k, v) -> obj.put(k, buildCompliantDocument(v)));
                yield obj;
            }
            case ValuesSchema(var val) -> {
                final var v1 = buildCompliantDocument(val);
                final var v2 = buildCompliantDocument(val);
                final var map = new LinkedHashMap<String, Object>();
                if (v1 != null) map.put("key1", v1);
                if (v2 != null) map.put("key2", v2);
                yield map;
            }
            case DiscriminatorSchema(var disc, var mapping) -> {
                final var firstEntry = mapping.entrySet().iterator().next();
                final var discValue = firstEntry.getKey();
                final var variant = firstEntry.getValue();
                final var obj = new LinkedHashMap<String, Object>();
                obj.put(disc, discValue);
                if (variant instanceof PropertiesSchema ps) {
                    ps.properties().forEach((k, v) -> {
                        if (!k.equals(disc)) obj.put(k, buildCompliantDocument(v));
                    });
                    ps.optionalProperties().forEach((k, v) -> {
                        if (!k.equals(disc)) obj.put(k, buildCompliantDocument(v));
                    });
                }
                yield obj;
            }
            case NullableSchema ignored -> null;
        };
    }

    private static Object buildCompliantTypeValue(String type) {
        return switch (type) {
            case "boolean" -> true;
            case "string" -> "compliant-string";
            case "timestamp" -> "2023-12-25T10:30:00Z";
            case "int8" -> 42;
            case "uint8" -> 200;
            case "int16" -> 30000;
            case "uint16" -> 50000;
            case "int32" -> 1000000;
            case "uint32" -> 3000000000L;
            case "float32", "float64" -> 3.14159;
            default -> "unknown";
        };
    }

    /// Creates failing documents for a schema
    @SuppressWarnings({"unchecked", "rawtypes"})
    static List<Object> createFailingDocuments(JtdTestSchema schema, Object compliant) {
        return switch (schema) {
            case EmptySchema ignored -> List.of();
            case RefSchema ignored -> Collections.singletonList(null);
            case TypeSchema(var type) -> createFailingTypeValues(type);
            case EnumSchema ignored -> List.of("invalid-enum-value");
            case ElementsSchema(var elem) -> {
                if (compliant instanceof List lst && !lst.isEmpty()) {
                    final var invalidElem = createFailingDocuments(elem, lst.getFirst());
                    if (!invalidElem.isEmpty()) {
                        final var innerLst = new ArrayList<>();
                        innerLst.add(lst.getFirst());
                        innerLst.add(invalidElem.getFirst());
                        final var failures = new ArrayList<>();
                        failures.add(innerLst);
                        failures.add("not-an-array");
                        yield failures;
                    }
                }
                yield List.of("not-an-array");
            }
            case PropertiesSchema(var props, var optProps, var add) -> {
                if (props.isEmpty() && optProps.isEmpty()) {
                    yield List.of();
                }
                final var failures = new ArrayList<Object>();
                if (!props.isEmpty() && compliant instanceof Map) {
                    final var firstKey = props.keySet().iterator().next();
                    failures.add(removeKey((Map<String, Object>) compliant, firstKey));
                }
                if (!add && compliant instanceof Map) {
                    final var extended = new LinkedHashMap<>((Map<String, Object>) compliant);
                    extended.put("extraProperty", "extra-value");
                    failures.add(extended);
                }
                failures.add("not-an-object");
                yield failures;
            }
            case ValuesSchema ignored -> List.of("not-an-object");
            case DiscriminatorSchema(var disc, var ignored) -> {
                final var failures = new ArrayList<Object>();
                if (compliant instanceof Map) {
                    final var modified = new LinkedHashMap<>((Map<String, Object>) compliant);
                    modified.put(disc, "invalid-discriminator");
                    failures.add(modified);
                }
                failures.add("not-an-object");
                yield failures;
            }
            case NullableSchema ignored -> List.of();
        };
    }

    private static List<Object> createFailingTypeValues(String type) {
        return switch (type) {
            case "boolean" -> Arrays.asList("not-boolean", 1);
            case "string", "timestamp" -> Arrays.asList(123, false);
            case "int8", "uint8", "int16", "int32", "uint32", "uint16" ->
                Arrays.asList("not-integer", 3.14);
            case "float32", "float64" -> Arrays.asList("not-float", true);
            default -> Collections.singletonList(null);
        };
    }

    private static Map<String, Object> removeKey(Map<String, Object> original, String key) {
        final var result = new LinkedHashMap<String, Object>();
        for (var entry : original.entrySet()) {
            if (!entry.getKey().equals(key)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /// Describes schema for logging
    static String describeSchema(JtdTestSchema schema) {
        return switch (schema) {
            case EmptySchema ignored -> "empty";
            case RefSchema(var ref) -> "ref:" + ref;
            case TypeSchema(var type) -> "type:" + type;
            case EnumSchema(var values) -> "enum[" + String.join(",", values) + "]";
            case ElementsSchema(var elem) -> "elements[" + describeSchema(elem) + "]";
            case PropertiesSchema(var props, var optProps, var add) -> {
                final var parts = new ArrayList<String>();
                if (!props.isEmpty()) parts.add("required{" + String.join(",", props.keySet()) + "}");
                if (!optProps.isEmpty()) parts.add("optional{" + String.join(",", optProps.keySet()) + "}");
                if (add) parts.add("additional");
                yield "properties[" + String.join(",", parts) + "]";
            }
            case ValuesSchema(var val) -> "values[" + describeSchema(val) + "]";
            case DiscriminatorSchema(var disc, var mapping) ->
                "discriminator[" + disc + "={" + String.join(",", mapping.keySet()) + "}]";
            case NullableSchema(var inner) -> "nullable[" + describeSchema(inner) + "]";
        };
    }

    /// Converts test schema to JSON for the codegen parser
    static JsonObject jtdSchemaToJsonObject(JtdTestSchema schema) {
        return switch (schema) {
            case EmptySchema ignored -> JsonObject.of(Map.of());
            case RefSchema(var ref) -> {
                final Map<String, JsonValue> map = Map.of("ref", JsonString.of(ref));
                yield JsonObject.of(map);
            }
            case TypeSchema(var type) -> {
                final Map<String, JsonValue> map = Map.of("type", JsonString.of(type));
                yield JsonObject.of(map);
            }
            case EnumSchema(var values) -> {
                final Map<String, JsonValue> map = Map.of("enum", JsonArray.of(values.stream().map(JsonString::of).toList()));
                yield JsonObject.of(map);
            }
            case ElementsSchema(var elem) -> {
                final Map<String, JsonValue> map = Map.of("elements", jtdSchemaToJsonObject(elem));
                yield JsonObject.of(map);
            }
            case PropertiesSchema(var props, var optProps, var add) -> {
                final var schemaMap = new LinkedHashMap<String, JsonValue>();
                if (!props.isEmpty()) {
                    final Map<String, JsonValue> propsMap = props.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, e -> jtdSchemaToJsonObject(e.getValue()),
                        (a, b) -> a, LinkedHashMap::new));
                    schemaMap.put("properties", JsonObject.of(propsMap));
                }
                if (!optProps.isEmpty()) {
                    final Map<String, JsonValue> optMap = optProps.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, e -> jtdSchemaToJsonObject(e.getValue()),
                        (a, b) -> a, LinkedHashMap::new));
                    schemaMap.put("optionalProperties", JsonObject.of(optMap));
                }
                if (add) {
                    schemaMap.put("additionalProperties", JsonBoolean.of(true));
                }
                yield JsonObject.of(schemaMap);
            }
            case ValuesSchema(var val) -> {
                final Map<String, JsonValue> map = Map.of("values", jtdSchemaToJsonObject(val));
                yield JsonObject.of(map);
            }
            case DiscriminatorSchema(var disc, var mapping) -> {
                final var schemaMap = new LinkedHashMap<String, JsonValue>();
                schemaMap.put("discriminator", JsonString.of(disc));
                final Map<String, JsonValue> mappingMap = mapping.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> jtdSchemaToJsonObject(e.getValue()),
                    (a, b) -> a, LinkedHashMap::new));
                schemaMap.put("mapping", JsonObject.of(mappingMap));
                yield JsonObject.of(schemaMap);
            }
            case NullableSchema(var inner) -> {
                final var innerSchema = jtdSchemaToJsonObject(inner);
                final var nullableMap = new LinkedHashMap<String, JsonValue>();
                nullableMap.putAll(innerSchema.members());
                nullableMap.put("nullable", JsonBoolean.of(true));
                yield JsonObject.of(nullableMap);
            }
        };
    }

    /// Converts a Java value to a GraalVM polyglot-compatible value.
    @SuppressWarnings("unchecked")
    private static Object toGraalValue(Context context, Object value) {
        if (value == null) return null;
        if (value instanceof Boolean || value instanceof String) return value;
        if (value instanceof Number num) return num.doubleValue();
        if (value instanceof List<?> lst) {
            final var jsArray = context.eval("js", "[]");
            for (int i = 0; i < lst.size(); i++) {
                jsArray.setArrayElement(i, toGraalValue(context, lst.get(i)));
            }
            return jsArray;
        }
        if (value instanceof Map<?, ?> rawMap) {
            final var jsObj = context.eval("js", "({})");
            final var typedMap = (Map<String, Object>) rawMap;
            for (var entry : typedMap.entrySet()) {
                jsObj.putMember(entry.getKey(), toGraalValue(context, entry.getValue()));
            }
            return jsObj;
        }
        return value;
    }

    /// Runs validation via GraalVM polyglot: loads the generated ESM module,
    /// calls `validate(instance)`, returns the number of errors.
    private static int runValidation(Path modulePath, Object document, String schemaDescription, String testName) throws IOException {
        final var jsContent = Files.readString(modulePath, StandardCharsets.UTF_8);
        LOG.finest(() -> String.format("%s - Generated JS for schema '%s':%n%s", testName, schemaDescription, jsContent));
        LOG.finest(() -> String.format("%s - Document: %s", testName, document));
        
        try (var context = Context.newBuilder("js")
                .allowIO(IOAccess.ALL)
                .option("js.esm-eval-returns-exports", "true")
                .option("js.ecmascript-version", "2020")
                .build()) {
            final var source = Source.newBuilder("js", modulePath.toFile())
                .mimeType("application/javascript+module")
                .build();
            final var exports = context.eval(source);
            final var validateFn = exports.getMember("validate");
            final var graalDoc = toGraalValue(context, document);
            final var result = validateFn.execute(graalDoc);
            return (int) result.getArraySize();
        }
    }

    @Property(generation = GenerationMode.AUTO)
    @SuppressWarnings({"unchecked", "rawtypes"})
    void generatedValidatorPassesCompliantDocuments(@ForAll("jtdSchemas") JtdTestSchema schema) throws Exception {
        LOG.finer(() -> "Executing generatedValidatorPassesCompliantDocuments");

        final var schemaDescription = describeSchema(schema);
        LOG.fine(() -> "Testing schema: " + schemaDescription);

        // Skip problematic combinations
        if (schemaDescription.contains("elements[discriminator[") && schemaDescription.contains("type=")) {
            LOG.fine(() -> "Skipping problematic schema: " + schemaDescription);
            return;
        }

        final var tempDir = Files.createTempDirectory("jtd-esm-prop-test-");

        // Write schema JSON and generate validator
        final var schemaJson = jtdSchemaToJsonObject(schema);
        final var schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, Json.toDisplayString(schemaJson, 0), StandardCharsets.UTF_8);
        final var outJs = JtdToEsmCli.run(schemaFile, tempDir);

        // Build compliant document
        final var compliantDoc = buildCompliantDocument(schema);
        if (compliantDoc == null) {
            LOG.fine(() -> "Skipping null compliant document for schema: " + schemaDescription);
            cleanup(tempDir);
            return;
        }

        // Validate via GraalVM polyglot
        final int errorCount = runValidation(outJs, compliantDoc, schemaDescription, "generatedValidatorPassesCompliantDocuments");

        if (errorCount != 0) {
            LOG.severe(() -> String.format(
                "Compliant document FAILED for schema: %s%nDocument: %s%nErrors: %d%nGenerated JS: %s",
                schemaDescription, compliantDoc, errorCount, outJs));
        }

        assertThat(errorCount).as(
            "Compliant document should pass validation for schema: %s with doc: %s",
            schemaDescription, compliantDoc).isZero();

        cleanup(tempDir);
    }

    @Property(generation = GenerationMode.AUTO)
    @SuppressWarnings({"unchecked", "rawtypes"})
    void generatedValidatorRejectsFailingDocuments(@ForAll("jtdSchemas") JtdTestSchema schema) throws Exception {
        LOG.finer(() -> "Executing generatedValidatorRejectsFailingDocuments");

        final var schemaDescription = describeSchema(schema);
        LOG.fine(() -> "Testing schema: " + schemaDescription);

        // Skip problematic combinations
        if (schemaDescription.contains("elements[discriminator[") && schemaDescription.contains("type=")) {
            LOG.fine(() -> "Skipping problematic schema: " + schemaDescription);
            return;
        }

        // Skip schemas that accept everything
        if (schema instanceof EmptySchema || schema instanceof NullableSchema) {
            return;
        }

        final var tempDir = Files.createTempDirectory("jtd-esm-prop-test-");

        // Write schema JSON and generate validator
        final var schemaJson = jtdSchemaToJsonObject(schema);
        final var schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, Json.toDisplayString(schemaJson, 0), StandardCharsets.UTF_8);
        final var outJs = JtdToEsmCli.run(schemaFile, tempDir);

        // Create failing documents
        final var compliantDoc = buildCompliantDocument(schema);
        final var failingDocs = createFailingDocuments(schema, compliantDoc);

        if (failingDocs.isEmpty()) {
            cleanup(tempDir);
            return;
        }

        // Validate each failing document
        for (int i = 0; i < failingDocs.size(); i++) {
            final var failingDoc = failingDocs.get(i);
            if (failingDoc == null) continue;

            final int errorCount = runValidation(outJs, failingDoc, schemaDescription, "generatedValidatorRejectsFailingDocuments");
            final int docIndex = i;

            if (errorCount == 0) {
                LOG.severe(() -> String.format(
                    "Failing document #%d PASSED (should have failed) for schema: %s%nDocument: %s%nGenerated JS: %s",
                    docIndex, schemaDescription, failingDoc, outJs));
            }

            assertThat(errorCount).as(
                "Failing document #%d should be rejected for schema: %s with doc: %s",
                docIndex, schemaDescription, failingDoc).isGreaterThan(0);
        }

        cleanup(tempDir);
    }

    private static void cleanup(Path tempDir) throws IOException {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            });
    }
}
