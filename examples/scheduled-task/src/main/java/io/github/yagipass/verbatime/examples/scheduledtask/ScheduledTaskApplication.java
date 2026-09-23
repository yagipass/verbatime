package io.github.yagipass.verbatime.examples.scheduledtask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@SpringBootApplication
@EnableScheduling
public class ScheduledTaskApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ScheduledTaskApplication.class, args);
    }

    @Bean
    OrderService orderService() {
        return new OrderService();
    }
}
