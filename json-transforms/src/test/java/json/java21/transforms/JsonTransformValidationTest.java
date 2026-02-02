package json.java21.transforms;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class JsonTransformValidationTest extends JsonTransformsLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonTransformValidationTest.class.getName());

    @Test
    void invalidVerb() {
        LOG.info(() -> "TEST: invalidVerb");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.invalid\":false}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }

    @Test
    void invalidVerbValue() {
        LOG.info(() -> "TEST: invalidVerbValue");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.remove\":10}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }

    @Test
    void invalidAttribute() {
        LOG.info(() -> "TEST: invalidAttribute");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.replace\": {\"@jdt.invalid\": false}}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }

    @Test
    void missingAttribute() {
        LOG.info(() -> "TEST: missingAttribute");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.rename\": {\"@jdt.path\": \"$.A\"}}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }

    @Test
    void mixedAttributes() {
        LOG.info(() -> "TEST: mixedAttributes");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.rename\": {\"@jdt.path\": \"$.A\", \"@jdt.value\": \"Astar\", \"NotAttribute\": true}}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }

    @Test
    void wrongAttributeValueType() {
        LOG.info(() -> "TEST: wrongAttributeValueType");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.remove\": {\"@jdt.path\": false}}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }

    @Test
    void removeNonExistentNodeByPathIsNoOp() {
        LOG.info(() -> "TEST: removeNonExistentNodeByPathIsNoOp");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.remove\": {\"@jdt.path\": \"$.B\"}}");

        var result = JsonTransform.parse(transform).run(source);
        assertThat(result).isEqualTo(source);
    }

    @Test
    void removeRootThrows() {
        LOG.info(() -> "TEST: removeRootThrows");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.remove\": true}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }

    @Test
    void invalidRenameMappingValue() {
        LOG.info(() -> "TEST: invalidRenameMappingValue");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.rename\": {\"A\": 10}}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }

    @Test
    void renameNonExistentNodeIsNoOp() {
        LOG.info(() -> "TEST: renameNonExistentNodeIsNoOp");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.rename\": {\"B\": \"Bstar\"}}");

        var result = JsonTransform.parse(transform).run(source);
        assertThat(result).isEqualTo(source);
    }

    @Test
    void replaceRootWithPrimitiveThrows() {
        LOG.info(() -> "TEST: replaceRootWithPrimitiveThrows");

        var source = Json.parse("{\"A\":1}");
        var transform = Json.parse("{\"@jdt.replace\": 10}");

        assertThatThrownBy(() -> JsonTransform.parse(transform).run(source))
                .isInstanceOf(JsonTransformException.class);
    }
}

