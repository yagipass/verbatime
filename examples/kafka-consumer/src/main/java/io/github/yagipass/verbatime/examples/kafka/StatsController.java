package io.github.yagipass.verbatime.examples.kafka;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final OrderConsumer consumer;

    StatsController(final OrderConsumer consumer) {
        this.consumer = consumer;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of("placed", consumer.placed(), "rejected", consumer.rejected());
    }
}
