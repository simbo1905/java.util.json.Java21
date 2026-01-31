package jdk.sandbox.java.util.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

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
    void parseExponentFormToBigIntegerExactWorks() {
        LOGGER.info("Executing parseExponentFormToBigIntegerExactWorks");

        var n = (JsonNumber) Json.parse("1.23e2");
        BigInteger bi = new BigDecimal(n.toString()).toBigIntegerExact();

        assertThat(bi).isEqualTo(BigInteger.valueOf(123));
    }

    @Test
    void bigDecimalToJsonNumberRequiresChoosingATextPolicy() {
        LOGGER.info("Executing bigDecimalToJsonNumberRequiresChoosingATextPolicy");

        var thousand = new BigDecimal("1000");

        var plain = JsonNumber.of(thousand.toPlainString());
        assertThat(plain.toString()).isEqualTo("1000");

        var scientific = JsonNumber.of(new BigDecimal("1E+3").toString());
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
}

