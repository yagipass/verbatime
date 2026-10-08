package io.github.yagipass.verbatime.examples.kafka;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {

  private final OrderService orders;

  private final AtomicLong placed = new AtomicLong();

  private final AtomicLong rejected = new AtomicLong();

  OrderConsumer(OrderService orders) {
    this.orders = orders;
  }

  @KafkaListener(topics = KafkaApplication.TOPIC)
  public void onOrder(String message) {
    int colon = message.indexOf(':');
    String sku = colon < 0 ? message : message.substring(0, colon);
    int qty = colon < 0 ? 1 : Integer.parseInt(message.substring(colon + 1));
    try {
      orders.placeOrder(sku, qty);
      placed.incrementAndGet();
    } catch (OutOfStockException e) {
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
