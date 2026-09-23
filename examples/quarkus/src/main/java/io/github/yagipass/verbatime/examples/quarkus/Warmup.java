package io.github.yagipass.verbatime.examples.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class Warmup {

    @Inject
    OrderService orders;

    void onStart(@Observes final StartupEvent event) {
        orders.placeOrder("warmup", 1);
    }
}
