package io.github.yagipass.verbatime.examples.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public final class HealthHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    Responses.text(exchange, 200, "ok");
  }
}
