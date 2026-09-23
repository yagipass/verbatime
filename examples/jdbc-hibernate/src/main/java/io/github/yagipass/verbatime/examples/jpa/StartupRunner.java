package io.github.yagipass.verbatime.examples.jpa;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupRunner implements ApplicationRunner {

    private final PersistentOrderService orders;

    StartupRunner(final PersistentOrderService orders) {
        this.orders = orders;
    }

    @Override
    public void run(final ApplicationArguments args) {
        orders.place("warmup", 1);
    }
}
