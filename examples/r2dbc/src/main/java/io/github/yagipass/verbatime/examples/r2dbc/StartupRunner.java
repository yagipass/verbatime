package io.github.yagipass.verbatime.examples.r2dbc;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.Receipt;

@Component
public class StartupRunner implements ApplicationRunner {

    private final OrderService orders;

    private final OrderRepository repository;

    StartupRunner(final OrderService orders, final OrderRepository repository) {
        this.orders = orders;
        this.repository = repository;
    }

    @Override
    public void run(final ApplicationArguments args) {
        final Receipt receipt = orders.placeOrder("warmup", 1);
        repository.save(OrderEntity.of(receipt.sku(), receipt.qty(), receipt.cents(), receipt.txId())).block();
    }
}
