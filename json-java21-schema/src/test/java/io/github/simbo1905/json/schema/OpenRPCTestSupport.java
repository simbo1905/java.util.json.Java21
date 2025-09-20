package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.Json;
import jdk.sandbox.java.util.json.JsonValue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

final class OpenRPCTestSupport {
    private OpenRPCTestSupport() {}

    static JsonSchema loadOpenRpcSchema() {
        return JsonSchema.compile(readJson("openrpc/schema.json"));
    }

    static JsonValue readJson(String resourcePath) {
        return Json.parse(readText(resourcePath));
    }

    static String readText(String resourcePath) {
        try {
            URL url = Objects.requireNonNull(OpenRPCTestSupport.class.getClassLoader().getResource(resourcePath), resourcePath);
            return Files.readString(Path.of(url.toURI()), StandardCharsets.UTF_8);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }

    static List<String> exampleNames() {
        try {
            URL dirUrl = Objects.requireNonNull(OpenRPCTestSupport.class.getClassLoader().getResource("openrpc/examples"), "missing openrpc/examples directory");
            try (Stream<Path> s = Files.list(Path.of(dirUrl.toURI()))) {
                return s.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .map(p -> p.getFileName().toString())
                        .sorted(Comparator.naturalOrder())
                        .toList();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list openrpc examples", e);
        }
    }

    static JsonSchema.ValidationResult validateExample(String name) {
        JsonSchema schema = loadOpenRpcSchema();
        JsonValue doc = readJson("openrpc/examples/" + name);
        return schema.validate(doc);
    }
}

