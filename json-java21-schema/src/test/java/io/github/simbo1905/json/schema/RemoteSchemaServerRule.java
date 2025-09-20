package io.github.simbo1905.json.schema;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class RemoteSchemaServerRule implements BeforeAllCallback, AfterAllCallback {
    private HttpServer server;
    private String host;
    private int port;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        host = "127.0.0.1";
        port = server.getAddress().getPort();

        // Basic example document
        addJson("/a.json", "{\n  \"$id\": \"http://" + host + ":" + port + "/a.json\",\n  \"$defs\": {\n    \"X\": {\"type\": \"integer\", \"minimum\": 1},\n    \"Y\": {\"type\": \"string\", \"minLength\": 1}\n  }\n}\n");

        // Simple second doc
        addJson("/b.json", "{\n  \"$id\": \"http://" + host + ":" + port + "/b.json\",\n  \"type\": \"string\"\n}\n");

        // 2-node cycle
        addJson("/cycle1.json", "{\n  \"$id\": \"http://" + host + ":" + port + "/cycle1.json\",\n  \"$ref\": \"http://" + host + ":" + port + "/cycle2.json#\"\n}\n");
        addJson("/cycle2.json", "{\n  \"$id\": \"http://" + host + ":" + port + "/cycle2.json\",\n  \"$ref\": \"http://" + host + ":" + port + "/cycle1.json#\"\n}\n");

        server.start();
    }

    private void addJson(String path, String body) {
        server.createContext(path, new JsonResponder(body));
    }

    String url(String path) { return "http://" + host + ":" + port + path; }

    @Override
    public void afterAll(ExtensionContext context) {
        if (server != null) server.stop(0);
    }

    private static final class JsonResponder implements HttpHandler {
        private final byte[] bytes;
        JsonResponder(String body) { this.bytes = body.getBytes(StandardCharsets.UTF_8); }
        @Override public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/schema+json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }
}
