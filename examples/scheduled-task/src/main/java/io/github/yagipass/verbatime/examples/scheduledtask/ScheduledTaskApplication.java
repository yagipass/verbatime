package io.github.yagipass.verbatime.examples.scheduledtask;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScheduledTaskApplication {

  public static void main(String[] args) {
    SpringApplication.run(ScheduledTaskApplication.class, args);
  }

  @Bean
  OrderService orderService() {
    return new OrderService();
  }
}
