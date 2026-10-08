package io.github.yagipass.verbatime.examples.quarkus;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class Warmup {

  @Inject OrderService orders;

  void onStart(@Observes StartupEvent event) {
    orders.placeOrder("warmup", 1);
  }
}
