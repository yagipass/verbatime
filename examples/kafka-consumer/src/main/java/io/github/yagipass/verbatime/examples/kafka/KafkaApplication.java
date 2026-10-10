package io.github.yagipass.verbatime.examples.kafka;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@SpringBootApplication
public class KafkaApplication {

  static final String TOPIC = "orders";

  @Bean
  OrderService orderService() {
    return new OrderService();
  }

  @Bean
  NewTopic ordersTopic() {
    return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
  }

  public static void main(String[] args) {
    SpringApplication.run(KafkaApplication.class, args);
  }
}
