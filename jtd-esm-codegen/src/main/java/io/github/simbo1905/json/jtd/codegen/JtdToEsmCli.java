package io.github.simbo1905.json.jtd.codegen;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static io.github.simbo1905.json.jtd.codegen.JtdAst.SchemaNode;

/// CLI entry point for generating an ES2020 ESM validator from a flat JTD schema.
///
/// Usage:
/// `java -jar jtd-esm-codegen.jar schema.jtd.json`
public final class JtdToEsmCli {
    private JtdToEsmCli() {}

    public static void main(String[] args) {
        final var err = new PrintWriter(System.err, true, StandardCharsets.UTF_8);

        if (args == null || args.length != 1) {
            err.println("Usage: java -jar jtd-esm-codegen.jar <schema.jtd.json>");
            System.exit(2);
            return;
        }

        try {
            final var in = Path.of(args[0]);
            final var out = run(in, Path.of("."));
            System.out.println(out.toAbsolutePath());
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace(err);
            System.exit(1);
        }
    }

    static Path run(Path schemaFile, Path outputDir) throws IOException {
        Objects.requireNonNull(schemaFile, "schemaFile must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");

        final byte[] digest = Sha256.digest(schemaFile);
        final String shaHex = Sha256.hex(digest);
        final String shaPrefix8 = Sha256.hexPrefix8(digest);

        final String json = Files.readString(schemaFile, StandardCharsets.UTF_8);
        final SchemaNode schema = JtdParser.parseString(json);

        final String js = EsmRenderer.render(schema, shaHex, shaPrefix8);
        final String fileName = schema.id() + "-" + shaPrefix8 + ".js";

        Files.createDirectories(outputDir);
        final Path out = outputDir.resolve(fileName);
        Files.writeString(out, js, StandardCharsets.UTF_8);
        return out;
    }
}

