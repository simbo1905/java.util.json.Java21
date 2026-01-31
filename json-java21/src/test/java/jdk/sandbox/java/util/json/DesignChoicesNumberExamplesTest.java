package jdk.sandbox.java.util.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DesignChoicesNumberExamplesTest {
    private static final Logger LOGGER = Logger.getLogger(DesignChoicesNumberExamplesTest.class.getName());

    @Test
    void parseHighPrecisionNumberIsLosslessViaToString() {
        LOGGER.info("Executing parseHighPrecisionNumberIsLosslessViaToString");

        var text = "3.141592653589793238462643383279";
        var n = (JsonNumber) Json.parse(text);

        assertThat(n.toString()).isEqualTo(text);
        assertThat(new BigDecimal(n.toString())).isEqualByComparingTo(new BigDecimal(text));
    }

    @Test
    void convertingToDoubleIsPotentiallyLossy() {
        LOGGER.info("Executing convertingToDoubleIsPotentiallyLossy");

        var text = "3.141592653589793238462643383279";
        var n = (JsonNumber) Json.parse(text);

        var lossless = new BigDecimal(n.toString());
        var lossy = new BigDecimal(Double.toString(n.toDouble()));

        assertThat(lossy).isNotEqualByComparingTo(lossless);
    }

    @Test
    void convertingNonIntegralNumberToLongThrows() {
        LOGGER.info("Executing convertingNonIntegralNumberToLongThrows");

        var n = (JsonNumber) Json.parse("5.5");
        assertThatThrownBy(n::toLong)
                .isInstanceOf(JsonAssertionException.class);
    }

    @Test
    void convertingOutOfRangeNumberToDoubleThrows() {
        LOGGER.info("Executing convertingOutOfRangeNumberToDoubleThrows");

        var n = (JsonNumber) Json.parse("1e309");
        assertThatThrownBy(n::toDouble)
                .isInstanceOf(JsonAssertionException.class);
    }

    @Test
    void parseExponentFormToBigIntegerExactWorks() {
        LOGGER.info("Executing parseExponentFormToBigIntegerExactWorks");

        var n = (JsonNumber) Json.parse("1.23e2");
        BigInteger bi = new BigDecimal(n.toString()).toBigIntegerExact();

        assertThat(bi).isEqualTo(BigInteger.valueOf(123));
    }

    @Test
    void bigDecimalToJsonNumberRequiresChoosingATextPolicy() {
        LOGGER.info("Executing bigDecimalToJsonNumberRequiresChoosingATextPolicy");

        // Using toPlainString() for a plain number representation
        var bdPlain = new BigDecimal("1000");

        var plain = JsonNumber.of(bdPlain.toPlainString());
        assertThat(plain.toString()).isEqualTo("1000");

        // Using toString(), which may produce scientific notation
        var bdScientific = new BigDecimal("1E+3");
        var scientific = JsonNumber.of(bdScientific.toString());
        assertThat(scientific.toString()).isEqualTo("1E+3");
    }

    @Test
    void lexicalPreservationDiffersFromNumericNormalization() {
        LOGGER.info("Executing lexicalPreservationDiffersFromNumericNormalization");

        var a = (JsonNumber) Json.parse("1e2");
        var b = (JsonNumber) Json.parse("100");

        // lexical forms differ
        assertThat(a.toString()).isEqualTo("1e2");
        assertThat(b.toString()).isEqualTo("100");

        // but numeric values compare equal when canonicalized explicitly
        assertThat(new BigDecimal(a.toString())).isEqualByComparingTo(new BigDecimal(b.toString()));
    }

    @Test
    void mappingToNativeTypesUsesPatternMatchingAndExplicitNumberPolicy() {
        LOGGER.info("Executing mappingToNativeTypesUsesPatternMatchingAndExplicitNumberPolicy");

        JsonValue json = Json.parse("""
            {
              "smallInt": 42,
              "decimal": 5.5
            }
            """);

        Object nativeValue = jdk.sandbox.java.util.json.examples.DesignChoicesExamples.toNative(json);
        assertThat(nativeValue).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        var map = (Map<String, Object>) nativeValue;

        assertThat(map.get("smallInt")).isEqualTo(42L);
        assertThat(map.get("decimal")).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) map.get("decimal")).isEqualByComparingTo(new BigDecimal("5.5"));
    }
}

