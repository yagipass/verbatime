package io.github.yagipass.verbatime.examples.jpa;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JpaApplication {

  public static void main(String[] args) {
    SpringApplication.run(JpaApplication.class, args);
  }

  @Bean
  OrderService orderService() {
    return new OrderService();
  }
}
