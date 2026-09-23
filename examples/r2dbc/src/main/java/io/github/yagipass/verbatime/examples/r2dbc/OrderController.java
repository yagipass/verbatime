package io.github.yagipass.verbatime.examples.r2dbc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.Receipt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class OrderController {

    private final OrderService orders;

    private final OrderRepository repository;

    OrderController(final OrderService orders, final OrderRepository repository) {
        this.orders = orders;
        this.repository = repository;
    }

    @GetMapping("/orders")
    public Mono<OrderEntity> place(@RequestParam(defaultValue = "widget") final String sku, @RequestParam(defaultValue = "1") final int qty) {
        return Mono.fromSupplier(() -> orders.placeOrder(sku, qty)).flatMap(this::save);
    }

    @GetMapping("/orders/recent")
    public Flux<OrderEntity> recent() {
        return repository.findTop10ByOrderByIdDesc();
    }

    @GetMapping("/orders/{id}")
    public Mono<ResponseEntity<OrderEntity>> find(@PathVariable final long id) {
        return repository.findById(id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    Mono<OrderEntity> save(final Receipt receipt) {
        return repository.save(OrderEntity.of(receipt.sku(), receipt.qty(), receipt.cents(), receipt.txId()));
    }
}
