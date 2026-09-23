package io.github.yagipass.verbatime.examples.micronaut;

import jakarta.inject.Singleton;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.micronaut.context.annotation.Factory;

@Factory
public class OrderServiceFactory {

    @Singleton
    OrderService orderService() {
        return new OrderService();
    }
}
