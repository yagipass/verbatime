package io.github.yagipass.verbatime.examples.batch;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupRunner implements ApplicationRunner {

  private final OrderService orders;

  StartupRunner(OrderService orders) {
    this.orders = orders;
  }

  @Override
  public void run(ApplicationArguments args) {
    orders.placeOrder("warmup", 1);
  }
}
