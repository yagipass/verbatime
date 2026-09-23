package io.github.yagipass.verbatime.examples.httpserver;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public final class HealthHandler implements HttpHandler {

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        Responses.text(exchange, 200, "ok");
    }
}
