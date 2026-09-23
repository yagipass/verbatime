package io.github.yagipass.verbatime.examples.helidon;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.helidon.webserver.WebServer;

public final class Main {

    private Main() {
    }

    public static void main(final String[] args) {
        final int port = 8080;
        final OrdersService service = new OrdersService(new OrderService());
        final WebServer server = WebServer.builder().port(port).routing(routing -> routing.register("/", service)).build();
        server.start();
        System.out.println("helidon-se listening on port " + server.port());
    }
}
