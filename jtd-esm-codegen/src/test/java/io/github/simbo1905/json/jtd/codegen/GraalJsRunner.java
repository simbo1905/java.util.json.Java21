package io.github.simbo1905.json.jtd.codegen;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/// Executes generated ES2020 validators in-process using GraalVM Polyglot JS.
/// No external runtime required - the JS engine runs inside the JVM.
final class GraalJsRunner {
    private static final Logger LOG = Logger.getLogger(GraalJsRunner.class.getName());

    private GraalJsRunner() {}

    /// Evaluates a generated validator module and returns its exports.
    /// The module must export a `validate(instance)` function.
    static Value loadValidatorModule(Context context, Path modulePath) throws IOException {
        LOG.fine(() -> "Loading validator module: " + modulePath);
        final var source = Source.newBuilder("js", modulePath.toFile())
            .mimeType("application/javascript+module")
            .build();
        return context.eval(source);
    }

    /// Creates a GraalVM Polyglot context configured for ES2020 module evaluation.
    static Context createContext() {
        return Context.newBuilder("js")
            .allowIO(IOAccess.ALL)
            .option("js.esm-eval-returns-exports", "true")
            .option("js.ecmascript-version", "2020")
            .build();
    }

    /// Validates a JSON value against a generated validator by calling its
    /// `validate` export. Returns a list of error maps (instancePath, schemaPath).
    static List<Map<String, String>> validate(Value exports, Object jsonValue) {
        final var validateFn = exports.getMember("validate");
        assert validateFn != null && validateFn.canExecute() : "Module must export a validate function";
        final var result = validateFn.execute(jsonValue);
        return convertErrors(result);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> convertErrors(Value result) {
        final var size = (int) result.getArraySize();
        final var errors = new java.util.ArrayList<Map<String, String>>(size);
        for (int i = 0; i < size; i++) {
            final var errorVal = result.getArrayElement(i);
            final var instancePath = errorVal.getMember("instancePath").asString();
            final var schemaPath = errorVal.getMember("schemaPath").asString();
            errors.add(Map.of("instancePath", instancePath, "schemaPath", schemaPath));
        }
        return errors;
    }
}
