package io.github.yagipass.verbatime.examples.kafka;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderPublisher {

    private final KafkaTemplate<String, String> kafka;

    OrderPublisher(final KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @PostMapping("/orders")
    public ResponseEntity<String> publish(@RequestParam(defaultValue = "widget") final String sku, @RequestParam(defaultValue = "1") final int qty) {
        kafka.send(KafkaApplication.TOPIC, sku, sku + ":" + qty);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("queued " + sku + ":" + qty);
    }
}
