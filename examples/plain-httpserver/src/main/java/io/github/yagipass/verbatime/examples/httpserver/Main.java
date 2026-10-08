package io.github.yagipass.verbatime.examples.httpserver;

import com.sun.net.httpserver.HttpServer;
import io.github.yagipass.verbatime.examples.workload.OrderService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class Main {

  private Main() {}

  public static void main(String[] args) throws IOException {
    int port = 8080;
    OrderService orders = new OrderService();
    warmUp(orders);
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/healthz", new HealthHandler());
    server.createContext("/orders", new OrderHandler(orders));
    server.setExecutor(Executors.newFixedThreadPool(8, r -> new Thread(r, "http-worker")));
    server.start();
    System.out.println("plain-httpserver listening on port " + port);
  }

  private static void warmUp(OrderService orders) {
    orders.placeOrder("warmup", 1);
  }
}
