package io.github.yagipass.verbatime.examples.mybatis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@SpringBootApplication
public class MybatisApplication {

    public static void main(final String[] args) {
        SpringApplication.run(MybatisApplication.class, args);
    }

    @Bean
    OrderService orderService() {
        return new OrderService();
    }
}
