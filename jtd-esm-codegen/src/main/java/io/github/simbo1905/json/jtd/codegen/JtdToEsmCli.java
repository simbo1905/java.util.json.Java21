package io.github.simbo1905.json.jtd.codegen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/// CLI entry point for the new JTD to ESM code generator.
/// Generates optimal vanilla ES2020 validators with explicit stack-based validation.
public final class JtdToEsmCli {
    private static final Logger LOG = Logger.getLogger(JtdToEsmCli.class.getName());

    private JtdToEsmCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar jtd-esm-codegen.jar <schema.json> [output-dir]");
            System.exit(1);
        }

        final Path schemaPath = Path.of(args[0]).toAbsolutePath().normalize();
        final Path outDir = args.length > 1 
            ? Path.of(args[1]).toAbsolutePath().normalize()
            : Path.of(".").toAbsolutePath().normalize();

        final Path outJs = run(schemaPath, outDir);
        System.out.println("Generated: " + outJs);
    }

    public static Path run(Path schemaPath, Path outDir) throws IOException {
        LOG.fine(() -> "Reading schema from: " + schemaPath);
        
        final String schemaJson = Files.readString(schemaPath, StandardCharsets.UTF_8);
        final var schema = JtdParser.parseString(schemaJson);
        
        final byte[] digest = Sha256.digest(schemaPath);
        final String shaHex = Sha256.hex(digest);
        final String shaPrefix8 = Sha256.hexPrefix8(digest);
        
        LOG.fine(() -> "Schema SHA-256: " + shaHex);
        
        final String js = EsmRenderer.render(schema, shaHex, shaPrefix8);
        
        final String fileName = schema.id() + "-" + shaPrefix8 + ".js";
        final Path outJs = outDir.resolve(fileName);
        
        Files.writeString(outJs, js, StandardCharsets.UTF_8);
        
        LOG.fine(() -> "Generated validator: " + outJs);
        return outJs;
    }
}
