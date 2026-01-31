package jdk.sandbox.java.util.json.examples;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonNumber;

import java.math.BigDecimal;
import java.math.BigInteger;

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
        var bd = new BigDecimal("1000");

        var plain = JsonNumber.of(bd.toPlainString());
        System.out.println("BigDecimal.toPlainString() -> JsonNumber: " + plain);

        var scientific = JsonNumber.of(new BigDecimal("1E+3").toString());
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

    private DesignChoicesExamples() {}
}

