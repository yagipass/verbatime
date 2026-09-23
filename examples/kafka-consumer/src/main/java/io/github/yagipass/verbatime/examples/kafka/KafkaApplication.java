package io.github.yagipass.verbatime.examples.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@SpringBootApplication
public class KafkaApplication {

    static final String TOPIC = "orders";

    public static void main(final String[] args) {
        SpringApplication.run(KafkaApplication.class, args);
    }

    @Bean
    OrderService orderService() {
        return new OrderService();
    }

    @Bean
    NewTopic ordersTopic() {
        return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
    }
}
