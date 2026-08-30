package jdk.incubator.java.util.json;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class TestJsonNumberOfDouble extends JsonTestLoggingConfig {

    @Test
    void ofDoubleToStringPreservesValue() {
        var jn = JsonNumber.of(123.45);
        assertThat(jn.toString()).isEqualTo("123.45");
    }

    @Test
    void ofDoubleToDoubleWorks() {
        var jn = JsonNumber.of(123.45);
        assertThat(jn.asDouble()).isEqualTo(123.45);
    }

    @Test
    void ofDoubleThenToLongForIntegralDouble() {
        // 123.0 should be convertible to long 123
        var jn = JsonNumber.of(123.0);
        assertThat(jn.asLong()).isEqualTo(123L);
    }
    
    @Test
    void ofDoubleThenToLongForNonIntegralShouldThrow() {
        var jn = JsonNumber.of(123.45);
        assertThatThrownBy(() -> jn.asLong())
            .isInstanceOf(jdk.incubator.java.util.json.JsonValueException.class);
    }
}
