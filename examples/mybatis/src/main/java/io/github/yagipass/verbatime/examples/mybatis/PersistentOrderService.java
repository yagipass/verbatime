package io.github.yagipass.verbatime.examples.mybatis;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.Receipt;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentOrderService {

  private final OrderService orders;

  private final OrderMapper mapper;

  PersistentOrderService(OrderService orders, OrderMapper mapper) {
    this.orders = orders;
    this.mapper = mapper;
  }

  @Transactional
  public OrderRow place(String sku, int qty) {
    Receipt receipt = orders.placeOrder(sku, qty);
    OrderRow row = new OrderRow(receipt.sku(), receipt.qty(), receipt.cents(), receipt.txId());
    mapper.insert(row);
    return mapper.findById(row.getId()).orElseThrow();
  }

  @Transactional(readOnly = true)
  public Optional<OrderRow> find(long id) {
    return mapper.findById(id);
  }

  @Transactional(readOnly = true)
  public List<OrderRow> recent() {
    return mapper.findRecent();
  }
}
