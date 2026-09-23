package io.github.yagipass.verbatime.examples.micronaut;

import jakarta.inject.Singleton;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;

@Singleton
public class Warmup {

    private final OrderService orders;

    Warmup(final OrderService orders) {
        this.orders = orders;
    }

    @EventListener
    void onStart(final StartupEvent event) {
        orders.placeOrder("warmup", 1);
    }
}
