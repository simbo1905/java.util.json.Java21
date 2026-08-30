package jdk.incubator.java.util.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Boundary hardening for JSON number math, added by the final-defense audit
/// of issue #145. Complements the ported upstream {@code TestJsonNumber} and
/// the issue #118 {@code JsonNumberOfDoubleMatrixTest} with the extremes that
/// exercise {@code JsonNumberImpl}'s fast path, overflow guards
/// ({@code Math.addExact}/{@code subtractExact}/{@code negateExact} and
/// {@code Utils.powExact}) and the {@code JsonNumber.of(String)} whitespace
/// contract. Expected behaviour is taken from the {@link JsonValue} javadoc
/// ranges ("MIN_VALUE to MAX_VALUE, inclusive"), the upstream implementation
/// and RFC 8259 section 6.
public class JsonNumberBoundaryTest extends JsonTestLoggingConfig {

    @Test
    void integerMinValueBoundaryIsRepresentable() {
        var jn = Json.parse("-2147483648");
        assertEquals(Integer.MIN_VALUE, jn.asInt(), "asInt at Integer.MIN_VALUE");
        assertEquals(-2147483648L, jn.asLong(), "asLong at Integer.MIN_VALUE");
        assertEquals(-2147483648.0d, jn.asDouble(), "asDouble at Integer.MIN_VALUE");
        assertEquals("-2147483648", jn.toString(), "toString preservation");
    }

    @Test
    void longMinValueBoundaryIsRepresentable() {
        var jn = Json.parse("-9223372036854775808");
        assertEquals(Long.MIN_VALUE, jn.asLong(), "asLong at Long.MIN_VALUE");
        assertThrows(JsonValueException.class, jn::asInt, "asInt beyond Integer range");
        assertEquals(-9.223372036854776E18d, jn.asDouble(), "asDouble at Long.MIN_VALUE");
        assertEquals("-9223372036854775808", jn.toString(), "toString preservation");
    }

    @Test
    void exponentAtIntMaxOverflowsAllConversions() {
        // 1e2147483647: Integer.parseInt of the exponent succeeds, but
        // Utils.powExact(10, 2147483647) overflows long -> ArithmeticException
        // -> Optional.empty -> JsonValueException; asDouble is Infinity.
        var jn = Json.parse("1e2147483647");
        assertThrows(JsonValueException.class, jn::asInt, "asInt for 1e2147483647");
        assertThrows(JsonValueException.class, jn::asLong, "asLong for 1e2147483647");
        assertThrows(JsonValueException.class, jn::asDouble, "asDouble for 1e2147483647");
        assertEquals("1e2147483647", jn.toString(), "toString preservation");
    }

    @Test
    void exponentAboveIntMaxOverflowsAllConversions() {
        // 1e2147483648: Integer.parseInt of the exponent itself overflows.
        var jn = Json.parse("1e2147483648");
        assertThrows(JsonValueException.class, jn::asInt, "asInt for 1e2147483648");
        assertThrows(JsonValueException.class, jn::asLong, "asLong for 1e2147483648");
        assertThrows(JsonValueException.class, jn::asDouble, "asDouble for 1e2147483648");
        assertEquals("1e2147483648", jn.toString(), "toString preservation");
    }

    @Test
    void exponentAtIntMinOverflowsIntegralOnly() {
        // 1e-2147483648: Math.negateExact(Integer.MIN_VALUE) overflows during
        // the 10^power division path -> JsonValueException for asInt/asLong;
        // asDouble underflows to a finite 0.0 via Double.parseDouble.
        var jn = Json.parse("1e-2147483648");
        assertThrows(JsonValueException.class, jn::asInt, "asInt for 1e-2147483648");
        assertThrows(JsonValueException.class, jn::asLong, "asLong for 1e-2147483648");
        assertEquals(0.0d, jn.asDouble(), "asDouble for 1e-2147483648");
        assertEquals("1e-2147483648", jn.toString(), "toString preservation");
    }

    @Test
    void exponentBelowIntMinOverflowsIntegralOnly() {
        // 1e-2147483649: Integer.parseInt of the exponent fails; asDouble
        // still underflows to a finite 0.0.
        var jn = Json.parse("1e-2147483649");
        assertThrows(JsonValueException.class, jn::asInt, "asInt for 1e-2147483649");
        assertThrows(JsonValueException.class, jn::asLong, "asLong for 1e-2147483649");
        assertEquals(0.0d, jn.asDouble(), "asDouble for 1e-2147483649");
        assertEquals("1e-2147483649", jn.toString(), "toString preservation");
    }

    @Test
    void parsedNegativeZeroKeepsTextAndValue() {
        var jn = Json.parse("-0");
        assertEquals("-0", jn.toString(), "toString preservation");
        assertEquals(0, jn.asInt(), "asInt of -0");
        assertEquals(0L, jn.asLong(), "asLong of -0");
        // Double.compare distinguishes -0.0 from 0.0; RFC 8259 permits "-0"
        // and asDouble is specified to properly return negative zero.
        assertEquals(0, Double.compare(jn.asDouble(), -0.0d), "asDouble of -0 is negative zero");
    }

    @Test
    void ofStringStripsInsignificantWhitespace() {
        // JsonNumber.of(String) javadoc: the representation is equivalent to
        // num with any leading or trailing JSON insignificant whitespaces removed.
        var jn = JsonNumber.of(" 3 ");
        assertEquals("3", jn.toString(), "toString of of(\" 3 \")");
        assertEquals(3, jn.asInt(), "asInt of of(\" 3 \")");
        var neg = JsonNumber.of("\t-0.5\n");
        assertEquals("-0.5", neg.toString(), "toString of of(\"\\t-0.5\\n\")");
        assertEquals(-0.5d, neg.asDouble(), "asDouble of of(\"\\t-0.5\\n\")");
    }

    @Test
    void bareMinusAndEmptyTextAreNotJsonNumbers() {
        // RFC 8259 section 6: a number must contain at least one integer digit;
        // an empty text is not a JSON value (RFC 8259 section 2).
        assertThrows(JsonParseException.class, () -> Json.parse("-"), "parse(\"-\")");
        assertThrows(JsonParseException.class, () -> Json.parse(""), "parse(\"\")");
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of("-"), "of(\"-\")");
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(""), "of(\"\")");
    }

    @Test
    void intMinValueViaFactoryStringContract() {
        // of(String) must accept the same boundary text parse accepts and be
        // behaviourally identical to the parsed value.
        var viaFactory = JsonNumber.of("-2147483648");
        var viaParse = Json.parse("-2147483648");
        assertEquals(viaParse.toString(), viaFactory.toString(), "toString parity");
        assertEquals(Integer.MIN_VALUE, viaFactory.asInt(), "asInt parity");
        assertEquals(Long.MIN_VALUE, JsonNumber.of("-9223372036854775808").asLong(),
                "asLong of of(\"-9223372036854775808\")");
        assertTrue(viaFactory instanceof JsonNumber, "factory produces JsonNumber");
    }
}
