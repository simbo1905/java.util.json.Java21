package json.java21.jsonpath;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

class JsonPathStreamsTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathStreamsTest.class.getName());

    @Test
    void testPredicates() {
        LOG.info(() -> "JsonPathStreamsTest#testPredicates");
        JsonValue num = JsonNumber.of(1);
        JsonValue str = JsonString.of("s");
        JsonValue bool = JsonBoolean.of(true);
        JsonValue arr = Json.parse("[]");
        JsonValue obj = Json.parse("{}");
        JsonValue nul = JsonNull.of();

        assertThat(JsonPathStreams.isNumber(num)).isTrue();
        assertThat(JsonPathStreams.isNumber(str)).isFalse();

        assertThat(JsonPathStreams.isString(str)).isTrue();
        assertThat(JsonPathStreams.isString(num)).isFalse();

        assertThat(JsonPathStreams.isBoolean(bool)).isTrue();
        assertThat(JsonPathStreams.isBoolean(str)).isFalse();

        assertThat(JsonPathStreams.isArray(arr)).isTrue();
        assertThat(JsonPathStreams.isArray(obj)).isFalse();

        assertThat(JsonPathStreams.isObject(obj)).isTrue();
        assertThat(JsonPathStreams.isObject(arr)).isFalse();

        assertThat(JsonPathStreams.isNull(nul)).isTrue();
        assertThat(JsonPathStreams.isNull(str)).isFalse();
    }

    @Test
    void testStrictConverters() {
        LOG.info(() -> "JsonPathStreamsTest#testStrictConverters");
        JsonValue num = JsonNumber.of(123.45);
        JsonValue numInt = JsonNumber.of(100);
        JsonValue str = JsonString.of("foo");
        JsonValue bool = JsonBoolean.of(true);

        // asDouble
        assertThat(JsonPathStreams.asDouble(num)).isEqualTo(123.45);
        assertThatThrownBy(() -> JsonPathStreams.asDouble(str))
            .isInstanceOf(ClassCastException.class)
            .hasMessageContaining("Expected JsonNumber");

        // asLong
        assertThat(JsonPathStreams.asLong(numInt)).isEqualTo(100L);
        assertThatThrownBy(() -> JsonPathStreams.asLong(str))
            .isInstanceOf(ClassCastException.class)
            .hasMessageContaining("Expected JsonNumber");

        // asString
        assertThat(JsonPathStreams.asString(str)).isEqualTo("foo");
        assertThatThrownBy(() -> JsonPathStreams.asString(num))
            .isInstanceOf(ClassCastException.class)
            .hasMessageContaining("Expected JsonString");

        // asBoolean
        assertThat(JsonPathStreams.asBoolean(bool)).isTrue();
        assertThatThrownBy(() -> JsonPathStreams.asBoolean(str))
            .isInstanceOf(ClassCastException.class)
            .hasMessageContaining("Expected JsonBoolean");
    }

    @Test
    void testLaxConverters() {
        LOG.info(() -> "JsonPathStreamsTest#testLaxConverters");
        JsonValue num = JsonNumber.of(123.45);
        JsonValue numInt = JsonNumber.of(100);
        JsonValue str = JsonString.of("foo");
        JsonValue bool = JsonBoolean.of(true);

        // asDoubleOrNull
        assertThat(JsonPathStreams.asDoubleOrNull(num)).isEqualTo(123.45);
        assertThat(JsonPathStreams.asDoubleOrNull(str)).isNull();

        // asLongOrNull
        assertThat(JsonPathStreams.asLongOrNull(numInt)).isEqualTo(100L);
        assertThat(JsonPathStreams.asLongOrNull(str)).isNull();

        // asStringOrNull
        assertThat(JsonPathStreams.asStringOrNull(str)).isEqualTo("foo");
        assertThat(JsonPathStreams.asStringOrNull(num)).isNull();

        // asBooleanOrNull
        assertThat(JsonPathStreams.asBooleanOrNull(bool)).isTrue();
        assertThat(JsonPathStreams.asBooleanOrNull(str)).isNull();
    }
}
