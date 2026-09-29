package io.github.yagipass.verbatime.examples.grpc;

import java.io.IOException;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 8080;
        OrderService orders = new OrderService();
        warmUp(orders);
        HealthStatusManager health = new HealthStatusManager();
        Server server = ServerBuilder.forPort(port).addService(new OrdersService(orders)).addService(ProtoReflectionServiceV1.newInstance()).addService(health.getHealthService()).build();
        server.start();
        System.out.println("grpc listening on port " + port);
        server.awaitTermination();
    }

    private static void warmUp(OrderService orders) {
        orders.placeOrder("warmup", 1);
    }
}
