package json.java21.transforms;

import jdk.sandbox.java.util.json.*;
import json.java21.transforms.JsonTransformsAst.*;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/// Tests for JsonTransformsParser - tests parsing of transform specifications to AST.
class JsonTransformsParserTest extends JsonTransformsLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonTransformsParserTest.class.getName());

    @Test
    void testParseEmptyTransform() {
        LOG.info(() -> "TEST: testParseEmptyTransform");
        final JsonValue transform = Json.parse("{}");
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast).isNotNull();
        assertThat(ast.nodes()).isEmpty();
        assertThat(ast.pathSelector()).isNull();
    }

    @Test
    void testParseSimpleValueOp() {
        LOG.info(() -> "TEST: testParseSimpleValueOp");
        final JsonValue transform = Json.parse("""
            {
                "newProp": {
                    "@jdt.value": "hello"
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast.nodes()).hasSize(1);
        final TransformNode node = ast.nodes().getFirst();
        assertThat(node).isInstanceOf(PropertyTransform.class);
        
        final PropertyTransform pt = (PropertyTransform) node;
        assertThat(pt.key()).isEqualTo("newProp");
        assertThat(pt.operation()).isInstanceOf(ValueOp.class);
        
        final ValueOp valueOp = (ValueOp) pt.operation();
        assertThat(valueOp.value()).isInstanceOf(JsonString.class);
        assertThat(((JsonString) valueOp.value()).string()).isEqualTo("hello");
    }

    @Test
    void testParseImplicitValueOp() {
        LOG.info(() -> "TEST: testParseImplicitValueOp - non-object value is implicit @jdt.value");
        final JsonValue transform = Json.parse("""
            {
                "newProp": "hello"
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast.nodes()).hasSize(1);
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        assertThat(pt.key()).isEqualTo("newProp");
        assertThat(pt.operation()).isInstanceOf(ValueOp.class);
        
        final ValueOp valueOp = (ValueOp) pt.operation();
        assertThat(((JsonString) valueOp.value()).string()).isEqualTo("hello");
    }

    @Test
    void testParseRemoveOp() {
        LOG.info(() -> "TEST: testParseRemoveOp");
        final JsonValue transform = Json.parse("""
            {
                "obsolete": {
                    "@jdt.remove": true
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast.nodes()).hasSize(1);
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        assertThat(pt.key()).isEqualTo("obsolete");
        assertThat(pt.operation()).isInstanceOf(RemoveOp.class);
        
        final RemoveOp removeOp = (RemoveOp) pt.operation();
        assertThat(removeOp.remove()).isTrue();
    }

    @Test
    void testParseRenameOp() {
        LOG.info(() -> "TEST: testParseRenameOp");
        final JsonValue transform = Json.parse("""
            {
                "oldName": {
                    "@jdt.rename": "newName"
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast.nodes()).hasSize(1);
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        assertThat(pt.key()).isEqualTo("oldName");
        assertThat(pt.operation()).isInstanceOf(RenameOp.class);
        
        final RenameOp renameOp = (RenameOp) pt.operation();
        assertThat(renameOp.newName()).isEqualTo("newName");
    }

    @Test
    void testParseReplaceOp() {
        LOG.info(() -> "TEST: testParseReplaceOp");
        final JsonValue transform = Json.parse("""
            {
                "existing": {
                    "@jdt.replace": "new value"
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast.nodes()).hasSize(1);
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        assertThat(pt.key()).isEqualTo("existing");
        assertThat(pt.operation()).isInstanceOf(ReplaceOp.class);
        
        final ReplaceOp replaceOp = (ReplaceOp) pt.operation();
        assertThat(((JsonString) replaceOp.value()).string()).isEqualTo("new value");
    }

    @Test
    void testParseMergeOp() {
        LOG.info(() -> "TEST: testParseMergeOp");
        final JsonValue transform = Json.parse("""
            {
                "config": {
                    "@jdt.merge": {
                        "newSetting": true,
                        "timeout": 5000
                    }
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast.nodes()).hasSize(1);
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        assertThat(pt.key()).isEqualTo("config");
        assertThat(pt.operation()).isInstanceOf(MergeOp.class);
        
        final MergeOp mergeOp = (MergeOp) pt.operation();
        assertThat(mergeOp.mergeValue()).isInstanceOf(JsonObject.class);
    }

    @Test
    void testParseRootPathSelector() {
        LOG.info(() -> "TEST: testParseRootPathSelector");
        final JsonValue transform = Json.parse("""
            {
                "@jdt.path": "$.users[*]",
                "status": {
                    "@jdt.value": "active"
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast.pathSelector()).isNotNull();
        assertThat(ast.nodes()).hasSize(1);
    }

    @Test
    void testParseMultipleOperations() {
        LOG.info(() -> "TEST: testParseMultipleOperations");
        final JsonValue transform = Json.parse("""
            {
                "oldProp": {
                    "@jdt.remove": true
                },
                "newProp": {
                    "@jdt.value": 42
                },
                "renamedProp": {
                    "@jdt.rename": "betterName"
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        assertThat(ast.nodes()).hasSize(3);
    }

    @Test
    void testParseInvalidTransformThrows() {
        LOG.info(() -> "TEST: testParseInvalidTransformThrows - array instead of object");
        final JsonValue transform = Json.parse("[1, 2, 3]");
        
        assertThatThrownBy(() -> JsonTransformsParser.parse(transform))
            .isInstanceOf(JsonTransformsParseException.class)
            .hasMessageContaining("must be a JSON object");
    }

    @Test
    void testParseInvalidRemoveTypeThrows() {
        LOG.info(() -> "TEST: testParseInvalidRemoveTypeThrows");
        final JsonValue transform = Json.parse("""
            {
                "prop": {
                    "@jdt.remove": "yes"
                }
            }
            """);
        
        assertThatThrownBy(() -> JsonTransformsParser.parse(transform))
            .isInstanceOf(JsonTransformsParseException.class)
            .hasMessageContaining("@jdt.remove must be a boolean");
    }

    @Test
    void testParseInvalidRenameTypeThrows() {
        LOG.info(() -> "TEST: testParseInvalidRenameTypeThrows");
        final JsonValue transform = Json.parse("""
            {
                "prop": {
                    "@jdt.rename": 123
                }
            }
            """);
        
        assertThatThrownBy(() -> JsonTransformsParser.parse(transform))
            .isInstanceOf(JsonTransformsParseException.class)
            .hasMessageContaining("@jdt.rename must be a string");
    }

    @Test
    void testParseInvalidMergeTypeThrows() {
        LOG.info(() -> "TEST: testParseInvalidMergeTypeThrows");
        final JsonValue transform = Json.parse("""
            {
                "prop": {
                    "@jdt.merge": "not an object"
                }
            }
            """);
        
        assertThatThrownBy(() -> JsonTransformsParser.parse(transform))
            .isInstanceOf(JsonTransformsParseException.class)
            .hasMessageContaining("@jdt.merge must be an object");
    }

    @Test
    void testParseUnknownDirectiveThrows() {
        LOG.info(() -> "TEST: testParseUnknownDirectiveThrows");
        final JsonValue transform = Json.parse("""
            {
                "prop": {
                    "@jdt.unknown": "value"
                }
            }
            """);
        
        assertThatThrownBy(() -> JsonTransformsParser.parse(transform))
            .isInstanceOf(JsonTransformsParseException.class)
            .hasMessageContaining("Unknown transform directive");
    }

    @Test
    void testParseNullThrows() {
        LOG.info(() -> "TEST: testParseNullThrows");
        assertThatThrownBy(() -> JsonTransformsParser.parse(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testParseInvalidJsonPathThrows() {
        LOG.info(() -> "TEST: testParseInvalidJsonPathThrows");
        final JsonValue transform = Json.parse("""
            {
                "@jdt.path": "invalid path syntax"
            }
            """);
        
        assertThatThrownBy(() -> JsonTransformsParser.parse(transform))
            .isInstanceOf(JsonTransformsParseException.class)
            .hasMessageContaining("Invalid JsonPath expression");
    }

    @Test
    void testParseValueOpWithNumber() {
        LOG.info(() -> "TEST: testParseValueOpWithNumber");
        final JsonValue transform = Json.parse("""
            {
                "count": {
                    "@jdt.value": 42
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        final ValueOp valueOp = (ValueOp) pt.operation();
        assertThat(valueOp.value()).isInstanceOf(JsonNumber.class);
        assertThat(((JsonNumber) valueOp.value()).toLong()).isEqualTo(42);
    }

    @Test
    void testParseValueOpWithBoolean() {
        LOG.info(() -> "TEST: testParseValueOpWithBoolean");
        final JsonValue transform = Json.parse("""
            {
                "active": {
                    "@jdt.value": true
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        final ValueOp valueOp = (ValueOp) pt.operation();
        assertThat(valueOp.value()).isInstanceOf(JsonBoolean.class);
        assertThat(((JsonBoolean) valueOp.value()).bool()).isTrue();
    }

    @Test
    void testParseValueOpWithNull() {
        LOG.info(() -> "TEST: testParseValueOpWithNull");
        final JsonValue transform = Json.parse("""
            {
                "optional": {
                    "@jdt.value": null
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        final ValueOp valueOp = (ValueOp) pt.operation();
        assertThat(valueOp.value()).isInstanceOf(JsonNull.class);
    }

    @Test
    void testParseValueOpWithArray() {
        LOG.info(() -> "TEST: testParseValueOpWithArray");
        final JsonValue transform = Json.parse("""
            {
                "tags": {
                    "@jdt.value": ["a", "b", "c"]
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        final ValueOp valueOp = (ValueOp) pt.operation();
        assertThat(valueOp.value()).isInstanceOf(JsonArray.class);
        assertThat(((JsonArray) valueOp.value()).elements()).hasSize(3);
    }

    @Test
    void testParseValueOpWithObject() {
        LOG.info(() -> "TEST: testParseValueOpWithObject");
        final JsonValue transform = Json.parse("""
            {
                "nested": {
                    "@jdt.value": {"key": "value"}
                }
            }
            """);
        final TransformRoot ast = JsonTransformsParser.parse(transform);
        
        final PropertyTransform pt = (PropertyTransform) ast.nodes().getFirst();
        final ValueOp valueOp = (ValueOp) pt.operation();
        assertThat(valueOp.value()).isInstanceOf(JsonObject.class);
    }
}
