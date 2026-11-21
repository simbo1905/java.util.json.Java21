package json.java21.jsonpath;

import jdk.incubator.java.util.json.Json;
import jdk.incubator.java.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for JsonPathCodegen mechanics: compilation produces a compiled engine
/// facade wrapping a runtime-generated executor, emits Java 21 bytecode, is
/// idempotent, and exposes ahead-of-time classfile bytes for jtd2jar-style tooling.
class JsonPathCodegenTest extends JsonPathLoggingConfig {

    private static final Logger LOG = Logger.getLogger(JsonPathCodegenTest.class.getName());

    private static final String DOC_JSON = """
        {"store": {"bicycle": {"color": "red"}}}
        """;

    @Test
    void compileReturnsCompiledEngineFacade() {
        LOG.info(() -> "TEST: compileReturnsCompiledEngineFacade");
        final var path = JsonPathCodegen.compile(JsonPath.parse("$.store.bicycle.color"));
        assertThat(path).isInstanceOf(JsonPathCompiled.class);
        assertThat(path.isCompiled()).isTrue();
        assertThat(path.isInterpreted()).isFalse();
    }

    @Test
    void compiledEngineQueriesDocuments() {
        LOG.info(() -> "TEST: compiledEngineQueriesDocuments");
        final var document = Json.parse(DOC_JSON);
        final var path = JsonPathCodegen.compile(JsonPath.parse("$.store.bicycle.color"));
        final var results = path.query(document);
        assertThat(results).hasSize(1);
        assertThat(((jdk.incubator.java.util.json.JsonString) results.getFirst()).asString()).isEqualTo("red");
    }

    @Test
    void compileIsIdempotent() {
        LOG.info(() -> "TEST: compileIsIdempotent - compiling a compiled path returns the same instance");
        final var path = JsonPathCodegen.compile(JsonPath.parse("$.store"));
        assertThat(JsonPathCodegen.compile(path)).isSameAs(path);
    }

    @Test
    void compiledEnginePreservesPathTextAndAst() {
        LOG.info(() -> "TEST: compiledEnginePreservesPathTextAndAst");
        final var interpreted = JsonPath.parse("$.store.book");
        final var compiled = JsonPathCodegen.compile(interpreted);
        assertThat(compiled.toString()).isEqualTo(interpreted.toString());
        assertThat(compiled.ast()).isNotNull();
        assertThat(compiled.ast().segments()).hasSize(2);
    }

    @Test
    void compileBytesEmitsJava21Classfile() {
        LOG.info(() -> "TEST: compileBytesEmitsJava21Classfile - classfile major version must be 65 (Java 21)");
        final var ast = JsonPathParser.parse("$.store.bicycle.color");
        final var bytes = JsonPathCodegen.compileBytes("GeneratedForVersionProbe", ast);
        assertThat(bytes).isNotEmpty();
        final int minor = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
        final int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        LOG.fine(() -> "Generated classfile version: " + major + "." + minor);
        assertThat(major).isEqualTo(65); // ClassFile.JAVA_21_VERSION
        assertThat(minor).isZero();
    }

    @Test
    void compileBytesEmitsFreshClassesForDistinctAsts() {
        LOG.info(() -> "TEST: compileBytesEmitsFreshClassesForDistinctAsts");
        final var bytesA = JsonPathCodegen.compileBytes("ProbeA", JsonPathParser.parse("$.a"));
        final var bytesB = JsonPathCodegen.compileBytes("ProbeA", JsonPathParser.parse("$.a.b"));
        assertThat(bytesA).isNotEmpty();
        assertThat(bytesB).isNotEmpty();
        assertThat(bytesA).isNotEqualTo(bytesB);
    }
}
