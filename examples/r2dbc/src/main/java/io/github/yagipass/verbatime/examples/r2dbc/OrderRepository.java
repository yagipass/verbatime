package io.github.yagipass.verbatime.examples.r2dbc;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, Long> {

    Flux<OrderEntity> findTop10ByOrderByIdDesc();
}
