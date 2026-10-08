package io.github.yagipass.verbatime.examples.micronaut;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class OrderServiceFactory {

  @Singleton
  OrderService orderService() {
    return new OrderService();
  }
}
