package io.github.yagipass.verbatime.examples.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@ApplicationScoped
public class OrderServiceProducer {

    @Produces
    @Singleton
    OrderService orderService() {
        return new OrderService();
    }
}
