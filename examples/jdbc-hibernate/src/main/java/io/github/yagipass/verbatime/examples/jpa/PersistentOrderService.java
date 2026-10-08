package io.github.yagipass.verbatime.examples.jpa;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.Receipt;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentOrderService {

  private final OrderService orders;

  private final OrderRepository repository;

  PersistentOrderService(OrderService orders, OrderRepository repository) {
    this.orders = orders;
    this.repository = repository;
  }

  @Transactional
  public OrderEntity place(String sku, int qty) {
    Receipt receipt = orders.placeOrder(sku, qty);
    return repository.save(
        new OrderEntity(receipt.sku(), receipt.qty(), receipt.cents(), receipt.txId()));
  }

  @Transactional(readOnly = true)
  public Optional<OrderEntity> find(long id) {
    return repository.findById(id);
  }

  @Transactional(readOnly = true)
  public List<OrderEntity> recent() {
    return repository.findTop10ByOrderByIdDesc();
  }
}
