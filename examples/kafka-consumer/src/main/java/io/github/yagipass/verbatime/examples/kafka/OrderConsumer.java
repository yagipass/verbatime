package io.github.yagipass.verbatime.examples.kafka;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;

@Component
public class OrderConsumer {

    private final OrderService orders;

    private final AtomicLong placed = new AtomicLong();

    private final AtomicLong rejected = new AtomicLong();

    OrderConsumer(final OrderService orders) {
        this.orders = orders;
    }

    @KafkaListener(topics = KafkaApplication.TOPIC)
    public void onOrder(final String message) {
        final int colon = message.indexOf(':');
        final String sku = colon < 0 ? message : message.substring(0, colon);
        final int qty = colon < 0 ? 1 : Integer.parseInt(message.substring(colon + 1));
        try {
            orders.placeOrder(sku, qty);
            placed.incrementAndGet();
        } catch (final OutOfStockException e) {
            rejected.incrementAndGet();
        }
    }

    public long placed() {
        return placed.get();
    }

    public long rejected() {
        return rejected.get();
    }
}
