package io.github.yagipass.verbatime.examples.scheduledtask;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@Component
public class StartupRunner implements ApplicationRunner {

    private final OrderService orders;

    StartupRunner(final OrderService orders) {
        this.orders = orders;
    }

    @Override
    public void run(final ApplicationArguments args) {
        orders.placeOrder("warmup", 1);
    }
}
