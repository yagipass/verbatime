package io.github.yagipass.verbatime.examples.webflux;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.Receipt;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class OrderController {

    private final OrderService orders;

    OrderController(final OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public Mono<Receipt> place(@RequestParam(defaultValue = "widget") final String sku, @RequestParam(defaultValue = "1") final int qty) {
        return Mono.fromSupplier(() -> orders.placeOrder(sku, qty));
    }

    @GetMapping("/orders/async")
    public Mono<Receipt> placeAsync(@RequestParam(defaultValue = "widget") final String sku, @RequestParam(defaultValue = "1") final int qty) {
        return Mono.fromCallable(() -> orders.placeOrder(sku, qty)).subscribeOn(Schedulers.boundedElastic());
    }
}
