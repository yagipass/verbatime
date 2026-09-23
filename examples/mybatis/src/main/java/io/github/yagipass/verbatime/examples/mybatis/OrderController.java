package io.github.yagipass.verbatime.examples.mybatis;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final PersistentOrderService orders;

    OrderController(final PersistentOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public OrderRow place(@RequestParam(defaultValue = "widget") final String sku, @RequestParam(defaultValue = "1") final int qty) {
        return orders.place(sku, qty);
    }

    @GetMapping("/orders/recent")
    public List<OrderRow> recent() {
        return orders.recent();
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderRow> find(@PathVariable final long id) {
        return ResponseEntity.of(orders.find(id));
    }
}
