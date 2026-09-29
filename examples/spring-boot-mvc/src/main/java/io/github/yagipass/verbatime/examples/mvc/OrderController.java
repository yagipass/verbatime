package io.github.yagipass.verbatime.examples.mvc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.Receipt;

@RestController
public class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public Receipt place(@RequestParam(defaultValue = "widget") String sku, @RequestParam(defaultValue = "1") int qty) {
        return orders.placeOrder(sku, qty);
    }
}
