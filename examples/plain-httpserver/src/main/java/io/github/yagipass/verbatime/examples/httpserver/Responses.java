package io.github.yagipass.verbatime.examples.httpserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;

final class Responses {

    private Responses() {
    }

    static void text(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = (body + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
