package io.github.yagipass.verbatime.examples.undertow;

import java.util.Deque;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.github.yagipass.verbatime.examples.workload.Receipt;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

public final class OrderHandler implements HttpHandler {

    private final OrderService orders;

    public OrderHandler(final OrderService orders) {
        this.orders = orders;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final String sku = param(exchange, "sku", "widget");
        final int qty;
        try {
            qty = Integer.parseInt(param(exchange, "qty", "1"));
        } catch (final NumberFormatException e) {
            text(exchange, StatusCodes.BAD_REQUEST, "qty must be an integer");
            return;
        }
        try {
            final Receipt receipt = orders.placeOrder(sku, qty);
            text(exchange, StatusCodes.OK, receipt.toText());
        } catch (final OutOfStockException e) {
            text(exchange, StatusCodes.CONFLICT, e.getMessage());
        }
    }

    private static String param(final HttpServerExchange exchange, final String name, final String dflt) {
        final Deque<String> values = exchange.getQueryParameters().get(name);
        return values == null || values.isEmpty() ? dflt : values.getFirst();
    }

    private static void text(final HttpServerExchange exchange, final int status, final String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");
        exchange.getResponseSender().send(body + "\n");
    }
}
