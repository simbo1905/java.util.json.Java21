package json.java21.jdt;

import jdk.sandbox.java.util.json.Json;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/// Tests for the JDT ESM renderer.
class JdtEsmRendererTest extends JdtLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JdtEsmRendererTest.class.getName());

    @Test
    void renderSimpleMerge() {
        LOG.info(() -> "TEST: renderSimpleMerge");

        final var transform = Json.parse("""
            {"A": 10, "B": "new"}
            """);
        final var ast = Jdt.parseToAst(transform);
        final var esm = JdtEsmRenderer.render(ast);

        assertThat(esm).contains("export function transform(source)");
        assertThat(esm).contains("deepMerge");
        assertThat(esm).contains("return");
        LOG.fine(() -> "Generated ESM:\n" + esm);
    }

    @Test
    void renderDirectiveWithRename() {
        LOG.info(() -> "TEST: renderDirectiveWithRename");

        final var transform = Json.parse("""
            {"@jdt.rename": {"old": "new"}}
            """);
        final var ast = Jdt.parseToAst(transform);
        final var esm = JdtEsmRenderer.render(ast);

        assertThat(esm).contains("\"old\" in _r");
        assertThat(esm).contains("delete _r[\"old\"]");
        assertThat(esm).contains("_r[\"new\"]");
        LOG.fine(() -> "Generated ESM:\n" + esm);
    }

    @Test
    void renderDirectiveWithRemove() {
        LOG.info(() -> "TEST: renderDirectiveWithRemove");

        final var transform = Json.parse("""
            {"@jdt.remove": "B"}
            """);
        final var ast = Jdt.parseToAst(transform);
        final var esm = JdtEsmRenderer.render(ast);

        assertThat(esm).contains("delete _r[\"B\"]");
        LOG.fine(() -> "Generated ESM:\n" + esm);
    }

    @Test
    void renderDirectiveWithReplace() {
        LOG.info(() -> "TEST: renderDirectiveWithReplace");

        final var transform = Json.parse("""
            {"@jdt.replace": 42}
            """);
        final var ast = Jdt.parseToAst(transform);
        final var esm = JdtEsmRenderer.render(ast);

        assertThat(esm).contains("_r = 42");
        LOG.fine(() -> "Generated ESM:\n" + esm);
    }

    @Test
    void renderDirectiveWithMerge() {
        LOG.info(() -> "TEST: renderDirectiveWithMerge");

        final var transform = Json.parse("""
            {"@jdt.merge": {"C": 3}}
            """);
        final var ast = Jdt.parseToAst(transform);
        final var esm = JdtEsmRenderer.render(ast);

        assertThat(esm).contains("deepMerge(_r,");
        LOG.fine(() -> "Generated ESM:\n" + esm);
    }

    @Test
    void renderPrimitiveReplacement() {
        LOG.info(() -> "TEST: renderPrimitiveReplacement");

        final var transform = Json.parse("42");
        final var ast = Jdt.parseToAst(transform);
        final var esm = JdtEsmRenderer.render(ast);

        assertThat(esm).contains("return 42;");
        LOG.fine(() -> "Generated ESM:\n" + esm);
    }

    @Test
    void renderStructurallyValid() {
        LOG.info(() -> "TEST: renderStructurallyValid");

        final var transform = Json.parse("""
            {
                "Settings": {
                    "@jdt.rename": {"old": "new"},
                    "@jdt.remove": "temp",
                    "value": "updated"
                }
            }
            """);
        final var ast = Jdt.parseToAst(transform);
        final var esm = JdtEsmRenderer.render(ast);

        assertThat(esm).startsWith("// Generated JDT");
        assertThat(esm).contains("export function transform(source)");
        assertThat(esm).contains("function deepMerge");
        assertThat(esm).endsWith("}\n");
        LOG.fine(() -> "Generated ESM:\n" + esm);
    }
}
