package io.github.yagipass.verbatime.examples.thymeleaf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@SpringBootApplication
public class ThymeleafApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ThymeleafApplication.class, args);
    }

    @Bean
    OrderService orderService() {
        return new OrderService();
    }
}
