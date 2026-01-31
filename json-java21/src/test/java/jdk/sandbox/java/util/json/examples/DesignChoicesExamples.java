package jdk.sandbox.java.util.json.examples;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonAssertionException;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonBoolean;
import jdk.sandbox.java.util.json.JsonNumber;
import jdk.sandbox.java.util.json.JsonNull;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Standalone examples demonstrating numeric design choices.
 *
 * <p>
 * This file mirrors the examples in {@code DESIGN_CHOICES.md}.
 */
public final class DesignChoicesExamples {

    public static void main(String[] args) {
        System.out.println("=== Numeric design choices examples ===\n");

        parseToBigDecimalLossless();
        parseToBigIntegerExact();
        bigDecimalToJsonNumberChooseTextPolicy();
        lexicalPreservationNotNormalization();
        counterExamples();
        mappingToNativeTypesWithPatternMatching();

        System.out.println("\n=== All examples completed successfully! ===");
    }

    public static BigDecimal parseToBigDecimalLossless() {
        var n = (JsonNumber) Json.parse("3.141592653589793238462643383279");
        var bd = new BigDecimal(n.toString());
        System.out.println("lossless BigDecimal: " + bd.toPlainString());
        return bd;
    }

    public static BigInteger parseToBigIntegerExact() {
        var n = (JsonNumber) Json.parse("1.23e2");
        var bi = new BigDecimal(n.toString()).toBigIntegerExact();
        System.out.println("exact BigInteger: " + bi);
        return bi;
    }

    public static JsonNumber bigDecimalToJsonNumberChooseTextPolicy() {
        // Example with toPlainString() to avoid scientific notation.
        var bdPlain = new BigDecimal("1000");

        var plain = JsonNumber.of(bdPlain.toPlainString());
        System.out.println("BigDecimal.toPlainString() -> JsonNumber: " + plain);

        // Example with toString(), which may use scientific notation.
        var bdScientific = new BigDecimal("1E+3");
        var scientific = JsonNumber.of(bdScientific.toString());
        System.out.println("BigDecimal.toString()      -> JsonNumber: " + scientific);

        return plain;
    }

    public static boolean lexicalPreservationNotNormalization() {
        var a = (JsonNumber) Json.parse("1e2");
        var b = (JsonNumber) Json.parse("100");

        System.out.println("a.toString(): " + a);
        System.out.println("b.toString(): " + b);
        System.out.println("same numeric value? " + new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())));

        return a.toString().equals(b.toString());
    }

    public static void counterExamples() {
        System.out.println("Counter-examples");
        System.out.println("----------------");

        // 1) Converting a non-integral JSON number to long throws.
        try {
            var nonIntegral = (JsonNumber) Json.parse("5.5");
            System.out.println("nonIntegral.toString(): " + nonIntegral);
            System.out.println("nonIntegral.toLong(): " + nonIntegral.toLong());
        } catch (JsonAssertionException e) {
            System.out.println("Expected toLong() failure: " + e.getMessage());
        }

        // 2) Converting an out-of-range JSON number to double throws.
        try {
            var tooBig = (JsonNumber) Json.parse("1e309");
            System.out.println("tooBig.toString(): " + tooBig);
            System.out.println("tooBig.toDouble(): " + tooBig.toDouble());
        } catch (JsonAssertionException e) {
            System.out.println("Expected toDouble() failure: " + e.getMessage());
        }

        // 3) Converting to double can be lossy even when it does not throw.
        var highPrecision = (JsonNumber) Json.parse("3.141592653589793238462643383279");
        var lossless = new BigDecimal(highPrecision.toString());
        var lossy = new BigDecimal(Double.toString(highPrecision.toDouble()));
        System.out.println("lossless (BigDecimal): " + lossless.toPlainString());
        System.out.println("lossy    (double->BD): " + lossy.toPlainString());
        System.out.println("lossless equals lossy? " + (lossless.compareTo(lossy) == 0));
        System.out.println();
    }

    public static Object toNative(JsonValue v) {
        return switch (v) {
            case JsonNull ignored -> null;
            case JsonBoolean b -> b.bool();
            case JsonString s -> s.string();
            case JsonNumber n -> {
                try {
                    yield n.toLong();
                } catch (JsonAssertionException ignored) {
                    yield new BigDecimal(n.toString());
                }
            }
            case JsonArray a -> a.elements().stream().map(DesignChoicesExamples::toNative).toList();
            case JsonObject o -> o.members().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> toNative(e.getValue())));
        };
    }

    public static Object mappingToNativeTypesWithPatternMatching() {
        System.out.println("Mapping to native types (pattern matching)");
        System.out.println("------------------------------------------");

        JsonValue json = Json.parse("""
            {
              "smallInt": 42,
              "decimal": 5.5,
              "huge": 3.141592653589793238462643383279,
              "flag": true,
              "name": "Ada",
              "items": [1, 2, 3]
            }
            """);

        Object nativeValue = toNative(json);
        System.out.println("native class: " + nativeValue.getClass().getName());
        System.out.println("native value: " + nativeValue);
        System.out.println();
        return nativeValue;
    }

    private DesignChoicesExamples() {}
}

