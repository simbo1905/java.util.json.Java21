package jdk.incubator.java.util.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Evidence matrix for issue #118 (`JsonNumber.of(double)` decimal/exponent
/// offsets). The historic local fix delegated `of(double)` to `of(String)`;
/// upstream has since reworked the numeric logic to compute the offsets via
/// `indexOf`. These tests prove the uplifted upstream implementation handles
/// the full matrix that motivated #118, making the carried fix unnecessary.
public class JsonNumberOfDoubleMatrixTest extends JsonTestLoggingConfig {

    private static void assertIntegralDouble(double d, long expected) {
        var jn = JsonNumber.of(d);
        // of(double) must be equivalent to the historic of(String) delegation
        var viaString = JsonNumber.of(Double.toString(d));
        assertEquals(viaString.toString(), jn.toString(), "toString for " + d);
        assertEquals(expected, jn.asLong(), "asLong for " + d);
        assertEquals((int) expected, jn.asInt(), "asInt for " + d);
        assertEquals(d, jn.asDouble(), "asDouble for " + d);
    }

    @Test
    void integralDoublesConvertExactly() {
        assertIntegralDouble(123.0, 123L);
        assertIntegralDouble(1.0E2, 100L);
        assertIntegralDouble(42.0, 42L);
        assertIntegralDouble(420e-1, 42L);
        assertIntegralDouble(42e6, 42_000_000L);
        assertIntegralDouble(0.0, 0L);
        assertIntegralDouble(1.0, 1L);
        assertIntegralDouble(5.000, 5L);
    }

    @Test
    void negativeIntegralDoublesConvertExactly() {
        assertIntegralDouble(-123.0, -123L);
        assertIntegralDouble(-42e6, -42_000_000L);
        assertIntegralDouble(-1.0, -1L);
    }

    @Test
    void negativeZeroBehaves() {
        var jn = JsonNumber.of(-0.0);
        assertEquals("-0.0", jn.toString());
        assertEquals(0L, jn.asLong());
        assertEquals(0, jn.asInt());
        assertEquals(-0.0, jn.asDouble());
        assertEquals(0.0, jn.asDouble() + 0.0);
    }

    @Test
    void fractionalDoublesAreNotIntegral() {
        assertNotIntegral(123.45);
        assertNotIntegral(0.1);
        assertNotIntegral(0.002);
        assertNotIntegral(-123.45);
    }

    private static void assertNotIntegral(double d) {
        var jn = JsonNumber.of(d);
        assertThrows(JsonValueException.class, jn::asLong, "asLong for " + d);
        assertThrows(JsonValueException.class, jn::asInt, "asInt for " + d);
        assertEquals(d, jn.asDouble(), "asDouble for " + d);
        // of(double) must match the of(String) representation exactly
        assertEquals(JsonNumber.of(Double.toString(d)).toString(), jn.toString());
    }

    @Test
    void outOfRangeIntegralThrowsJsonValueException() {
        assertNotConvertibleToLong(1e300);
        assertNotConvertibleToLong(-1e300);
        assertNotConvertibleToLong(Double.MAX_VALUE);
        assertNotConvertibleToLong(9.3e18);
    }

    private static void assertNotConvertibleToLong(double d) {
        var jn = JsonNumber.of(d);
        assertThrows(JsonValueException.class, jn::asLong, "asLong for " + d);
        assertThrows(JsonValueException.class, jn::asInt, "asInt for " + d);
        assertEquals(d, jn.asDouble(), "asDouble for " + d);
    }

    @Test
    void tinyMagnitudesRoundTripAsDouble() {
        var tiny = JsonNumber.of(4.9E-324);
        assertEquals(4.9E-324, tiny.asDouble());
        assertThrows(JsonValueException.class, tiny::asLong);
        var small = JsonNumber.of(5e-100);
        assertEquals(5e-100, small.asDouble());
        assertThrows(JsonValueException.class, small::asLong);
    }

    @Test
    void ofDoubleMatchesParsedEquivalent() {
        // of(double) goes through Double.toString; parsing that same text must
        // produce the same conversions (the equivalence #118 was about)
        for (double d : new double[]{123.0, 1.0E2, 42e6, 0.0, -0.0, 123.45, 1e300, 4.9E-324}) {
            var factory = JsonNumber.of(d);
            var parsed = Json.parse(Double.toString(d));
            assertNotEquals(parsed, factory); // identity semantics; compare behaviour
            assertEquals(factory.toString(), parsed.toString(), "toString for " + d);
            assertEquals(factory.asDouble(), ((JsonNumber) parsed).asDouble(), "asDouble for " + d);
        }
    }

    @Test
    void nonFiniteDoublesRejected() {
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.NEGATIVE_INFINITY));
    }
}
