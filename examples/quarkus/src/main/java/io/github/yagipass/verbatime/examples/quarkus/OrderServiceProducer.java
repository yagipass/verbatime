package io.github.yagipass.verbatime.examples.quarkus;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class OrderServiceProducer {

  @Produces
  @Singleton
  OrderService orderService() {
    return new OrderService();
  }
}
