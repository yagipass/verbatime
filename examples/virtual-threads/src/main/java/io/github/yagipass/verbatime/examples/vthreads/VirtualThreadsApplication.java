package io.github.yagipass.verbatime.examples.vthreads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@SpringBootApplication
public class VirtualThreadsApplication {

    public static void main(final String[] args) {
        SpringApplication.run(VirtualThreadsApplication.class, args);
    }

    @Bean
    OrderService orderService() {
        return new OrderService();
    }
}
