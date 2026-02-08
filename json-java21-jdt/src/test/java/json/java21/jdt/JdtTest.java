package json.java21.jdt;

import jdk.sandbox.java.util.json.*;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/// Unit tests for the JDT (JSON Document Transforms) engine.
class JdtTest extends JdtLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JdtTest.class.getName());

    // ========== Default Transformation Tests ==========

    @Test
    void defaultTransform_primitiveReplacement() {
        LOG.info(() -> "TEST: defaultTransform_primitiveReplacement");
        
        final var source = Json.parse("""
            {"A": 1, "B": true, "C": null}
            """);
        final var transform = Json.parse("""
            {"A": 10, "B": false}
            """);
        
        final var result = Jdt.transform(source, transform);
        
        assertThat(result).isInstanceOf(JsonObject.class);
        final var obj = (JsonObject) result;
        assertThat(obj.members().get("A")).isEqualTo(Json.parse("10"));
        assertThat(obj.members().get("B")).isEqualTo(Json.parse("false"));
        assertThat(obj.members().get("C")).isEqualTo(JsonNull.of());
    }

    @Test
    void defaultTransform_addNewKey() {
        LOG.info(() -> "TEST: defaultTransform_addNewKey");
        
        final var source = Json.parse("""
            {"A": 1}
            """);
        final var transform = Json.parse("""
            {"B": 2}
            """);
        
        final var result = Jdt.transform(source, transform);
        
        assertThat(result).isInstanceOf(JsonObject.class);
        final var obj = (JsonObject) result;
        assertThat(obj.members().get("A")).isEqualTo(Json.parse("1"));
        assertThat(obj.members().get("B")).isEqualTo(Json.parse("2"));
    }

    @Test
    void defaultTransform_recursiveMerge() {
        LOG.info(() -> "TEST: defaultTransform_recursiveMerge");
        
        final var source = Json.parse("""
            {
                "Settings": {
                    "Setting01": "Original",
                    "Setting02": "Untouched"
                }
            }
            """);
        final var transform = Json.parse("""
            {
                "Settings": {
                    "Setting01": "Updated",
                    "Setting03": "New"
                }
            }
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {
                "Settings": {
                    "Setting01": "Updated",
                    "Setting02": "Untouched",
                    "Setting03": "New"
                }
            }
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void defaultTransform_arrayAppend() {
        LOG.info(() -> "TEST: defaultTransform_arrayAppend");
        
        final var source = Json.parse("""
            {"items": [1, 2, 3]}
            """);
        final var transform = Json.parse("""
            {"items": [4, 5]}
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {"items": [1, 2, 3, 4, 5]}
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    // ========== @jdt.replace Tests ==========

    @Test
    void replace_withPrimitive() {
        LOG.info(() -> "TEST: replace_withPrimitive");
        
        final var source = Json.parse("""
            {"A": {"nested": "value"}}
            """);
        final var transform = Json.parse("""
            {"A": {"@jdt.replace": 1}}
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {"A": 1}
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void replace_withObject() {
        LOG.info(() -> "TEST: replace_withObject");
        
        final var source = Json.parse("""
            {"A": {"old": "value"}}
            """);
        final var transform = Json.parse("""
            {"A": {"@jdt.replace": {"new": "value"}}}
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {"A": {"new": "value"}}
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void replace_withArrayUsingDoubleBrackets() {
        LOG.info(() -> "TEST: replace_withArrayUsingDoubleBrackets");
        
        final var source = Json.parse("""
            {"A": {"old": "value"}}
            """);
        final var transform = Json.parse("""
            {"A": {"@jdt.replace": [[1, 2, 3]]}}
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {"A": [1, 2, 3]}
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    // ========== @jdt.remove Tests ==========

    @Test
    void remove_singleKey() {
        LOG.info(() -> "TEST: remove_singleKey");
        
        final var source = Json.parse("""
            {"A": 1, "B": 2, "C": 3}
            """);
        final var transform = Json.parse("""
            {"@jdt.remove": "B"}
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {"A": 1, "C": 3}
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void remove_multipleKeys() {
        LOG.info(() -> "TEST: remove_multipleKeys");
        
        final var source = Json.parse("""
            {"A": 1, "B": 2, "C": 3, "D": 4}
            """);
        final var transform = Json.parse("""
            {"@jdt.remove": ["B", "D"]}
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {"A": 1, "C": 3}
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void remove_allWithTrue() {
        LOG.info(() -> "TEST: remove_allWithTrue");
        
        final var source = Json.parse("""
            {"nested": {"A": 1, "B": 2}}
            """);
        final var transform = Json.parse("""
            {"nested": {"@jdt.remove": true}}
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {"nested": null}
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    // ========== @jdt.rename Tests ==========

    @Test
    void rename_singleKey() {
        LOG.info(() -> "TEST: rename_singleKey");
        
        final var source = Json.parse("""
            {"oldName": "value", "other": "data"}
            """);
        final var transform = Json.parse("""
            {"@jdt.rename": {"oldName": "newName"}}
            """);
        
        final var result = Jdt.transform(source, transform);
        
        assertThat(result).isInstanceOf(JsonObject.class);
        final var obj = (JsonObject) result;
        assertThat(obj.members()).containsKey("newName");
        assertThat(obj.members()).doesNotContainKey("oldName");
        assertThat(obj.members().get("newName")).isEqualTo(Json.parse("\"value\""));
    }

    @Test
    void rename_multipleKeys() {
        LOG.info(() -> "TEST: rename_multipleKeys");
        
        final var source = Json.parse("""
            {"A": 1, "B": 2, "C": 3}
            """);
        final var transform = Json.parse("""
            {"@jdt.rename": {"A": "Astar", "B": "Bstar"}}
            """);
        
        final var result = Jdt.transform(source, transform);
        
        assertThat(result).isInstanceOf(JsonObject.class);
        final var obj = (JsonObject) result;
        assertThat(obj.members()).containsKeys("Astar", "Bstar", "C");
        assertThat(obj.members()).doesNotContainKeys("A", "B");
    }

    // ========== @jdt.merge Tests ==========

    @Test
    void merge_explicitMerge() {
        LOG.info(() -> "TEST: merge_explicitMerge");
        
        final var source = Json.parse("""
            {"Settings": {"A": 1, "B": 2}}
            """);
        final var transform = Json.parse("""
            {"Settings": {"@jdt.merge": {"A": 10, "C": 3}}}
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {"Settings": {"A": 10, "B": 2, "C": 3}}
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    // ========== Combined Transformation Tests ==========

    @Test
    void combined_renameAndRemove() {
        LOG.info(() -> "TEST: combined_renameAndRemove");
        
        final var source = Json.parse("""
            {"A": 1, "B": 2, "C": 3}
            """);
        final var transform = Json.parse("""
            {
                "@jdt.rename": {"A": "Astar"},
                "@jdt.remove": "B"
            }
            """);
        
        final var result = Jdt.transform(source, transform);
        
        assertThat(result).isInstanceOf(JsonObject.class);
        final var obj = (JsonObject) result;
        assertThat(obj.members()).containsKeys("Astar", "C");
        assertThat(obj.members()).doesNotContainKeys("A", "B");
    }

    @Test
    void nested_transformations() {
        LOG.info(() -> "TEST: nested_transformations");
        
        final var source = Json.parse("""
            {
                "level1": {
                    "level2": {
                        "value": "original"
                    }
                }
            }
            """);
        final var transform = Json.parse("""
            {
                "level1": {
                    "level2": {
                        "value": "updated",
                        "newKey": "added"
                    }
                }
            }
            """);
        
        final var result = Jdt.transform(source, transform);
        final var expected = Json.parse("""
            {
                "level1": {
                    "level2": {
                        "value": "updated",
                        "newKey": "added"
                    }
                }
            }
            """);
        
        assertThat(result).isEqualTo(expected);
    }

    // ========== Edge Cases ==========

    @Test
    void emptyTransform_returnsSourceUnchanged() {
        LOG.info(() -> "TEST: emptyTransform_returnsSourceUnchanged");
        
        final var source = Json.parse("""
            {"A": 1, "B": 2}
            """);
        final var transform = Json.parse("{}");
        
        final var result = Jdt.transform(source, transform);
        
        assertThat(result).isEqualTo(source);
    }

    @Test
    void primitiveSource_replacedByObjectTransform() {
        LOG.info(() -> "TEST: primitiveSource_replacedByObjectTransform");
        
        final var source = Json.parse("42");
        final var transform = Json.parse("""
            {"value": 42}
            """);
        
        final var result = Jdt.transform(source, transform);
        
        assertThat(result).isEqualTo(transform);
    }

    @Test
    void primitiveTransform_replacesSource() {
        LOG.info(() -> "TEST: primitiveTransform_replacesSource");
        
        final var source = Json.parse("""
            {"A": 1}
            """);
        final var transform = Json.parse("42");
        
        final var result = Jdt.transform(source, transform);
        
        assertThat(result).isEqualTo(transform);
    }

    // ========== AST Parser Tests ==========

    @Test
    void ast_primitiveTransformParsesToReplacement() {
        LOG.info(() -> "TEST: ast_primitiveTransformParsesToReplacement");
        
        final var transform = Json.parse("42");
        final var ast = Jdt.parseToAst(transform);
        
        assertThat(ast).isInstanceOf(JdtAst.ReplacementNode.class);
        assertThat(((JdtAst.ReplacementNode) ast).value()).isEqualTo(transform);
    }

    @Test
    void ast_objectWithoutDirectivesParsesToMergeNode() {
        LOG.info(() -> "TEST: ast_objectWithoutDirectivesParsesToMergeNode");
        
        final var transform = Json.parse("""
            {"A": 1, "B": {"C": 2}}
            """);
        final var ast = Jdt.parseToAst(transform);
        
        assertThat(ast).isInstanceOf(JdtAst.MergeNode.class);
        final var merge = (JdtAst.MergeNode) ast;
        assertThat(merge.children()).containsKeys("A", "B");
        assertThat(merge.children().get("A")).isInstanceOf(JdtAst.ReplacementNode.class);
        assertThat(merge.children().get("B")).isInstanceOf(JdtAst.MergeNode.class);
    }

    @Test
    void ast_objectWithDirectivesParsesToDirectiveNode() {
        LOG.info(() -> "TEST: ast_objectWithDirectivesParsesToDirectiveNode");
        
        final var transform = Json.parse("""
            {
                "@jdt.rename": {"old": "new"},
                "@jdt.remove": "B",
                "C": 3
            }
            """);
        final var ast = Jdt.parseToAst(transform);
        
        assertThat(ast).isInstanceOf(JdtAst.DirectiveNode.class);
        final var directive = (JdtAst.DirectiveNode) ast;
        assertThat(directive.rename()).isNotNull();
        assertThat(directive.remove()).isNotNull();
        assertThat(directive.merge()).isNull();
        assertThat(directive.replace()).isNull();
        assertThat(directive.children()).containsKey("C");
    }

    @Test
    void ast_nestedDirectivesParseCorrectly() {
        LOG.info(() -> "TEST: ast_nestedDirectivesParseCorrectly");
        
        final var transform = Json.parse("""
            {
                "Settings": {
                    "@jdt.merge": {"newKey": "value"},
                    "existing": "updated"
                }
            }
            """);
        final var ast = Jdt.parseToAst(transform);
        
        assertThat(ast).isInstanceOf(JdtAst.MergeNode.class);
        final var root = (JdtAst.MergeNode) ast;
        assertThat(root.children().get("Settings")).isInstanceOf(JdtAst.DirectiveNode.class);
    }
}
