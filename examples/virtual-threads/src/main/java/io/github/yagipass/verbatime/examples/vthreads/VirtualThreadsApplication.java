package io.github.yagipass.verbatime.examples.vthreads;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class VirtualThreadsApplication {

  public static void main(String[] args) {
    SpringApplication.run(VirtualThreadsApplication.class, args);
  }

  @Bean
  OrderService orderService() {
    return new OrderService();
  }
}
