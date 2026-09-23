package io.github.yagipass.verbatime.examples.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@SpringBootApplication
public class MvcApplication {

    public static void main(final String[] args) {
        SpringApplication.run(MvcApplication.class, args);
    }

    @Bean
    OrderService orderService() {
        return new OrderService();
    }
}
