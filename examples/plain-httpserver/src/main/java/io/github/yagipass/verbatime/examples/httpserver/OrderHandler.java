package io.github.yagipass.verbatime.examples.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.github.yagipass.verbatime.examples.workload.Receipt;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public final class OrderHandler implements HttpHandler {

  private final OrderService orders;

  public OrderHandler(OrderService orders) {
    this.orders = orders;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    Map<String, String> query = parseQuery(exchange.getRequestURI());
    String sku = query.getOrDefault("sku", "widget");
    int qty;
    try {
      qty = Integer.parseInt(query.getOrDefault("qty", "1"));
    } catch (NumberFormatException e) {
      Responses.text(exchange, 400, "qty must be an integer");
      return;
    }
    try {
      Receipt receipt = orders.placeOrder(sku, qty);
      Responses.text(exchange, 200, receipt.toText());
    } catch (OutOfStockException e) {
      Responses.text(exchange, 409, e.getMessage());
    }
  }

  private static Map<String, String> parseQuery(URI uri) {
    Map<String, String> out = new HashMap<>();
    String q = uri.getRawQuery();
    if (q == null || q.isEmpty()) {
      return out;
    }
    for (String pair : q.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        out.put(pair.substring(0, eq), pair.substring(eq + 1));
      }
    }
    return out;
  }
}
