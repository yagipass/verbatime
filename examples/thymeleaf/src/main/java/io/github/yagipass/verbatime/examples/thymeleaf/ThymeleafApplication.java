package io.github.yagipass.verbatime.examples.thymeleaf;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ThymeleafApplication {

  public static void main(String[] args) {
    SpringApplication.run(ThymeleafApplication.class, args);
  }

  @Bean
  OrderService orderService() {
    return new OrderService();
  }
}
