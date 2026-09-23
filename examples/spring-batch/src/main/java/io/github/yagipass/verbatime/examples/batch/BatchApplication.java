package io.github.yagipass.verbatime.examples.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@SpringBootApplication
public class BatchApplication {

    public static void main(final String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BatchApplication.class, args)));
    }

    @Bean
    OrderService orderService() {
        return new OrderService();
    }
}
