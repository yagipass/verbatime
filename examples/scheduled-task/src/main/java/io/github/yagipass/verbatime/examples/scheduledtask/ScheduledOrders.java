package io.github.yagipass.verbatime.examples.scheduledtask;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@Component
public class ScheduledOrders {

    private static final String[] SKUS = { "widget", "gadget", "gizmo" };

    private final OrderService orders;

    private final AtomicLong ticks = new AtomicLong();

    ScheduledOrders(final OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void placeScheduledOrder() {
        final long n = ticks.incrementAndGet();
        orders.placeOrder(SKUS[(int) (n % SKUS.length)], (int) (n % 5) + 1);
    }

    public long ticks() {
        return ticks.get();
    }
}
