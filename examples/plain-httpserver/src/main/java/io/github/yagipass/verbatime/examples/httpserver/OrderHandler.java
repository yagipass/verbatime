package io.github.yagipass.verbatime.examples.httpserver;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.github.yagipass.verbatime.examples.workload.Receipt;

public final class OrderHandler implements HttpHandler {

    private final OrderService orders;

    public OrderHandler(final OrderService orders) {
        this.orders = orders;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        final Map<String, String> query = parseQuery(exchange.getRequestURI());
        final String sku = query.getOrDefault("sku", "widget");
        final int qty;
        try {
            qty = Integer.parseInt(query.getOrDefault("qty", "1"));
        } catch (final NumberFormatException e) {
            Responses.text(exchange, 400, "qty must be an integer");
            return;
        }
        try {
            final Receipt receipt = orders.placeOrder(sku, qty);
            Responses.text(exchange, 200, receipt.toText());
        } catch (final OutOfStockException e) {
            Responses.text(exchange, 409, e.getMessage());
        }
    }

    private static Map<String, String> parseQuery(final URI uri) {
        final Map<String, String> out = new HashMap<>();
        final String q = uri.getRawQuery();
        if (q == null || q.isEmpty()) {
            return out;
        }
        for (final String pair : q.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return out;
    }
}
