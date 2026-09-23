package io.github.yagipass.verbatime.examples.httpserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import io.github.yagipass.verbatime.examples.workload.OrderService;

public final class Main {

    private Main() {
    }

    public static void main(final String[] args) throws IOException {
        final int port = 8080;
        final OrderService orders = new OrderService();
        warmUp(orders);
        final HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/healthz", new HealthHandler());
        server.createContext("/orders", new OrderHandler(orders));
        server.setExecutor(Executors.newFixedThreadPool(8, r -> new Thread(r, "http-worker")));
        server.start();
        System.out.println("plain-httpserver listening on port " + port);
    }

    private static void warmUp(final OrderService orders) {
        orders.placeOrder("warmup", 1);
    }
}
