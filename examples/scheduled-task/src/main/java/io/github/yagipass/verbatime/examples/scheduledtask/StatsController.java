package io.github.yagipass.verbatime.examples.scheduledtask;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final ScheduledOrders scheduled;

    StatsController(final ScheduledOrders scheduled) {
        this.scheduled = scheduled;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of("ticks", scheduled.ticks());
    }
}
