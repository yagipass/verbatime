package io.github.yagipass.verbatime.examples.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.Receipt;

@Service
public class PersistentOrderService {

    private final OrderService orders;

    private final OrderRepository repository;

    PersistentOrderService(final OrderService orders, final OrderRepository repository) {
        this.orders = orders;
        this.repository = repository;
    }

    @Transactional
    public OrderEntity place(final String sku, final int qty) {
        final Receipt receipt = orders.placeOrder(sku, qty);
        return repository.save(new OrderEntity(receipt.sku(), receipt.qty(), receipt.cents(), receipt.txId()));
    }

    @Transactional(readOnly = true)
    public Optional<OrderEntity> find(final long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> recent() {
        return repository.findTop10ByOrderByIdDesc();
    }
}
