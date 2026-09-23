package io.github.yagipass.verbatime.examples.r2dbc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@SpringBootApplication
public class R2dbcApplication {

    public static void main(final String[] args) {
        SpringApplication.run(R2dbcApplication.class, args);
    }

    @Bean
    OrderService orderService() {
        return new OrderService();
    }
}
