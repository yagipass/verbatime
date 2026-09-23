package io.github.yagipass.verbatime.examples.undertow;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;

public final class Main {

    private Main() {
    }

    public static void main(final String[] args) {
        final int port = 8080;
        final OrderService orders = new OrderService();
        warmUp(orders);
        final Undertow server = Undertow.builder().addHttpListener(port, "0.0.0.0").setHandler(Handlers.path().addExactPath("/healthz", exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");
            exchange.getResponseSender().send("ok\n");
        }).addExactPath("/orders", new BlockingHandler(new OrderHandler(orders)))).build();
        server.start();
        System.out.println("undertow listening on port " + port);
    }

    private static void warmUp(final OrderService orders) {
        orders.placeOrder("warmup", 1);
    }
}
