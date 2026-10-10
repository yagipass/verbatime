package io.github.yagipass.verbatime.examples.mvc;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MvcApplication {

  @Bean
  OrderService orderService() {
    return new OrderService();
  }

  public static void main(String[] args) {
    SpringApplication.run(MvcApplication.class, args);
  }
}
