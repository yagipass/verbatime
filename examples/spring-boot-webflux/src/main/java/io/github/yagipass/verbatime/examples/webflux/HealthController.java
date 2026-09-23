package io.github.yagipass.verbatime.examples.webflux;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Mono<String> healthz() {
        return Mono.just("ok");
    }
}
